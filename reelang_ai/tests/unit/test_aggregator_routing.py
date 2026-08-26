"""Which tables a given event type touches, checked without a database.

The DB-backed assertions live in tests/integration/test_event_consumer.py; this suite
guards the routing decision itself so it stays covered when Postgres is unavailable.
"""
import uuid
from datetime import datetime, timezone

import pytest

from reelang_ai.events.aggregator import MalformedEvent, persist_event


class FakeSession:
    """Records the SQL persist_event runs instead of executing it."""

    def __init__(self, duplicate: bool = False):
        self.statements: list[str] = []
        self.commits = 0
        self.rollbacks = 0
        self._duplicate = duplicate

    def execute(self, statement, params=None):
        self.statements.append(str(statement))
        return FakeResult(None if self._duplicate else ("event-id",))

    def commit(self):
        self.commits += 1

    def rollback(self):
        self.rollbacks += 1

    def touched(self, table: str) -> bool:
        return any(table in statement for statement in self.statements)


class FakeResult:
    def __init__(self, row):
        self._row = row

    def fetchone(self):
        return self._row

    def scalar(self):
        return self._row


def make_event(**overrides) -> dict:
    event = {
        "event_id": str(uuid.uuid4()),
        "event_type": "reel_impression",
        "user_id": "user-1",
        "reel_id": "reel-1",
        "session_id": str(uuid.uuid4()),
        "platform": "android",
        "client_timestamp": datetime.now(timezone.utc).isoformat(),
        "payload": {},
    }
    event.update(overrides)
    return event


LOAD_TIMING_PAYLOAD = {
    "time_to_first_frame_ms": 640,
    "was_prefetched": False,
    "buffering_ms": 180,
    "network_type": "wifi",
}


class TestReelLoadTiming:
    def test_is_written_to_the_raw_log(self):
        db = FakeSession()

        assert persist_event(
            make_event(event_type="reel_load_timing", payload=LOAD_TIMING_PAYLOAD), db
        ) is True
        assert db.touched("reel_events")
        assert db.commits == 1

    def test_skips_the_interaction_counters(self):
        db = FakeSession()

        persist_event(make_event(event_type="reel_load_timing", payload=LOAD_TIMING_PAYLOAD), db)

        assert not db.touched("reel_stats")
        assert not db.touched("user_reel_stats")

    def test_a_duplicate_is_still_ignored(self):
        db = FakeSession(duplicate=True)

        assert persist_event(make_event(event_type="reel_load_timing"), db) is False

    def test_a_malformed_envelope_is_still_rejected(self):
        event = make_event(event_type="reel_load_timing")
        del event["reel_id"]

        with pytest.raises(MalformedEvent):
            persist_event(event, FakeSession())


class TestInteractionEvents:
    @pytest.mark.parametrize(
        "event_type",
        ["reel_impression", "watch_progress", "reel_completed", "like", "save", "skip", "replay"],
    )
    def test_still_reach_both_aggregate_tables(self, event_type):
        db = FakeSession()

        persist_event(make_event(event_type=event_type), db)

        assert db.touched("reel_events")
        assert db.touched("reel_stats")
        assert db.touched("user_reel_stats")
