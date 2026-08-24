from datetime import datetime, timedelta, timezone

import pytest

from app.models import AppLog

ADMIN_HEADERS = {"X-Admin-Token": "logs-secret"}


@pytest.fixture
def admin_token(monkeypatch):
    monkeypatch.setenv("ADMIN_TOKEN", "logs-secret")


@pytest.fixture
def seed(db):
    base = datetime.now(timezone.utc) - timedelta(hours=1)

    def insert(level: str, message: str, minutes_ago: int, logger_name="reelang.seed", context=None):
        entry = AppLog(
            level=level,
            logger_name=logger_name,
            message=message,
            context=context,
            created_at=base - timedelta(minutes=minutes_ago),
        )
        db.add(entry)
        db.flush()
        return entry

    return insert


def fetch(client, **params):
    response = client.get("/api/v1/admin/logs", headers=ADMIN_HEADERS, params=params)
    assert response.status_code == 200
    return response.json()


class TestAuth:
    def test_missing_token_returns_403(self, client, admin_token):
        assert client.get("/api/v1/admin/logs").status_code == 403

    def test_wrong_token_returns_403(self, client, admin_token):
        response = client.get("/api/v1/admin/logs", headers={"X-Admin-Token": "nope"})

        assert response.status_code == 403

    def test_unset_admin_token_still_rejects(self, client, monkeypatch):
        monkeypatch.delenv("ADMIN_TOKEN", raising=False)

        assert client.get("/api/v1/admin/logs", headers=ADMIN_HEADERS).status_code == 403

    def test_firebase_user_token_does_not_grant_access(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers={"Authorization": "Bearer some-user"}
        )

        assert response.status_code == 403


class TestOrdering:
    def test_newest_entries_come_first(self, client, admin_token, seed):
        seed("ERROR", "oldest", 30)
        seed("ERROR", "middle", 20)
        seed("ERROR", "newest", 10)

        messages = [entry["message"] for entry in fetch(client)]

        assert messages == ["newest", "middle", "oldest"]

    def test_created_at_is_descending(self, client, admin_token, seed):
        for minutes in (5, 25, 15):
            seed("WARNING", f"entry-{minutes}", minutes)

        timestamps = [entry["created_at"] for entry in fetch(client)]

        assert timestamps == sorted(timestamps, reverse=True)


class TestLevelFilter:
    def test_filters_to_a_single_level(self, client, admin_token, seed):
        seed("WARNING", "a-warning", 10)
        seed("ERROR", "an-error", 9)
        seed("CRITICAL", "a-critical", 8)

        entries = fetch(client, level="ERROR")

        assert [e["message"] for e in entries] == ["an-error"]

    def test_no_filter_returns_every_level(self, client, admin_token, seed):
        seed("WARNING", "a-warning", 10)
        seed("ERROR", "an-error", 9)
        seed("CRITICAL", "a-critical", 8)

        levels = {entry["level"] for entry in fetch(client)}

        assert levels == {"WARNING", "ERROR", "CRITICAL"}

    def test_critical_filter_excludes_errors(self, client, admin_token, seed):
        seed("ERROR", "an-error", 10)
        seed("CRITICAL", "a-critical", 9)

        entries = fetch(client, level="CRITICAL")

        assert [e["message"] for e in entries] == ["a-critical"]

    def test_unknown_level_is_rejected(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers=ADMIN_HEADERS, params={"level": "TRACE"}
        )

        assert response.status_code == 422

    def test_info_is_not_an_accepted_filter(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers=ADMIN_HEADERS, params={"level": "INFO"}
        )

        assert response.status_code == 422


class TestLimit:
    def test_limit_caps_the_result_size(self, client, admin_token, seed):
        for minutes in range(5):
            seed("ERROR", f"entry-{minutes}", minutes)

        assert len(fetch(client, limit=2)) == 2

    def test_limit_above_the_maximum_is_rejected(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers=ADMIN_HEADERS, params={"limit": 201}
        )

        assert response.status_code == 422

    def test_limit_at_the_maximum_is_accepted(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers=ADMIN_HEADERS, params={"limit": 200}
        )

        assert response.status_code == 200

    def test_zero_limit_is_rejected(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/logs", headers=ADMIN_HEADERS, params={"limit": 0}
        )

        assert response.status_code == 422


class TestCursorPagination:
    def test_before_returns_only_older_entries(self, client, admin_token, seed):
        for minutes in (10, 20, 30, 40):
            seed("ERROR", f"entry-{minutes}", minutes)

        first_page = fetch(client, limit=2)
        cursor = first_page[-1]["created_at"]
        second_page = fetch(client, limit=2, before=cursor)

        assert [e["message"] for e in first_page] == ["entry-10", "entry-20"]
        assert [e["message"] for e in second_page] == ["entry-30", "entry-40"]

    def test_pages_do_not_overlap(self, client, admin_token, seed):
        for minutes in range(6):
            seed("ERROR", f"entry-{minutes}", minutes)

        first_page = fetch(client, limit=3)
        second_page = fetch(client, limit=3, before=first_page[-1]["created_at"])

        first_ids = {e["id"] for e in first_page}
        second_ids = {e["id"] for e in second_page}
        assert first_ids & second_ids == set()

    def test_exhausted_cursor_returns_an_empty_list(self, client, admin_token, seed):
        seed("ERROR", "only-entry", 10)

        page = fetch(client)
        assert fetch(client, before=page[-1]["created_at"]) == []

    def test_cursor_combines_with_the_level_filter(self, client, admin_token, seed):
        seed("ERROR", "error-old", 30)
        seed("WARNING", "warning-mid", 20)
        seed("ERROR", "error-new", 10)

        page = fetch(client, level="ERROR", limit=1)
        next_page = fetch(client, level="ERROR", before=page[-1]["created_at"])

        assert [e["message"] for e in page] == ["error-new"]
        assert [e["message"] for e in next_page] == ["error-old"]


class TestResponseShape:
    def test_entry_exposes_the_documented_fields(self, client, admin_token, seed):
        seed("ERROR", "shaped", 10, context={"reel_id": "reel-1"})

        entry = fetch(client)[0]

        assert set(entry) == {
            "id",
            "level",
            "logger_name",
            "message",
            "context",
            "created_at",
        }

    def test_context_is_returned_as_an_object(self, client, admin_token, seed):
        seed("ERROR", "with-context", 10, context={"attempt": 3, "reel_id": "reel-7"})

        assert fetch(client)[0]["context"] == {"attempt": 3, "reel_id": "reel-7"}

    def test_missing_context_is_null(self, client, admin_token, seed):
        seed("ERROR", "no-context", 10)

        assert fetch(client)[0]["context"] is None

    def test_empty_table_returns_an_empty_list(self, client, admin_token):
        assert fetch(client) == []
