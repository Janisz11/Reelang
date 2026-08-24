import logging

import pytest
from sqlalchemy.exc import OperationalError

from reelang_ai.scheduler import jobs


class FakeResult:
    def __init__(self, rowcount: int):
        self.rowcount = rowcount


class FakeSession:
    """Stands in for a Session; each hook can be told to blow up."""

    def __init__(self, rowcount=0, execute_error=None, commit_error=None, rollback_error=None,
                 close_error=None):
        self.rowcount = rowcount
        self.execute_error = execute_error
        self.commit_error = commit_error
        self.rollback_error = rollback_error
        self.close_error = close_error
        self.executed = []
        self.committed = False
        self.rolled_back = False
        self.closed = False

    def execute(self, statement, params=None):
        self.executed.append((statement, params))
        if self.execute_error:
            raise self.execute_error
        return FakeResult(self.rowcount)

    def commit(self):
        if self.commit_error:
            raise self.commit_error
        self.committed = True

    def rollback(self):
        self.rolled_back = True
        if self.rollback_error:
            raise self.rollback_error

    def close(self):
        self.closed = True
        if self.close_error:
            raise self.close_error


def unavailable(message: str = "connection refused") -> OperationalError:
    return OperationalError("DELETE FROM app_logs", {}, Exception(message))


@pytest.fixture
def session(monkeypatch):
    def install(fake: FakeSession) -> FakeSession:
        monkeypatch.setattr(jobs, "SessionLocal", lambda: fake)
        return fake

    return install


class TestStatement:
    async def test_deletes_from_app_logs_by_created_at(self, session):
        fake = session(FakeSession())

        await jobs.cleanup_old_app_logs()

        statement = str(fake.executed[0][0])
        assert "DELETE FROM app_logs" in statement
        assert "created_at <" in statement

    async def test_retention_window_is_passed_as_a_bound_parameter(self, session, monkeypatch):
        monkeypatch.setattr(jobs, "RETENTION_DAYS", 7)
        fake = session(FakeSession())

        await jobs.cleanup_old_app_logs()

        assert fake.executed[0][1] == {"days": 7}

    async def test_retention_days_is_not_interpolated_into_the_sql(self, session):
        fake = session(FakeSession())

        await jobs.cleanup_old_app_logs()

        assert ":days" in str(fake.executed[0][0])

    async def test_successful_run_commits(self, session):
        fake = session(FakeSession(rowcount=3))

        await jobs.cleanup_old_app_logs()

        assert fake.committed is True
        assert fake.rolled_back is False

    async def test_session_is_always_closed(self, session):
        fake = session(FakeSession())

        await jobs.cleanup_old_app_logs()

        assert fake.closed is True


class TestLogging:
    async def test_reports_the_deleted_row_count_at_info_level(self, session, caplog):
        session(FakeSession(rowcount=42))

        with caplog.at_level(logging.INFO, logger=jobs.__name__):
            await jobs.cleanup_old_app_logs()

        record = next(r for r in caplog.records if "app_logs cleanup" in r.message)
        assert record.levelno == logging.INFO
        assert "42" in record.message

    async def test_success_never_logs_at_warning_or_above(self, session, caplog):
        session(FakeSession(rowcount=5))

        with caplog.at_level(logging.DEBUG, logger=jobs.__name__):
            await jobs.cleanup_old_app_logs()

        assert [r for r in caplog.records if r.levelno >= logging.WARNING] == []

    async def test_zero_deleted_rows_is_still_info(self, session, caplog):
        session(FakeSession(rowcount=0))

        with caplog.at_level(logging.DEBUG, logger=jobs.__name__):
            await jobs.cleanup_old_app_logs()

        assert [r for r in caplog.records if r.levelno >= logging.WARNING] == []
        assert any("removed 0 rows" in r.message for r in caplog.records)

    async def test_failure_is_logged_as_an_error(self, session, caplog):
        session(FakeSession(execute_error=unavailable()))

        with caplog.at_level(logging.ERROR, logger=jobs.__name__):
            await jobs.cleanup_old_app_logs()

        record = next(r for r in caplog.records if "app_logs cleanup failed" in r.message)
        assert record.levelno == logging.ERROR


class TestDatabaseUnavailable:
    async def test_execute_failure_does_not_raise(self, session):
        session(FakeSession(execute_error=unavailable()))

        await jobs.cleanup_old_app_logs()

    async def test_commit_failure_does_not_raise(self, session):
        session(FakeSession(commit_error=unavailable()))

        await jobs.cleanup_old_app_logs()

    async def test_failed_run_rolls_back_and_closes(self, session):
        fake = session(FakeSession(execute_error=unavailable()))

        await jobs.cleanup_old_app_logs()

        assert fake.rolled_back is True
        assert fake.closed is True

    async def test_failing_rollback_does_not_raise(self, session):
        fake = session(
            FakeSession(execute_error=unavailable(), rollback_error=unavailable("broken"))
        )

        await jobs.cleanup_old_app_logs()

        assert fake.closed is True

    async def test_failing_close_does_not_raise(self, session):
        session(FakeSession(close_error=unavailable("broken")))

        await jobs.cleanup_old_app_logs()

    async def test_unavailable_session_factory_does_not_raise(self, monkeypatch, caplog):
        def explode():
            raise unavailable("engine is down")

        monkeypatch.setattr(jobs, "SessionLocal", explode)

        with caplog.at_level(logging.ERROR, logger=jobs.__name__):
            await jobs.cleanup_old_app_logs()

        assert any("app_logs cleanup failed" in r.message for r in caplog.records)


class TestSchedulerRegistration:
    def test_cleanup_is_registered_once_a_day_at_four(self):
        scheduler = jobs.create_scheduler()

        job = scheduler.get_job("app_logs_cleanup")

        assert job is not None
        assert str(job.trigger.fields[job.trigger.FIELD_NAMES.index("hour")]) == "4"
        assert str(job.trigger.fields[job.trigger.FIELD_NAMES.index("minute")]) == "0"

    def test_cleanup_uses_the_same_timezone_as_the_scheduler(self):
        scheduler = jobs.create_scheduler()

        job = scheduler.get_job("app_logs_cleanup")

        assert job.trigger.timezone == scheduler.timezone

    def test_cleanup_does_not_pile_up_if_a_run_is_missed(self):
        scheduler = jobs.create_scheduler()

        job = scheduler.get_job("app_logs_cleanup")

        assert job.max_instances == 1
        assert job.coalesce is True

    def test_existing_curation_job_is_untouched(self):
        scheduler = jobs.create_scheduler()

        job = scheduler.get_job("feed_curation")

        assert job is not None
        assert job.func is jobs.run_curation_job

    def test_scheduler_has_exactly_the_two_jobs(self):
        scheduler = jobs.create_scheduler()

        assert {job.id for job in scheduler.get_jobs()} == {"feed_curation", "app_logs_cleanup"}
