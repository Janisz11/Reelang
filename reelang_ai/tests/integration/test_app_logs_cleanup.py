from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import text

from reelang_ai.scheduler import jobs

pytestmark = pytest.mark.db

CREATE_APP_LOGS = text(
    """
    CREATE TABLE app_logs (
        id bigserial PRIMARY KEY,
        level varchar(20) NOT NULL,
        logger varchar(255) NOT NULL,
        message text NOT NULL,
        created_at timestamptz NOT NULL DEFAULT now()
    )
    """
)

INSERT_APP_LOG = text(
    """
    INSERT INTO app_logs (level, logger, message, created_at)
    VALUES (:level, :logger, :message, :created_at)
    """
)


@pytest.fixture
def app_logs(session_factory):
    """Provides an app_logs table. Drops it afterwards only if this fixture created it."""
    session = session_factory()
    pre_existing = (
        session.execute(text("SELECT to_regclass('public.app_logs')")).scalar() is not None
    )

    if not pre_existing:
        session.execute(CREATE_APP_LOGS)
    session.execute(text("DELETE FROM app_logs"))
    session.commit()

    yield session_factory

    cleanup = session_factory()
    if pre_existing:
        cleanup.execute(text("DELETE FROM app_logs"))
    else:
        cleanup.execute(text("DROP TABLE app_logs"))
    cleanup.commit()


def insert_log(session, days_ago: float, message: str) -> None:
    session.execute(
        INSERT_APP_LOG,
        {
            "level": "WARNING",
            "logger": "reelang_ai.test",
            "message": message,
            "created_at": datetime.now(timezone.utc) - timedelta(days=days_ago),
        },
    )


def remaining_messages(session) -> set:
    session.expire_all()
    rows = session.execute(text("SELECT message FROM app_logs ORDER BY created_at")).fetchall()
    return {row[0] for row in rows}


@pytest.fixture
def run_cleanup(monkeypatch, app_logs):
    async def run(retention_days: int = 30):
        monkeypatch.setattr(jobs, "RETENTION_DAYS", retention_days)
        monkeypatch.setattr(jobs, "SessionLocal", app_logs)
        await jobs.cleanup_old_app_logs()

    return run


class TestDeletesOnlyOldRows:
    async def test_old_rows_go_and_fresh_rows_stay(self, app_logs, run_cleanup):
        session = app_logs()
        insert_log(session, 90, "ancient")
        insert_log(session, 31, "just-too-old")
        insert_log(session, 29, "just-fresh-enough")
        insert_log(session, 0, "brand-new")
        session.commit()

        await run_cleanup(30)

        assert remaining_messages(app_logs()) == {"just-fresh-enough", "brand-new"}

    async def test_rows_just_inside_and_outside_the_window_are_split(self, app_logs, run_cleanup):
        session = app_logs()
        insert_log(session, 29.99, "inside-window")
        insert_log(session, 30.01, "outside-window")
        session.commit()

        await run_cleanup(30)

        assert remaining_messages(app_logs()) == {"inside-window"}

    async def test_retention_window_is_configurable(self, app_logs, run_cleanup):
        session = app_logs()
        insert_log(session, 10, "ten-days-old")
        insert_log(session, 3, "three-days-old")
        session.commit()

        await run_cleanup(7)

        assert remaining_messages(app_logs()) == {"three-days-old"}

    async def test_nothing_is_deleted_when_all_rows_are_fresh(self, app_logs, run_cleanup):
        session = app_logs()
        insert_log(session, 1, "yesterday")
        insert_log(session, 2, "two-days-ago")
        session.commit()

        await run_cleanup(30)

        assert remaining_messages(app_logs()) == {"yesterday", "two-days-ago"}

    async def test_all_rows_go_when_everything_is_stale(self, app_logs, run_cleanup):
        session = app_logs()
        insert_log(session, 60, "old-one")
        insert_log(session, 45, "old-two")
        session.commit()

        await run_cleanup(30)

        assert remaining_messages(app_logs()) == set()

    async def test_empty_table_is_handled(self, app_logs, run_cleanup):
        await run_cleanup(30)

        assert remaining_messages(app_logs()) == set()

    async def test_deletion_is_committed_and_visible_to_other_sessions(
        self, app_logs, run_cleanup
    ):
        session = app_logs()
        insert_log(session, 90, "stale")
        session.commit()

        await run_cleanup(30)

        fresh_session = app_logs()
        count = fresh_session.execute(text("SELECT count(*) FROM app_logs")).scalar()
        assert count == 0


class TestMissingTable:
    async def test_absent_app_logs_table_does_not_raise(self, monkeypatch, session_factory):
        session = session_factory()
        if session.execute(text("SELECT to_regclass('public.app_logs')")).scalar() is not None:
            pytest.skip("app_logs exists in this database")

        monkeypatch.setattr(jobs, "SessionLocal", session_factory)

        await jobs.cleanup_old_app_logs()
