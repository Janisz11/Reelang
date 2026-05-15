import logging

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from ..agents.feed_curator import run_curation_cycle
from ..config import SCHEDULER_INTERVAL_MINUTES
from ..database import SessionLocal

logger = logging.getLogger(__name__)


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
    return scheduler


async def run_curation_job() -> None:
    """Wrapper for the curation cycle with its own DB session."""
    logger.info("Running scheduled feed curation...")
    db = SessionLocal()
    try:
        await run_curation_cycle(db)
    except Exception as e:
        logger.error(f"Curation job failed: {e}")
    finally:
        db.close()
