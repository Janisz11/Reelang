import asyncio
import logging

import uvicorn
from fastapi import FastAPI

from .config import SCHEDULER_INTERVAL_MINUTES
from .database import SessionLocal
from .db_log_handler import install_database_log_handler
from .scheduler.jobs import create_scheduler, run_curation_job

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

install_database_log_handler()

api = FastAPI()


@api.post("/trigger/{user_id}")
async def trigger_curation(user_id: str):
    """Immediately curate feed for a specific user."""
    logger.info(f"Trigger received for user {user_id}")
    from .agents.feed_curator import curate_feed_for_user

    db = SessionLocal()
    try:
        added = await curate_feed_for_user(user_id, db, count=5)
        logger.info(f"Trigger completed: added {added} reels for {user_id}")
        return {"added": added, "user_id": user_id}
    except Exception as e:
        logger.error(f"Trigger failed: {e}")
        return {"added": 0, "error": str(e)}
    finally:
        db.close()


async def keep_alive() -> None:
    try:
        while True:
            await asyncio.sleep(60)
    except (KeyboardInterrupt, SystemExit):
        pass


async def main() -> None:
    logger.info("Starting ReeLang AI service...")

    logger.info("Running initial curation cycle...")
    await run_curation_job()

    scheduler = create_scheduler()
    scheduler.start()
    logger.info(
        f"Scheduler started — running every {SCHEDULER_INTERVAL_MINUTES} minutes"
    )

    config = uvicorn.Config(api, host="0.0.0.0", port=8001, log_level="warning")
    server = uvicorn.Server(config)

    await asyncio.gather(
        server.serve(),
        keep_alive(),
    )


if __name__ == "__main__":
    asyncio.run(main())
