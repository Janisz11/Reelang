import logging
from typing import Dict, List

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)


def get_user_profile(user_id: str, db: Session) -> Dict:
    """Build user learning profile from database."""
    from collections import Counter

    profile_row = db.execute(
        text("SELECT username, level FROM profiles WHERE user_id = :uid"),
        {"uid": user_id},
    ).fetchone()

    # Most saved word languages (strong signal)
    word_langs = db.execute(
        text("""
            SELECT language, COUNT(*) as cnt
            FROM words
            WHERE user_id = :uid
            GROUP BY language
            ORDER BY cnt DESC
            LIMIT 3
        """),
        {"uid": user_id},
    ).fetchall()

    # Languages from most watched reels (by watch_time_ms)
    watched_langs = db.execute(
        text("""
            SELECT r.language, SUM(al.watch_time_ms) as total_watch
            FROM activity_logs al
            JOIN user_feed_queue q ON q.user_id = al.user_id
            JOIN reels r ON r.id = q.reel_id
            WHERE al.user_id = :uid
            GROUP BY r.language
            ORDER BY total_watch DESC
            LIMIT 3
        """),
        {"uid": user_id},
    ).fetchall()

    # Popular tags from reels user watched
    watched_tags = db.execute(
        text("""
            SELECT r.tags
            FROM user_feed_queue q
            JOIN reels r ON r.id = q.reel_id
            WHERE q.user_id = :uid
            AND r.tags IS NOT NULL
            AND r.tags != ''
            AND q.consumed = true
            LIMIT 50
        """),
        {"uid": user_id},
    ).fetchall()

    tag_counter = Counter()
    for row in watched_tags:
        if row[0]:
            tags = [t.strip().lower() for t in row[0].split(",") if t.strip()]
            tag_counter.update(tags)
    top_tags = [tag for tag, _ in tag_counter.most_common(5)]

    # Reel IDs already in queue
    seen_ids = db.execute(
        text("SELECT reel_id FROM user_feed_queue WHERE user_id = :uid"),
        {"uid": user_id},
    ).fetchall()

    primary_language = "es"
    if watched_langs:
        primary_language = watched_langs[0][0].lower()
    elif word_langs:
        primary_language = word_langs[0][0].lower()

    all_langs = {r[0].lower() for r in watched_langs} | {r[0].lower() for r in word_langs}
    secondary_languages = [l for l in all_langs if l != primary_language][:2]

    level = "A1"
    if profile_row and profile_row[1]:
        raw = profile_row[1]
        level = f"A{raw}" if isinstance(raw, int) else "A1"

    return {
        "user_id": user_id,
        "primary_language": primary_language,
        "secondary_languages": secondary_languages,
        "level": level,
        "seen_reel_ids": [r[0] for r in seen_ids],
        "word_languages": [r[0] for r in word_langs],
        "watched_languages": [r[0] for r in watched_langs],
        "top_tags": top_tags,
    }


def get_users_needing_refill(db: Session, min_queue_size: int = 5) -> List[str]:
    """Find users whose unconsumed feed queue is below min_queue_size, plus active users with no queue at all."""
    rows = db.execute(
        text("""
            SELECT user_id, COUNT(*) AS queue_size
            FROM user_feed_queue
            WHERE consumed = false
            GROUP BY user_id
            HAVING COUNT(*) < :min_size
        """),
        {"min_size": min_queue_size},
    ).fetchall()

    all_active = db.execute(
        text("""
            SELECT DISTINCT user_id FROM activity_logs
            WHERE date >= CURRENT_DATE - INTERVAL '7 days'
        """)
    ).fetchall()

    users_with_queue = db.execute(
        text("SELECT DISTINCT user_id FROM user_feed_queue")
    ).fetchall()

    users_below_threshold = {r[0] for r in rows}
    active_user_ids = {r[0] for r in all_active}
    users_with_any_queue = {r[0] for r in users_with_queue}

    users_no_queue = active_user_ids - users_with_any_queue

    return list(users_below_threshold | users_no_queue)


def get_all_active_users(db: Session) -> List[str]:
    """Get users active in the last 7 days."""
    rows = db.execute(
        text("""
            SELECT DISTINCT user_id FROM activity_logs
            WHERE date >= CURRENT_DATE - INTERVAL '7 days'
        """)
    ).fetchall()
    return [r[0] for r in rows]
