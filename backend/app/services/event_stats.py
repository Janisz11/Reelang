from __future__ import annotations

from typing import Dict, List

from sqlalchemy import text
from sqlalchemy.orm import Session

from ..schemas import (
    EVENT_STATS_RECENT_LIMIT,
    EVENT_STATS_TOP_LIMIT,
    EventStatsResponse,
    EventStatsWindow,
    EventTimeBucket,
    ReelRateEntry,
    RecentEvent,
)

WINDOW_SETTINGS: Dict[str, Dict[str, str]] = {
    "24h": {"truncate": "hour", "interval": "24 hours"},
    "14d": {"truncate": "day", "interval": "14 days"},
}

TIME_SERIES_SQL = text(
    """
    SELECT date_trunc(:truncate, server_timestamp) AS bucket,
           event_type,
           count(*) AS count
    FROM reel_events
    WHERE server_timestamp >= now() - CAST(:interval AS interval)
    GROUP BY bucket, event_type
    ORDER BY bucket, event_type
    """
)

RATE_COLUMNS = frozenset({"completions", "skips"})

TOP_RATE_SQL = """
    SELECT s.reel_id,
           r.title,
           s.impressions,
           s.{column} AS count,
           s.{column}::float / s.impressions AS rate
    FROM reel_stats s
    LEFT JOIN reels r ON r.id = s.reel_id
    WHERE s.impressions > 0
    ORDER BY rate DESC, s.impressions DESC, s.reel_id
    LIMIT :limit
"""

RECENT_EVENTS_SQL = text(
    """
    SELECT event_id, event_type, reel_id, platform, server_timestamp
    FROM reel_events
    ORDER BY server_timestamp DESC, event_id DESC
    LIMIT :limit
    """
)


def _time_series(db: Session, window: EventStatsWindow) -> List[EventTimeBucket]:
    settings = WINDOW_SETTINGS[window]
    rows = db.execute(
        TIME_SERIES_SQL,
        {"truncate": settings["truncate"], "interval": settings["interval"]},
    ).fetchall()

    return [
        EventTimeBucket(bucket=row.bucket, event_type=row.event_type, count=row.count)
        for row in rows
    ]


def _top_by_rate(db: Session, column: str) -> List[ReelRateEntry]:
    if column not in RATE_COLUMNS:
        raise ValueError(f"Unsupported rate column: {column}")

    rows = db.execute(
        text(TOP_RATE_SQL.format(column=column)), {"limit": EVENT_STATS_TOP_LIMIT}
    ).fetchall()

    return [
        ReelRateEntry(
            reel_id=row.reel_id,
            title=row.title,
            impressions=row.impressions,
            count=row.count,
            rate=row.rate,
        )
        for row in rows
    ]


def _recent_events(db: Session) -> List[RecentEvent]:
    rows = db.execute(RECENT_EVENTS_SQL, {"limit": EVENT_STATS_RECENT_LIMIT}).fetchall()

    return [
        RecentEvent(
            event_id=row.event_id,
            event_type=row.event_type,
            reel_id=row.reel_id,
            platform=row.platform,
            server_timestamp=row.server_timestamp,
        )
        for row in rows
    ]


def get_event_stats(db: Session, window: EventStatsWindow) -> EventStatsResponse:
    """Dashboard aggregates. Reels with no impressions are ranked out, never divided by zero."""
    return EventStatsResponse(
        window=window,
        time_series=_time_series(db, window),
        top_completion=_top_by_rate(db, "completions"),
        top_skip=_top_by_rate(db, "skips"),
        recent_events=_recent_events(db),
    )
