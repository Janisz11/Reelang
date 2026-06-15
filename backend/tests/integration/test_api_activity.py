import uuid
from datetime import date, datetime, timedelta

from app.models import ActivityLog, Profile

USER = "streak-test-user"
USER_HEADERS = {"Authorization": f"Bearer {USER}"}


def _log(client, reels_watched=1, watch_time_ms=60000):
    return client.post(
        "/api/v1/activity/log",
        headers=USER_HEADERS,
        json={"watch_time_ms": watch_time_ms, "reels_watched": reels_watched, "words_saved": 0},
    )


def _streak(client):
    return client.get("/api/v1/profiles/me", headers=USER_HEADERS).json()["streak_days"]


def test_first_activity_sets_streak_to_1(client):
    _log(client)
    assert _streak(client) == 1


def test_second_log_same_day_does_not_increment_streak(client):
    _log(client)
    _log(client)
    assert _streak(client) == 1


def test_active_yesterday_increments_streak(client, db):
    yesterday = date.today() - timedelta(days=1)
    db.add(ActivityLog(
        id=str(uuid.uuid4()),
        user_id=USER,
        date=yesterday,
        watch_time_ms=60000,
        reels_watched=1,
        words_saved=0,
    ))
    db.add(Profile(
        user_id=USER,
        username=f"user_{USER[:8]}",
        avatar_initials=USER[:2].upper(),
        created_at=datetime.utcnow(),
        streak_days=1,
    ))
    db.commit()

    _log(client)

    assert _streak(client) == 2


def test_gap_more_than_1_day_resets_streak_to_1(client, db):
    two_days_ago = date.today() - timedelta(days=2)
    db.add(ActivityLog(
        id=str(uuid.uuid4()),
        user_id=USER,
        date=two_days_ago,
        watch_time_ms=60000,
        reels_watched=1,
        words_saved=0,
    ))
    db.add(Profile(
        user_id=USER,
        username=f"user_{USER[:8]}",
        avatar_initials=USER[:2].upper(),
        created_at=datetime.utcnow(),
        streak_days=5,
    ))
    db.commit()

    _log(client)

    assert _streak(client) == 1


def test_zero_reels_watched_does_not_change_streak(client, db):
    db.add(Profile(
        user_id=USER,
        username=f"user_{USER[:8]}",
        avatar_initials=USER[:2].upper(),
        created_at=datetime.utcnow(),
        streak_days=3,
    ))
    db.commit()

    client.post(
        "/api/v1/activity/log",
        headers=USER_HEADERS,
        json={"watch_time_ms": 10000, "reels_watched": 0, "words_saved": 0},
    )

    assert _streak(client) == 3


def test_multiple_logs_same_day_accumulate_watch_time(client, db):
    db.add(ActivityLog(
        id=str(uuid.uuid4()),
        user_id=USER,
        date=date.today(),
        watch_time_ms=10000,
        reels_watched=1,
        words_saved=0,
    ))
    db.commit()

    _log(client, reels_watched=2, watch_time_ms=20000)

    log_entry = db.query(ActivityLog).filter_by(user_id=USER, date=date.today()).first()
    assert log_entry.reels_watched == 3
    assert log_entry.watch_time_ms == 30000


def test_auto_creates_profile_for_new_user(client):
    _log(client)
    response = client.get("/api/v1/profiles/me", headers=USER_HEADERS)
    assert response.status_code == 200
    assert response.json()["user_id"] == USER
