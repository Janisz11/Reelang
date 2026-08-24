import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.models import Reel, ReelEvent, ReelStats

ADMIN_HEADERS = {"X-Admin-Token": "stats-secret"}


@pytest.fixture
def admin_token(monkeypatch):
    monkeypatch.setenv("ADMIN_TOKEN", "stats-secret")


@pytest.fixture
def make_reel(db):
    def create(title: str = "Reel title") -> str:
        reel = Reel(id=str(uuid.uuid4()), title=title, language="es")
        db.add(reel)
        db.flush()
        return reel.id

    return create


@pytest.fixture
def make_stats(db):
    def create(reel_id: str, impressions: int, completions: int = 0, skips: int = 0):
        db.add(
            ReelStats(
                reel_id=reel_id,
                impressions=impressions,
                completions=completions,
                skips=skips,
            )
        )
        db.flush()

    return create


@pytest.fixture
def make_event(db):
    def create(reel_id: str, event_type: str = "view", hours_ago: float = 1.0,
               platform: str = "android"):
        db.add(
            ReelEvent(
                event_id=uuid.uuid4(),
                event_type=event_type,
                user_id="user-1",
                reel_id=reel_id,
                session_id=uuid.uuid4(),
                platform=platform,
                client_timestamp=datetime.now(timezone.utc) - timedelta(hours=hours_ago),
                server_timestamp=datetime.now(timezone.utc) - timedelta(hours=hours_ago),
            )
        )
        db.flush()

    return create


def fetch(client, **params):
    response = client.get("/api/v1/admin/event-stats", headers=ADMIN_HEADERS, params=params)
    assert response.status_code == 200
    return response.json()


class TestAuth:
    def test_missing_token_returns_403(self, client, admin_token):
        assert client.get("/api/v1/admin/event-stats").status_code == 403

    def test_wrong_token_returns_403(self, client, admin_token):
        response = client.get("/api/v1/admin/event-stats", headers={"X-Admin-Token": "nope"})

        assert response.status_code == 403

    def test_unset_admin_token_still_rejects(self, client, monkeypatch):
        monkeypatch.delenv("ADMIN_TOKEN", raising=False)

        assert client.get("/api/v1/admin/event-stats", headers=ADMIN_HEADERS).status_code == 403

    def test_firebase_user_token_does_not_grant_access(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/event-stats", headers={"Authorization": "Bearer some-user"}
        )

        assert response.status_code == 403


class TestEmptyDataset:
    def test_all_four_sections_are_empty_lists(self, client, admin_token):
        stats = fetch(client)

        assert stats["time_series"] == []
        assert stats["top_completion"] == []
        assert stats["top_skip"] == []
        assert stats["recent_events"] == []

    def test_response_is_still_200_with_no_data(self, client, admin_token):
        response = client.get("/api/v1/admin/event-stats", headers=ADMIN_HEADERS)

        assert response.status_code == 200

    def test_response_exposes_the_documented_fields(self, client, admin_token):
        assert set(fetch(client)) == {
            "window",
            "time_series",
            "top_completion",
            "top_skip",
            "recent_events",
        }


class TestTimeSeries:
    def test_events_are_grouped_by_type(self, client, admin_token, make_reel, make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=1)
        make_event(reel_id, "view", hours_ago=1)
        make_event(reel_id, "like", hours_ago=1)

        counts = {row["event_type"]: row["count"] for row in fetch(client)["time_series"]}

        assert counts == {"view": 2, "like": 1}

    def test_events_outside_the_window_are_excluded(self, client, admin_token, make_reel,
                                                    make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=1)
        make_event(reel_id, "view", hours_ago=48)

        rows = fetch(client, window="24h")["time_series"]

        assert sum(row["count"] for row in rows) == 1

    def test_fourteen_day_window_includes_older_events(self, client, admin_token, make_reel,
                                                       make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=48)

        rows = fetch(client, window="14d")["time_series"]

        assert sum(row["count"] for row in rows) == 1

    def test_separate_hours_produce_separate_buckets(self, client, admin_token, make_reel,
                                                     make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=1)
        make_event(reel_id, "view", hours_ago=5)

        buckets = {row["bucket"] for row in fetch(client, window="24h")["time_series"]}

        assert len(buckets) == 2

    def test_window_is_echoed_back(self, client, admin_token):
        assert fetch(client, window="14d")["window"] == "14d"

    def test_default_window_is_24h(self, client, admin_token):
        assert fetch(client)["window"] == "24h"

    def test_unknown_window_is_rejected(self, client, admin_token):
        response = client.get(
            "/api/v1/admin/event-stats", headers=ADMIN_HEADERS, params={"window": "7d"}
        )

        assert response.status_code == 422


class TestTopCompletion:
    def test_ranks_by_completion_rate(self, client, admin_token, make_reel, make_stats):
        best = make_reel("Best")
        worst = make_reel("Worst")
        make_stats(best, impressions=10, completions=9)
        make_stats(worst, impressions=10, completions=2)

        titles = [row["title"] for row in fetch(client)["top_completion"]]

        assert titles == ["Best", "Worst"]

    def test_rate_is_a_fraction_of_impressions(self, client, admin_token, make_reel, make_stats):
        reel_id = make_reel("Half")
        make_stats(reel_id, impressions=10, completions=5)

        entry = fetch(client)["top_completion"][0]

        assert entry["rate"] == pytest.approx(0.5)
        assert entry["impressions"] == 10
        assert entry["count"] == 5

    def test_reels_without_impressions_are_excluded(self, client, admin_token, make_reel,
                                                    make_stats):
        counted = make_reel("Counted")
        ignored = make_reel("Ignored")
        make_stats(counted, impressions=4, completions=1)
        make_stats(ignored, impressions=0, completions=0)

        titles = [row["title"] for row in fetch(client)["top_completion"]]

        assert titles == ["Counted"]

    def test_zero_impressions_never_causes_a_division_error(self, client, admin_token,
                                                            make_reel, make_stats):
        make_stats(make_reel("Only zero"), impressions=0, completions=0)

        response = client.get("/api/v1/admin/event-stats", headers=ADMIN_HEADERS)

        assert response.status_code == 200
        assert response.json()["top_completion"] == []

    def test_ranking_is_capped_at_ten(self, client, admin_token, make_reel, make_stats):
        for index in range(12):
            make_stats(make_reel(f"Reel {index}"), impressions=10, completions=index)

        assert len(fetch(client)["top_completion"]) == 10

    def test_title_is_included_for_context(self, client, admin_token, make_reel, make_stats):
        make_stats(make_reel("Titled reel"), impressions=3, completions=3)

        assert fetch(client)["top_completion"][0]["title"] == "Titled reel"


class TestTopSkip:
    def test_ranks_by_skip_rate(self, client, admin_token, make_reel, make_stats):
        skipped = make_reel("Skipped")
        watched = make_reel("Watched")
        make_stats(skipped, impressions=10, skips=8)
        make_stats(watched, impressions=10, skips=1)

        titles = [row["title"] for row in fetch(client)["top_skip"]]

        assert titles == ["Skipped", "Watched"]

    def test_skip_rate_uses_the_skips_column(self, client, admin_token, make_reel, make_stats):
        reel_id = make_reel("Quarter")
        make_stats(reel_id, impressions=8, completions=8, skips=2)

        entry = fetch(client)["top_skip"][0]

        assert entry["rate"] == pytest.approx(0.25)
        assert entry["count"] == 2

    def test_reels_without_impressions_are_excluded(self, client, admin_token, make_reel,
                                                    make_stats):
        make_stats(make_reel("No impressions"), impressions=0, skips=0)

        assert fetch(client)["top_skip"] == []


class TestRecentEvents:
    def test_newest_events_come_first(self, client, admin_token, make_reel, make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=3)
        make_event(reel_id, "like", hours_ago=1)

        types = [event["event_type"] for event in fetch(client)["recent_events"]]

        assert types == ["like", "view"]

    def test_feed_is_capped_at_twenty(self, client, admin_token, make_reel, make_event):
        reel_id = make_reel()
        for index in range(25):
            make_event(reel_id, "view", hours_ago=index * 0.01)

        assert len(fetch(client)["recent_events"]) == 20

    def test_event_exposes_the_documented_fields(self, client, admin_token, make_reel,
                                                 make_event):
        make_event(make_reel(), "view", hours_ago=1)

        event = fetch(client)["recent_events"][0]

        assert set(event) == {
            "event_id",
            "event_type",
            "reel_id",
            "platform",
            "server_timestamp",
        }

    def test_recent_events_ignore_the_window(self, client, admin_token, make_reel, make_event):
        reel_id = make_reel()
        make_event(reel_id, "view", hours_ago=200)

        assert len(fetch(client, window="24h")["recent_events"]) == 1
