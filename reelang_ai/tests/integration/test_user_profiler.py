import uuid
from datetime import date, timedelta

import pytest

from reelang_ai.recommenders.user_profiler import (
    get_all_active_users,
    get_user_profile,
    get_users_needing_refill,
)
from .db_fixtures import (
    insert_activity_log,
    insert_profile,
    insert_queue_entry,
    insert_reel,
    insert_word,
)

pytestmark = pytest.mark.db


def new_user_id() -> str:
    return f"user-{uuid.uuid4()}"


class TestGetUserProfile:
    def test_unknown_user_gets_defaults(self, db):
        user_id = new_user_id()

        profile = get_user_profile(user_id, db)

        assert profile["primary_language"] == "es"
        assert profile["level"] == "A1"
        assert profile["top_tags"] == []
        assert profile["secondary_languages"] == []
        assert profile["seen_reel_ids"] == []

    def test_profile_level_is_mapped_to_a_prefix(self, db):
        user_id = new_user_id()
        insert_profile(db, user_id, level=3)

        profile = get_user_profile(user_id, db)

        assert profile["level"] == "A3"

    def test_primary_language_falls_back_to_word_language(self, db):
        user_id = new_user_id()
        insert_word(db, user_id, "fr")
        insert_word(db, user_id, "fr")
        insert_word(db, user_id, "de")

        profile = get_user_profile(user_id, db)

        assert profile["primary_language"] == "fr"
        assert profile["word_languages"] == ["fr", "de"]

    def test_primary_language_prefers_watched_language(self, db):
        user_id = new_user_id()
        insert_word(db, user_id, "fr")

        reel_de = insert_reel(db, language="de")
        insert_queue_entry(db, user_id, reel_de)
        insert_activity_log(db, user_id, date.today(), watch_time_ms=5000)

        profile = get_user_profile(user_id, db)

        assert profile["primary_language"] == "de"
        assert profile["watched_languages"] == ["de"]

    def test_top_tags_counted_from_consumed_reels(self, db):
        user_id = new_user_id()

        reel_a = insert_reel(db, tags="food,travel")
        reel_b = insert_reel(db, tags="food,sport")
        reel_c = insert_reel(db, tags="music")

        insert_queue_entry(db, user_id, reel_a, consumed=True)
        insert_queue_entry(db, user_id, reel_b, consumed=True)
        insert_queue_entry(db, user_id, reel_c, consumed=False)

        profile = get_user_profile(user_id, db)

        assert profile["top_tags"][0] == "food"
        assert "music" not in profile["top_tags"]

    def test_seen_reel_ids_lists_all_queued_reels(self, db):
        user_id = new_user_id()
        reel_a = insert_reel(db)
        reel_b = insert_reel(db)
        insert_queue_entry(db, user_id, reel_a)
        insert_queue_entry(db, user_id, reel_b, consumed=True)

        profile = get_user_profile(user_id, db)

        assert set(profile["seen_reel_ids"]) == {reel_a, reel_b}


class TestGetUsersNeedingRefill:
    def test_user_below_min_queue_size_needs_refill(self, db):
        user_id = new_user_id()
        for _ in range(3):
            reel_id = insert_reel(db)
            insert_queue_entry(db, user_id, reel_id, consumed=False)

        users = get_users_needing_refill(db, min_queue_size=5)

        assert user_id in users

    def test_user_with_full_queue_and_no_activity_not_returned(self, db):
        user_id = new_user_id()
        for _ in range(5):
            reel_id = insert_reel(db)
            insert_queue_entry(db, user_id, reel_id, consumed=False)

        users = get_users_needing_refill(db, min_queue_size=5)

        assert user_id not in users

    def test_active_user_with_no_queue_needs_refill(self, db):
        user_id = new_user_id()
        insert_activity_log(db, user_id, date.today())

        users = get_users_needing_refill(db, min_queue_size=5)

        assert user_id in users


class TestGetAllActiveUsers:
    def test_recently_active_user_is_returned(self, db):
        user_id = new_user_id()
        insert_activity_log(db, user_id, date.today())

        users = get_all_active_users(db)

        assert user_id in users

    def test_inactive_user_is_not_returned(self, db):
        user_id = new_user_id()
        insert_activity_log(db, user_id, date.today() - timedelta(days=30))

        users = get_all_active_users(db)

        assert user_id not in users
