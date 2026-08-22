import uuid
from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.schemas import EVENT_BATCH_MAX_SIZE, EventBatch, EventEnvelope


def make_event(**overrides) -> dict:
    event = {
        "event_id": str(uuid.uuid4()),
        "event_type": "watch_progress",
        "user_id": "user-1",
        "reel_id": "reel-1",
        "session_id": str(uuid.uuid4()),
        "platform": "android",
        "client_timestamp": datetime.now(timezone.utc).isoformat(),
        "payload": {"watch_percent": 42.5},
    }
    event.update(overrides)
    return event


class TestEventEnvelope:
    def test_accepts_valid_event(self):
        envelope = EventEnvelope(**make_event())

        assert envelope.event_type == "watch_progress"
        assert envelope.platform == "android"
        assert envelope.payload["watch_percent"] == 42.5

    def test_payload_defaults_to_empty_dict(self):
        event = make_event()
        del event["payload"]

        assert EventEnvelope(**event).payload == {}

    @pytest.mark.parametrize(
        "event_type",
        [
            "reel_impression",
            "watch_progress",
            "reel_completed",
            "like",
            "unlike",
            "save",
            "unsave",
            "skip",
            "replay",
            "share",
        ],
    )
    def test_accepts_every_supported_event_type(self, event_type):
        assert EventEnvelope(**make_event(event_type=event_type)).event_type == event_type

    def test_rejects_unknown_event_type(self):
        with pytest.raises(ValidationError):
            EventEnvelope(**make_event(event_type="video_exploded"))

    def test_rejects_unknown_platform(self):
        with pytest.raises(ValidationError):
            EventEnvelope(**make_event(platform="ios"))

    def test_rejects_non_uuid_event_id(self):
        with pytest.raises(ValidationError):
            EventEnvelope(**make_event(event_id="not-a-uuid"))

    def test_rejects_non_uuid_session_id(self):
        with pytest.raises(ValidationError):
            EventEnvelope(**make_event(session_id="nope"))

    def test_rejects_missing_reel_id(self):
        event = make_event()
        del event["reel_id"]

        with pytest.raises(ValidationError):
            EventEnvelope(**event)

    def test_rejects_unparseable_timestamp(self):
        with pytest.raises(ValidationError):
            EventEnvelope(**make_event(client_timestamp="yesterday"))


class TestEventBatch:
    def test_accepts_batch_at_max_size(self):
        batch = EventBatch(events=[make_event() for _ in range(EVENT_BATCH_MAX_SIZE)])

        assert len(batch.events) == EVENT_BATCH_MAX_SIZE

    def test_rejects_batch_over_max_size(self):
        with pytest.raises(ValidationError):
            EventBatch(events=[make_event() for _ in range(EVENT_BATCH_MAX_SIZE + 1)])

    def test_rejects_empty_batch(self):
        with pytest.raises(ValidationError):
            EventBatch(events=[])

    def test_rejects_batch_containing_one_invalid_event(self):
        events = [make_event(), make_event(event_type="bogus"), make_event()]

        with pytest.raises(ValidationError):
            EventBatch(events=events)

    def test_max_size_is_fifty(self):
        assert EVENT_BATCH_MAX_SIZE == 50
