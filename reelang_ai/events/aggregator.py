import json
import logging
from datetime import datetime
from typing import Dict, Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

REEL_COUNTER_DELTAS = {
    "reel_impression": ("impressions", 1),
    "reel_completed": ("completions", 1),
    "skip": ("skips", 1),
    "like": ("likes", 1),
    "unlike": ("likes", -1),
    "save": ("saves", 1),
    "unsave": ("saves", -1),
}

WATCH_PERCENT_EVENTS = ("watch_progress", "reel_completed")

VIEW_START_EVENTS = ("reel_impression", "replay")

RAW_ONLY_EVENTS = ("reel_load_timing",)
"""Performance telemetry: kept in the raw log, never folded into interaction counters."""

LIKE_STATE = {"like": True, "unlike": False}
SAVE_STATE = {"save": True, "unsave": False}


class MalformedEvent(ValueError):
    """Raised when an event can never be persisted, no matter how many times it is retried."""


def _parse_timestamp(value) -> datetime:
    if isinstance(value, datetime):
        return value
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as e:
        raise MalformedEvent(f"invalid client_timestamp: {value!r}") from e


def _watch_percent(event_type: str, payload: Dict) -> Optional[float]:
    if event_type not in WATCH_PERCENT_EVENTS:
        return None

    raw = payload.get("watch_percent")
    if raw is None:
        return 100.0 if event_type == "reel_completed" else None

    try:
        return max(0.0, min(100.0, float(raw)))
    except (TypeError, ValueError):
        raise MalformedEvent(f"invalid watch_percent: {raw!r}")


def _insert_raw_event(event: Dict, db: Session) -> bool:
    """Append to the raw log. Returns False when this event_id was already stored."""
    row = db.execute(
        text("""
            INSERT INTO reel_events (
                event_id, event_type, user_id, reel_id, session_id,
                platform, client_timestamp, payload
            )
            VALUES (
                CAST(:event_id AS uuid), :event_type, :user_id, :reel_id, CAST(:session_id AS uuid),
                :platform, :client_timestamp, CAST(:payload AS jsonb)
            )
            ON CONFLICT (event_id) DO NOTHING
            RETURNING event_id
        """),
        {
            "event_id": event["event_id"],
            "event_type": event["event_type"],
            "user_id": event["user_id"],
            "reel_id": event["reel_id"],
            "session_id": event["session_id"],
            "platform": event["platform"],
            "client_timestamp": _parse_timestamp(event["client_timestamp"]),
            "payload": json.dumps(event.get("payload") or {}),
        },
    ).fetchone()
    return row is not None


def _apply_reel_stats(event_type: str, reel_id: str, db: Session) -> None:
    counter = REEL_COUNTER_DELTAS.get(event_type)

    if counter is None:
        db.execute(
            text("INSERT INTO reel_stats (reel_id) VALUES (:rid) ON CONFLICT (reel_id) DO NOTHING"),
            {"rid": reel_id},
        )
        return

    column, delta = counter
    db.execute(
        text(f"""
            INSERT INTO reel_stats (reel_id, {column}, updated_at)
            VALUES (:rid, GREATEST(:delta, 0), now())
            ON CONFLICT (reel_id) DO UPDATE
            SET {column} = GREATEST(reel_stats.{column} + :delta, 0),
                updated_at = now()
        """),
        {"rid": reel_id, "delta": delta},
    )


def _recompute_avg_watch_percent(reel_id: str, db: Session) -> None:
    db.execute(
        text("""
            UPDATE reel_stats
            SET avg_watch_percent = (
                    SELECT AVG(LEAST(100, GREATEST(0, sample)))
                    FROM (
                        SELECT COALESCE(
                                   (payload->>'watch_percent')::float,
                                   CASE WHEN event_type = 'reel_completed' THEN 100 END
                               ) AS sample
                        FROM reel_events
                        WHERE reel_id = :rid
                          AND event_type IN ('watch_progress', 'reel_completed')
                    ) samples
                    WHERE sample IS NOT NULL
                ),
                updated_at = now()
            WHERE reel_id = :rid
        """),
        {"rid": reel_id},
    )


def _apply_user_reel_stats(event: Dict, watch_percent: Optional[float], db: Session) -> None:
    event_type = event["event_type"]
    watch_increment = 1 if event_type in VIEW_START_EVENTS else 0
    liked = LIKE_STATE.get(event_type)
    saved = SAVE_STATE.get(event_type)

    db.execute(
        text("""
            INSERT INTO user_reel_stats (
                user_id, reel_id, watch_count, max_watch_percent, liked, saved, last_interaction
            )
            VALUES (
                :uid, :rid, :watch_increment, :watch_percent,
                COALESCE(:liked, false), COALESCE(:saved, false), :ts
            )
            ON CONFLICT (user_id, reel_id) DO UPDATE
            SET watch_count = user_reel_stats.watch_count + :watch_increment,
                max_watch_percent = GREATEST(
                    user_reel_stats.max_watch_percent, EXCLUDED.max_watch_percent
                ),
                liked = COALESCE(:liked, user_reel_stats.liked),
                saved = COALESCE(:saved, user_reel_stats.saved),
                last_interaction = GREATEST(
                    user_reel_stats.last_interaction, EXCLUDED.last_interaction
                )
        """),
        {
            "uid": event["user_id"],
            "rid": event["reel_id"],
            "watch_increment": watch_increment,
            "watch_percent": watch_percent or 0.0,
            "liked": liked,
            "saved": saved,
            "ts": _parse_timestamp(event["client_timestamp"]),
        },
    )


def persist_event(event: Dict, db: Session) -> bool:
    """Store one event and fold it into both aggregate tables.

    Returns False when the event was a duplicate and no aggregate was touched.
    """
    for field in ("event_id", "event_type", "user_id", "reel_id", "session_id", "platform", "client_timestamp"):
        if not event.get(field):
            raise MalformedEvent(f"missing required field: {field}")

    event_type = event["event_type"]
    payload = event.get("payload") or {}
    watch_percent = _watch_percent(event_type, payload)

    try:
        if not _insert_raw_event(event, db):
            db.commit()
            logger.info(f"Skipping duplicate event {event['event_id']}")
            return False

        if event_type in RAW_ONLY_EVENTS:
            db.commit()
            return True

        _apply_reel_stats(event_type, event["reel_id"], db)

        if event_type in WATCH_PERCENT_EVENTS:
            _recompute_avg_watch_percent(event["reel_id"], db)

        _apply_user_reel_stats(event, watch_percent, db)
        db.commit()
        return True
    except Exception:
        db.rollback()
        raise
