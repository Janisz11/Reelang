import asyncio
import logging

from .config import SCHEDULER_INTERVAL_MINUTES
from .scheduler.jobs import create_scheduler, run_curation_job

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


async def main() -> None:
    logger.info("Starting ReeLang AI service...")

    logger.info("Running initial curation cycle...")
    await run_curation_job()

    scheduler = create_scheduler()
    scheduler.start()
    logger.info(
        f"Scheduler started — running every {SCHEDULER_INTERVAL_MINUTES} minutes"
    )

    try:
        while True:
            await asyncio.sleep(60)
    except (KeyboardInterrupt, SystemExit):
        scheduler.shutdown()
        logger.info("ReeLang AI service stopped")


if __name__ == "__main__":
    asyncio.run(main())
