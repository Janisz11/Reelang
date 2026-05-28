import logging
from datetime import datetime
from typing import Dict, List, Set

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

R2_REELS_QUERY_LIMIT = 20
FRESH_REEL_MAX_DAYS = 7


def get_seen_reel_ids(user_id: str, db: Session) -> Set[str]:
    rows = db.execute(
        text("SELECT reel_id FROM user_feed_queue WHERE user_id = :uid"),
        {"uid": user_id}
    ).fetchall()
    return {r[0] for r in rows}


async def get_r2_reels_for_user(user_id: str, profile: Dict, db: Session) -> List[Dict]:
    rows = db.execute(
        text("""
            SELECT id, title, language, level, tags, r2_key, created_at
            FROM reels
            WHERE r2_key IS NOT NULL
            AND owner_user_id != :uid
            AND language = :lang
            AND id NOT IN (
                SELECT reel_id FROM user_feed_queue
                WHERE user_id = :uid
            )
            ORDER BY created_at DESC
            LIMIT :limit
        """),
        {"uid": user_id, "lang": profile["primary_language"], "limit": R2_REELS_QUERY_LIMIT}
    ).fetchall()

    reels = []
    for row in rows:
        is_fresh = False
        if row[6]:
            try:
                created = row[6] if hasattr(row[6], 'date') else datetime.fromisoformat(str(row[6]))
                is_fresh = (datetime.utcnow() - created).days <= FRESH_REEL_MAX_DAYS
            except Exception:
                pass

        reels.append({
            "reel_id": row[0],
            "title": row[1],
            "language": row[2],
            "level": row[3],
            "tags": row[4],
            "source": "r2",
            "is_fresh": is_fresh,
        })

    return reels


async def enqueue_reel(user_id: str, reel_id: str, score: float, db: Session) -> bool:
    try:
        existing = db.execute(
            text("SELECT 1 FROM user_feed_queue WHERE user_id = :uid AND reel_id = :rid"),
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
