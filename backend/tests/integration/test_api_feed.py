import pytest
from app.models import Reel, Profile
import uuid
from datetime import datetime


def create_test_reel(db, youtube_id: str = None, language: str = "es") -> Reel:
    reel = Reel(
        id=str(uuid.uuid4()),
        title=f"Test Reel {youtube_id}",
        language=language,
        youtube_id=youtube_id,
        likes_count=0,
        created_at=datetime.utcnow(),
    )
    db.add(reel)
    db.commit()
    db.refresh(reel)
    return reel


def create_test_profile(db, user_id: str) -> Profile:
    profile = Profile(
        user_id=user_id,
        username=f"user_{user_id[:8]}",
        created_at=datetime.utcnow(),
    )
    db.add(profile)
    db.commit()
    return profile


def _auth(user_id: str) -> dict:
    return {"Authorization": f"Bearer {user_id}"}


def test_get_feed_empty_returns_empty_list(client):
    response = client.get("/api/v1/feed", headers=_auth("new-user-123"))
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_get_feed_falls_back_to_latest_reels(client, db):
    create_test_reel(db, youtube_id="fallback123")

    response = client.get("/api/v1/feed", headers=_auth("test-user-fallback"))
    assert response.status_code == 200
    reels = response.json()
    assert len(reels) >= 1


def test_get_feed_respects_limit(client, db):
    for i in range(5):
        create_test_reel(db, youtube_id=f"limit-test-{i}")

    response = client.get("/api/v1/feed?limit=3", headers=_auth("test-user"))
    assert response.status_code == 200
    assert len(response.json()) <= 3


def test_mark_consumed_updates_queue(client, db):
    from sqlalchemy import text

    reel = create_test_reel(db, youtube_id="consumed-test")
    user_id = "consume-user-123"

    db.execute(
        text("""
            INSERT INTO user_feed_queue
            (id, user_id, reel_id, score, consumed, added_at)
            VALUES (:id, :uid, :rid, :score, false, :now)
        """),
        {
            "id": str(uuid.uuid4()),
            "uid": user_id,
            "rid": reel.id,
            "score": 1.0,
            "now": datetime.utcnow(),
        },
    )
    db.commit()

    response = client.post(f"/api/v1/feed/consumed/{reel.id}", headers=_auth(user_id))
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] == True
    assert data["remaining_queue"] == 0


def test_mark_consumed_returns_remaining_count(client, db):
    from sqlalchemy import text

    user_id = "remaining-test-user"
    reels = [create_test_reel(db, youtube_id=f"remaining-{i}") for i in range(3)]

    for reel in reels:
        db.execute(
            text("""
                INSERT INTO user_feed_queue
                (id, user_id, reel_id, score, consumed, added_at)
                VALUES (:id, :uid, :rid, :score, false, :now)
            """),
            {
                "id": str(uuid.uuid4()),
                "uid": user_id,
                "rid": reel.id,
                "score": 1.0,
                "now": datetime.utcnow(),
            },
        )
    db.commit()

    response = client.post(f"/api/v1/feed/consumed/{reels[0].id}", headers=_auth(user_id))
    assert response.status_code == 200
    assert response.json()["remaining_queue"] == 2


def test_feed_shows_queued_reels_first(client, db):
    from sqlalchemy import text

    user_id = "queue-priority-user"
    queued_reel = create_test_reel(db, youtube_id="queued-reel-001")
    create_test_reel(db, youtube_id="other-reel-002")

    db.execute(
        text("""
            INSERT INTO user_feed_queue
            (id, user_id, reel_id, score, consumed, added_at)
            VALUES (:id, :uid, :rid, :score, false, :now)
        """),
        {
            "id": str(uuid.uuid4()),
            "uid": user_id,
            "rid": queued_reel.id,
            "score": 1.0,
            "now": datetime.utcnow(),
        },
    )
    db.commit()

    response = client.get("/api/v1/feed?limit=10", headers=_auth(user_id))
    assert response.status_code == 200
    reels = response.json()
    assert len(reels) >= 1
    assert reels[0]["id"] == queued_reel.id


def test_refill_endpoint_returns_status(client):
    response = client.post("/api/v1/feed/refill", headers=_auth("refill-test-user"))
    assert response.status_code == 200
    data = response.json()
    assert "remaining" in data
    assert "refill_needed" in data


def test_refill_needed_when_queue_empty(client):
    response = client.post("/api/v1/feed/refill", headers=_auth("empty-queue-user"))
    assert response.status_code == 200
    assert response.json()["refill_needed"] == True
