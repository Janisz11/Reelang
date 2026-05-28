import logging
from typing import Dict

import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..config import BACKEND_URL, REELS_PER_RUN
from ..recommenders.user_profiler import (
    get_all_active_users,
    get_user_profile,
    get_users_needing_refill,
)
from ..sources.youtube_source import search_youtube_shorts

logger = logging.getLogger(__name__)


async def import_reel_to_backend(reel_data: Dict) -> str | None:
   
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(
                f"{BACKEND_URL}/api/v1/reels/import",
                json={
                    "youtube_url": f"https://www.youtube.com/watch?v={reel_data['youtube_id']}",
                    "language": reel_data["language"],
                    "level": reel_data["level"],
                    "topic": reel_data.get("topic", "language_learning"),
                    "tags": reel_data.get("tags"),
                },
            )
            if resp.status_code in (200, 201):
                return resp.json().get("reel_id")
    except Exception as e:
        logger.error(f"Failed to import reel {reel_data.get('youtube_id')}: {e}")
    return None


async def enqueue_reel_for_user(
    user_id: str, reel_id: str, score: float, db: Session
) -> bool:
    
    try:
        existing = db.execute(
            text(
                "SELECT 1 FROM user_feed_queue WHERE user_id = :uid AND reel_id = :rid"
            ),
            {"uid": user_id, "rid": reel_id},
        ).fetchone()
        if existing:
            return False

        db.execute(
            text("""
                INSERT INTO user_feed_queue (id, user_id, reel_id, score, consumed, added_at)
                VALUES (gen_random_uuid(), :uid, :rid, :score, false, NOW())
            """),
            {"uid": user_id, "rid": reel_id, "score": score},
        )
        db.commit()
        return True
    except Exception as e:
        logger.error(f"Failed to enqueue reel {reel_id} for {user_id}: {e}")
        db.rollback()
        return False


async def curate_feed_for_user(user_id: str, db: Session, count: int = 5) -> int:
    
    profile = get_user_profile(user_id, db)

    existing_yt_ids = db.execute(
        text("SELECT youtube_id FROM reels WHERE youtube_id IS NOT NULL")
    ).fetchall()
    exclude_ids = [r[0] for r in existing_yt_ids]

    import random

    use_tags = profile.get("top_tags", []) if random.random() < 0.7 else []

    candidates = await search_youtube_shorts(
        language=profile["primary_language"],
        level=profile["level"],
        max_results=count * 2,
        exclude_ids=exclude_ids,
        tags=use_tags,
    )

    if len(candidates) < count and use_tags:
        logger.info(f"Not enough tagged results, retrying without tags")
        extra = await search_youtube_shorts(
            language=profile["primary_language"],
            level=profile["level"],
            max_results=count,
            exclude_ids=exclude_ids + [c["youtube_id"] for c in candidates],
            tags=[],
        )
        candidates += extra

    added = 0
    for candidate in candidates:
        if added >= count:
            break
        reel_id = await import_reel_to_backend(candidate)
        if not reel_id:
            continue
        success = await enqueue_reel_for_user(user_id, reel_id, score=1.0, db=db)
        if success:
            added += 1
            logger.info(f"Queued reel {reel_id} for user {user_id}")

    logger.info(f"Curated {added} reels for user {user_id}")
    return added


async def run_curation_cycle(db: Session) -> None:
    
    logger.info("Starting curation cycle...")

    users = get_users_needing_refill(db)
    if not users:
        users = get_all_active_users(db)

    logger.info(f"Found {len(users)} users needing refill")

    for user_id in users:
        try:
            added = await curate_feed_for_user(user_id, db, count=REELS_PER_RUN)
            logger.info(f"Added {added} reels for user {user_id}")
        except Exception as e:
            logger.error(f"Curation failed for user {user_id}: {e}")
