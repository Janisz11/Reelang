import uuid
from datetime import datetime, timedelta

import pytest
from sqlalchemy import text

from reelang_ai.eventqueue.queue_manager import (
    enqueue_reel,
    get_r2_reels_for_user,
    get_seen_reel_ids,
)
from .db_fixtures import insert_queue_entry, insert_reel

pytestmark = pytest.mark.db


def new_user_id() -> str:
    return f"user-{uuid.uuid4()}"


class TestGetSeenReelIds:
    def test_returns_reel_ids_in_users_queue(self, db):
        user_id = new_user_id()
        reel_a = insert_reel(db)
        reel_b = insert_reel(db)
        insert_queue_entry(db, user_id, reel_a)
        insert_queue_entry(db, user_id, reel_b, consumed=True)

        seen = get_seen_reel_ids(user_id, db)

        assert seen == {reel_a, reel_b}

    def test_returns_empty_set_when_no_queue(self, db):
        user_id = new_user_id()

        assert get_seen_reel_ids(user_id, db) == set()


class TestGetR2ReelsForUser:
    async def test_returns_matching_r2_reel(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(db, language="es", r2_key="reels/some-key.mp4", owner_user_id="other-user")
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        assert any(r["reel_id"] == reel_id for r in results)
        match = next(r for r in results if r["reel_id"] == reel_id)
        assert match["source"] == "r2"
        assert match["language"] == "es"

    async def test_excludes_reel_without_r2_key(self, db):
        user_id = new_user_id()
        insert_reel(db, language="es", r2_key=None, owner_user_id="other-user")
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        assert results == []

    async def test_excludes_reel_with_different_language(self, db):
        user_id = new_user_id()
        insert_reel(db, language="fr", r2_key="reels/key.mp4", owner_user_id="other-user")
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        assert results == []

    async def test_excludes_own_reel(self, db):
        user_id = new_user_id()
        insert_reel(db, language="es", r2_key="reels/key.mp4", owner_user_id=user_id)
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        assert results == []

    async def test_excludes_reel_already_in_queue(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(db, language="es", r2_key="reels/key.mp4", owner_user_id="other-user")
        insert_queue_entry(db, user_id, reel_id)
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        assert results == []

    async def test_recent_reel_is_marked_fresh(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(
            db, language="es", r2_key="reels/key.mp4", owner_user_id="other-user",
            created_at=datetime.utcnow(),
        )
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        match = next(r for r in results if r["reel_id"] == reel_id)
        assert match["is_fresh"] is True

    async def test_old_reel_is_not_marked_fresh(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(
            db, language="es", r2_key="reels/key.mp4", owner_user_id="other-user",
            created_at=datetime.utcnow() - timedelta(days=10),
        )
        profile = {"primary_language": "es"}

        results = await get_r2_reels_for_user(user_id, profile, db)

        match = next(r for r in results if r["reel_id"] == reel_id)
        assert match["is_fresh"] is False


class TestEnqueueReel:
    async def test_enqueues_new_reel(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(db)

        added = await enqueue_reel(user_id, reel_id, 0.75, db)

        assert added is True
        row = db.execute(
            text("SELECT score, consumed FROM user_feed_queue WHERE user_id = :uid AND reel_id = :rid"),
            {"uid": user_id, "rid": reel_id},
        ).fetchone()
        assert row is not None
        assert row[0] == pytest.approx(0.75)
        assert row[1] is False

    async def test_duplicate_enqueue_returns_false(self, db):
        user_id = new_user_id()
        reel_id = insert_reel(db)
        insert_queue_entry(db, user_id, reel_id)

        added = await enqueue_reel(user_id, reel_id, 0.5, db)

        assert added is False
        count = db.execute(
            text("SELECT COUNT(*) FROM user_feed_queue WHERE user_id = :uid AND reel_id = :rid"),
            {"uid": user_id, "rid": reel_id},
        ).scalar()
        assert count == 1
