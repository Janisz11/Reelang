import logging

import pytest
from sqlalchemy import text

from app.models import AppLog
from app.services import db_log_handler
from app.services.db_log_handler import DatabaseLogHandler, install_database_log_handler


class NoCloseSession:
    """Delegates to the test session but ignores close(), which the fixture owns."""

    def __init__(self, session):
        self._session = session

    def add(self, obj):
        self._session.add(obj)

    def commit(self):
        self._session.commit()

    def execute(self, *args, **kwargs):
        return self._session.execute(*args, **kwargs)

    def close(self):
        pass


@pytest.fixture
def handler(monkeypatch, db):
    monkeypatch.setattr(db_log_handler, "SessionLocal", lambda: NoCloseSession(db))
    return DatabaseLogHandler()


@pytest.fixture
def logger(handler):
    log = logging.getLogger("reelang.test.handler")
    log.setLevel(logging.DEBUG)
    log.propagate = False
    log.addHandler(handler)
    try:
        yield log
    finally:
        log.removeHandler(handler)


def stored(db, logger_name="reelang.test.handler"):
    return (
        db.query(AppLog)
        .filter(AppLog.logger_name == logger_name)
        .order_by(AppLog.id)
        .all()
    )


class TestPersistence:
    def test_error_lands_in_app_logs(self, logger, db):
        logger.error("something broke")

        entries = stored(db)
        assert len(entries) == 1
        assert entries[0].level == "ERROR"
        assert entries[0].message == "something broke"

    def test_logger_name_is_recorded(self, logger, db):
        logger.error("boom")

        assert stored(db)[0].logger_name == "reelang.test.handler"

    def test_created_at_is_populated_by_the_database(self, logger, db):
        logger.error("boom")

        assert stored(db)[0].created_at is not None

    def test_printf_style_arguments_are_rendered(self, logger, db):
        logger.error("failed after %d attempts on %s", 3, "reel-1")

        assert stored(db)[0].message == "failed after 3 attempts on reel-1"

    def test_warning_and_critical_are_both_captured(self, logger, db):
        logger.warning("careful")
        logger.critical("meltdown")

        assert [e.level for e in stored(db)] == ["WARNING", "CRITICAL"]

    def test_info_and_debug_are_ignored(self, logger, db):
        logger.info("just so you know")
        logger.debug("noisy detail")

        assert stored(db) == []

    def test_handler_level_is_warning(self, handler):
        assert handler.level == logging.WARNING


class TestContext:
    def test_plain_message_stores_no_context(self, logger, db):
        logger.error("nothing extra")

        assert stored(db)[0].context is None

    def test_extra_fields_are_stored(self, logger, db):
        logger.error("import failed", extra={"reel_id": "reel-9", "attempt": 2})

        context = stored(db)[0].context
        assert context["reel_id"] == "reel-9"
        assert context["attempt"] == 2

    def test_exc_info_is_stored_as_a_traceback_string(self, logger, db):
        try:
            raise ValueError("inner explosion")
        except ValueError:
            logger.error("wrapped failure", exc_info=True)

        traceback_text = stored(db)[0].context["traceback"]
        assert "ValueError: inner explosion" in traceback_text

    def test_logger_exception_helper_is_captured(self, logger, db):
        try:
            raise KeyError("missing-key")
        except KeyError:
            logger.exception("lookup failed")

        entry = stored(db)[0]
        assert entry.level == "ERROR"
        assert "KeyError" in entry.context["traceback"]

    def test_unserializable_extras_fall_back_to_strings(self, logger, db):
        logger.error("odd payload", extra={"blob": object()})

        assert isinstance(stored(db)[0].context["blob"], str)

    def test_context_survives_a_round_trip_as_jsonb(self, logger, db):
        logger.error("nested", extra={"detail": {"a": [1, 2, {"b": True}]}})

        assert stored(db)[0].context["detail"] == {"a": [1, 2, {"b": True}]}


class TestNeverRaises:
    def test_database_failure_is_swallowed(self, monkeypatch, db):
        def explode():
            raise RuntimeError("database is gone")

        monkeypatch.setattr(db_log_handler, "SessionLocal", explode)
        log = logging.getLogger("reelang.test.broken")
        log.propagate = False
        log.addHandler(DatabaseLogHandler())

        try:
            log.error("this must not raise")
        finally:
            log.handlers.clear()

    def test_commit_failure_is_swallowed(self, monkeypatch, db):
        class FailingSession:
            def add(self, obj):
                pass

            def commit(self):
                raise RuntimeError("commit rejected")

            def close(self):
                pass

        monkeypatch.setattr(db_log_handler, "SessionLocal", lambda: FailingSession())
        log = logging.getLogger("reelang.test.failing-commit")
        log.propagate = False
        log.addHandler(DatabaseLogHandler())

        try:
            log.error("still must not raise")
        finally:
            log.handlers.clear()

    def test_a_failing_write_does_not_recurse(self, monkeypatch, db):
        calls = []

        def counting_session():
            calls.append(1)
            raise RuntimeError("database is gone")

        monkeypatch.setattr(db_log_handler, "SessionLocal", counting_session)
        log = logging.getLogger("reelang.test.recursion")
        log.propagate = False
        log.addHandler(DatabaseLogHandler())

        try:
            log.error("one failure, one attempt")
        finally:
            log.handlers.clear()

        assert len(calls) == 1


@pytest.fixture
def pristine_root():
    root = logging.getLogger()
    original = list(root.handlers)
    root.handlers = [h for h in original if not isinstance(h, DatabaseLogHandler)]
    try:
        yield root
    finally:
        root.handlers = original


class TestInstall:
    def test_install_attaches_a_handler_to_the_root_logger(self, pristine_root):
        installed = install_database_log_handler()

        assert installed in pristine_root.handlers

    def test_installing_twice_does_not_duplicate(self, pristine_root):
        first = install_database_log_handler()
        second = install_database_log_handler()

        assert first is second
        assert len([h for h in pristine_root.handlers if isinstance(h, DatabaseLogHandler)]) == 1

    def test_installed_handler_only_takes_warning_and_above(self, pristine_root):
        assert install_database_log_handler().level == logging.WARNING


class TestRetentionJobCompatibility:
    def test_retention_job_sql_runs_against_this_table(self, db):
        result = db.execute(
            text("DELETE FROM app_logs WHERE created_at < now() - make_interval(days => :days)"),
            {"days": 30},
        )

        assert result.rowcount >= 0
