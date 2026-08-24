import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import text

from ..agents.feed_curator import run_curation_cycle
from ..config import RETENTION_DAYS, SCHEDULER_INTERVAL_MINUTES
from ..database import SessionLocal

logger = logging.getLogger(__name__)

APP_LOGS_CLEANUP_HOUR = 4

DELETE_OLD_APP_LOGS = text(
    "DELETE FROM app_logs WHERE created_at < now() - make_interval(days => :days)"
)


def create_scheduler() -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler()
    scheduler.add_job(
        run_curation_job,
        "interval",
        minutes=SCHEDULER_INTERVAL_MINUTES,
        id="feed_curation",
        max_instances=1,
        coalesce=True,
    )
    scheduler.add_job(
        cleanup_old_app_logs,
        "cron",
        hour=APP_LOGS_CLEANUP_HOUR,
        minute=0,
        id="app_logs_cleanup",
        max_instances=1,
        coalesce=True,
    )
    return scheduler


async def run_curation_job() -> None:

    logger.info("Running scheduled feed curation...")
    db = SessionLocal()
    try:
        await run_curation_cycle(db)
    except Exception as e:
        logger.error(f"Curation job failed: {e}")
    finally:
        db.close()


async def cleanup_old_app_logs() -> None:
    """Drop app_logs rows older than RETENTION_DAYS. Never raises: the scheduler retries tomorrow."""
    try:
        db = SessionLocal()
    except Exception as e:
        logger.error(f"app_logs cleanup failed: {e}")
        return

    try:
        result = db.execute(DELETE_OLD_APP_LOGS, {"days": RETENTION_DAYS})
        db.commit()
        logger.info(
            f"app_logs cleanup: removed {result.rowcount} rows older than {RETENTION_DAYS} days"
        )
    except Exception as e:
        logger.error(f"app_logs cleanup failed: {e}")
        try:
            db.rollback()
        except Exception:
            pass
    finally:
        try:
            db.close()
        except Exception:
            pass
