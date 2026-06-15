import logging
import random
from typing import Dict

import httpx
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..config import BACKEND_URL, INTERNAL_API_TOKEN, REELS_PER_RUN
from ..recommenders.user_profiler import (
    get_all_active_users,
    get_user_profile,
    get_users_needing_refill,
)
from ..recommenders.scorer import rank_candidates
from ..queue.queue_manager import enqueue_reel, get_r2_reels_for_user, get_seen_reel_ids
from ..sources.youtube_source import search_youtube_shorts

logger = logging.getLogger(__name__)

HTTP_CLIENT_TIMEOUT = 30.0
HTTP_SUCCESS_STATUSES = (200, 201)
YOUTUBE_SEARCH_MULTIPLIER = 3
TAG_EXPLORATION_PROBABILITY = 0.7
DEFAULT_CURATE_COUNT = 5


async def import_reel_to_backend(reel_data: Dict) -> str | None:
    try:
        headers = {"X-Internal-Token": INTERNAL_API_TOKEN} if INTERNAL_API_TOKEN else {}
        async with httpx.AsyncClient(timeout=HTTP_CLIENT_TIMEOUT) as client:
            resp = await client.post(
                f"{BACKEND_URL}/api/v1/reels/import",
                json={
                    "youtube_url": f"https://www.youtube.com/watch?v={reel_data['youtube_id']}",
                    "language": reel_data["language"],
                    "level": reel_data["level"],
                    "topic": reel_data.get("topic", "language_learning"),
                    "tags": reel_data.get("tags"),
                },
                headers=headers,
            )
            if resp.status_code in HTTP_SUCCESS_STATUSES:
                return resp.json().get("reel_id")
    except Exception as e:
        logger.error(f"Failed to import reel {reel_data.get('youtube_id')}: {e}")
    return None


async def curate_feed_for_user(user_id: str, db: Session, count: int = DEFAULT_CURATE_COUNT) -> int:
    profile = get_user_profile(user_id, db)
    logger.info(
        f"Profile for {user_id}: "
        f"lang={profile['primary_language']} "
        f"level={profile['level']} "
        f"tags={profile['top_tags']}"
    )

    seen_reel_ids = get_seen_reel_ids(user_id, db)

    existing_yt_rows = db.execute(
        text("SELECT youtube_id FROM reels WHERE youtube_id IS NOT NULL")
    ).fetchall()
    existing_yt_ids = [r[0] for r in existing_yt_rows]

    all_candidates = []

    r2_reels = await get_r2_reels_for_user(user_id, profile, db)
    for reel in r2_reels:
        if reel["reel_id"] not in seen_reel_ids:
            all_candidates.append(reel)
    logger.info(f"Found {len(r2_reels)} R2 candidates for {user_id}")

    use_tags = profile.get("top_tags", []) if random.random() < TAG_EXPLORATION_PROBABILITY else []
    yt_candidates = await search_youtube_shorts(
        language=profile["primary_language"],
        level=profile["level"],
        max_results=count * YOUTUBE_SEARCH_MULTIPLIER,
        exclude_ids=existing_yt_ids,
        tags=use_tags,
    )

    if len(yt_candidates) < count and use_tags:
        logger.info("Not enough tagged results, retrying without tags")
        extra = await search_youtube_shorts(
            language=profile["primary_language"],
            level=profile["level"],
            max_results=count,
            exclude_ids=existing_yt_ids + [c["youtube_id"] for c in yt_candidates],
            tags=[],
        )
        yt_candidates += extra

    for reel in yt_candidates:
        reel["source"] = "youtube"
        reel["is_fresh"] = True
        all_candidates.append(reel)

    ranked = rank_candidates(all_candidates, profile)
    logger.info(f"Total ranked candidates: {len(ranked)}")

    added = 0
    for candidate in ranked:
        if added >= count:
            break

        if candidate.get("source") == "r2":
            reel_id = candidate["reel_id"]
        else:
            reel_id = await import_reel_to_backend(candidate)
            if not reel_id:
                continue

        success = await enqueue_reel(user_id, reel_id, candidate["score"], db)
        if success:
            added += 1
            logger.info(
                f"Queued reel {reel_id} "
                f"(score={candidate['score']}, source={candidate.get('source')}) "
                f"for {user_id}"
            )

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
