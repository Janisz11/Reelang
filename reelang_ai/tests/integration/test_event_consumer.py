import json
import os
import socket
import uuid
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

import pytest
from sqlalchemy import text
from sqlalchemy.exc import OperationalError

from reelang_ai.events.consumer import make_message_handler

pytestmark = pytest.mark.db

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")


def _broker_reachable() -> bool:
    parsed = urlparse(RABBITMQ_URL)
    sock = socket.socket()
    sock.settimeout(1)
    try:
        return sock.connect_ex((parsed.hostname or "localhost", parsed.port or 5672)) == 0
    finally:
        sock.close()


requires_broker = pytest.mark.skipif(
    not _broker_reachable(), reason="RabbitMQ is not reachable"
)


class FakeMessage:
    """Stands in for an AMQP delivery so the real handler can be driven directly."""

    def __init__(self, body: bytes):
        self.body = body
        self.acked = False
        self.rejected = False
        self.requeued = None

    async def ack(self):
        self.acked = True

    async def reject(self, requeue=True):
        self.rejected = True
        self.requeued = requeue


REEL_TITLE = "Consumer Test Reel"


@pytest.fixture(autouse=True)
def purge_orphaned_reels(session_factory):
    """Clear fixtures stranded by an interrupted run; they leak into other suites."""
    session = session_factory()
    try:
        session.execute(text("DELETE FROM reels WHERE title = :title"), {"title": REEL_TITLE})
        session.commit()
    finally:
        session.close()


@pytest.fixture
def reel_id(session_factory):
    created = str(uuid.uuid4())
    session = session_factory()
    session.execute(
        text("""
            INSERT INTO reels (id, title, language, likes_count, created_at)
            VALUES (:id, :title, 'es', 0, now())
        """),
        {"id": created, "title": REEL_TITLE},
    )
    session.commit()

    session.close()

    yield created

    cleanup = session_factory()
    try:
        cleanup.execute(text("DELETE FROM reels WHERE id = :id"), {"id": created})
        cleanup.commit()
    finally:
        cleanup.close()


@pytest.fixture
def user_id():
    return f"consumer-user-{uuid.uuid4()}"


@pytest.fixture
def handler(session_factory):
    return make_message_handler(session_factory=session_factory, backoff_seconds=0)


def make_event(user_id: str, reel_id: str, **overrides) -> dict:
    event = {
        "event_id": str(uuid.uuid4()),
        "event_type": "reel_impression",
        "user_id": user_id,
        "reel_id": reel_id,
        "session_id": str(uuid.uuid4()),
        "platform": "android",
        "client_timestamp": datetime.now(timezone.utc).isoformat(),
        "payload": {},
    }
    event.update(overrides)
    return event


def message_for(event: dict) -> FakeMessage:
    return FakeMessage(json.dumps(event).encode())


def _read(session_factory, sql, params, scalar=False):
    """Read committed state, leaving no transaction open to block the cleanup DELETE."""
    session = session_factory()
    try:
        result = session.execute(text(sql), params)
        return result.scalar() if scalar else result.fetchone()
    finally:
        session.rollback()
        session.close()


def reel_stats(session_factory, reel_id):
    return _read(
        session_factory,
        "SELECT impressions, completions, skips, likes, saves, avg_watch_percent "
        "FROM reel_stats WHERE reel_id = :rid",
        {"rid": reel_id},
    )


def user_stats(session_factory, user_id, reel_id):
    return _read(
        session_factory,
        "SELECT watch_count, max_watch_percent, liked, saved, last_interaction "
        "FROM user_reel_stats WHERE user_id = :uid AND reel_id = :rid",
        {"uid": user_id, "rid": reel_id},
    )


def raw_event_count(session_factory, reel_id):
    return _read(
        session_factory,
        "SELECT COUNT(*) FROM reel_events WHERE reel_id = :rid",
        {"rid": reel_id},
        scalar=True,
    )


class TestCounters:
    async def test_impression_increments_impressions_and_acks(
        self, handler, session_factory, user_id, reel_id
    ):
        message = message_for(make_event(user_id, reel_id, event_type="reel_impression"))

        await handler(message)

        assert message.acked is True
        assert reel_stats(session_factory, reel_id)[0] == 1

    async def test_skip_increments_skips(self, handler, session_factory, user_id, reel_id):
        await handler(message_for(make_event(user_id, reel_id, event_type="skip")))

        assert reel_stats(session_factory, reel_id)[2] == 1

    async def test_completion_increments_completions(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_completed")))

        assert reel_stats(session_factory, reel_id)[1] == 1

    async def test_like_then_unlike_returns_counter_to_zero(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="like")))
        assert reel_stats(session_factory, reel_id)[3] == 1

        await handler(message_for(make_event(user_id, reel_id, event_type="unlike")))
        assert reel_stats(session_factory, reel_id)[3] == 0

    async def test_save_then_unsave_returns_counter_to_zero(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="save")))
        assert reel_stats(session_factory, reel_id)[4] == 1

        await handler(message_for(make_event(user_id, reel_id, event_type="unsave")))
        assert reel_stats(session_factory, reel_id)[4] == 0

    async def test_counters_never_go_negative(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="unlike")))

        assert reel_stats(session_factory, reel_id)[3] == 0

    async def test_impressions_accumulate_across_users(
        self, handler, session_factory, reel_id
    ):
        for _ in range(3):
            await handler(
                message_for(
                    make_event(f"user-{uuid.uuid4()}", reel_id, event_type="reel_impression")
                )
            )

        assert reel_stats(session_factory, reel_id)[0] == 3


class TestAvgWatchPercent:
    async def test_single_watch_progress_sets_average(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(
            message_for(
                make_event(
                    user_id, reel_id,
                    event_type="watch_progress", payload={"watch_percent": 40},
                )
            )
        )

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(40.0)

    async def test_average_is_mean_of_all_watch_samples(
        self, handler, session_factory, user_id, reel_id
    ):
        for percent in (20, 40, 60):
            await handler(
                message_for(
                    make_event(
                        user_id, reel_id,
                        event_type="watch_progress", payload={"watch_percent": percent},
                    )
                )
            )

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(40.0)

    async def test_completion_without_payload_counts_as_full_watch(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_completed")))

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(100.0)

    async def test_average_mixes_progress_and_completion(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(
            message_for(
                make_event(
                    user_id, reel_id,
                    event_type="watch_progress", payload={"watch_percent": 50},
                )
            )
        )
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_completed")))

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(75.0)

    async def test_out_of_range_percent_is_clamped_before_averaging(
        self, handler, session_factory, user_id, reel_id
    ):
        for percent in (150, 50):
            await handler(
                message_for(
                    make_event(
                        user_id, reel_id,
                        event_type="watch_progress", payload={"watch_percent": percent},
                    )
                )
            )

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(75.0)

    async def test_watch_progress_without_percent_is_excluded_from_average(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(
            message_for(
                make_event(
                    user_id, reel_id,
                    event_type="watch_progress", payload={"watch_percent": 60},
                )
            )
        )
        await handler(
            message_for(make_event(user_id, reel_id, event_type="watch_progress", payload={}))
        )

        assert reel_stats(session_factory, reel_id)[5] == pytest.approx(60.0)

    async def test_non_watch_events_leave_average_null(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="like")))

        assert reel_stats(session_factory, reel_id)[5] is None


class TestUserReelStats:
    async def test_impression_increments_watch_count(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_impression")))

        assert user_stats(session_factory, user_id, reel_id)[0] == 1

    async def test_replay_increments_watch_count(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_impression")))
        await handler(message_for(make_event(user_id, reel_id, event_type="replay")))

        assert user_stats(session_factory, user_id, reel_id)[0] == 2

    async def test_watch_progress_does_not_inflate_watch_count(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="reel_impression")))
        for percent in (10, 20, 30):
            await handler(
                message_for(
                    make_event(
                        user_id, reel_id,
                        event_type="watch_progress", payload={"watch_percent": percent},
                    )
                )
            )

        assert user_stats(session_factory, user_id, reel_id)[0] == 1

    async def test_max_watch_percent_tracks_the_highest_value(
        self, handler, session_factory, user_id, reel_id
    ):
        for percent in (30, 80, 55):
            await handler(
                message_for(
                    make_event(
                        user_id, reel_id,
                        event_type="watch_progress", payload={"watch_percent": percent},
                    )
                )
            )

        assert user_stats(session_factory, user_id, reel_id)[1] == pytest.approx(80.0)

    async def test_lower_watch_percent_does_not_lower_the_maximum(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(
            message_for(
                make_event(
                    user_id, reel_id,
                    event_type="watch_progress", payload={"watch_percent": 90},
                )
            )
        )
        await handler(
            message_for(
                make_event(
                    user_id, reel_id,
                    event_type="watch_progress", payload={"watch_percent": 10},
                )
            )
        )

        assert user_stats(session_factory, user_id, reel_id)[1] == pytest.approx(90.0)

    async def test_like_and_unlike_toggle_the_flag(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="like")))
        assert user_stats(session_factory, user_id, reel_id)[2] is True

        await handler(message_for(make_event(user_id, reel_id, event_type="unlike")))
        assert user_stats(session_factory, user_id, reel_id)[2] is False

    async def test_save_and_unsave_toggle_the_flag(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="save")))
        assert user_stats(session_factory, user_id, reel_id)[3] is True

        await handler(message_for(make_event(user_id, reel_id, event_type="unsave")))
        assert user_stats(session_factory, user_id, reel_id)[3] is False

    async def test_unrelated_event_leaves_flags_untouched(
        self, handler, session_factory, user_id, reel_id
    ):
        await handler(message_for(make_event(user_id, reel_id, event_type="like")))
        await handler(message_for(make_event(user_id, reel_id, event_type="skip")))

        row = user_stats(session_factory, user_id, reel_id)
        assert row[2] is True
        assert row[3] is False

    async def test_last_interaction_uses_client_timestamp(
        self, handler, session_factory, user_id, reel_id
    ):
        stamp = datetime.now(timezone.utc).replace(microsecond=0)
        await handler(
            message_for(
                make_event(user_id, reel_id, client_timestamp=stamp.isoformat())
            )
        )

        assert user_stats(session_factory, user_id, reel_id)[4] == stamp

    async def test_out_of_order_event_does_not_rewind_last_interaction(
        self, handler, session_factory, user_id, reel_id
    ):
        newer = datetime.now(timezone.utc).replace(microsecond=0)
        older = newer - timedelta(hours=1)

        await handler(
            message_for(make_event(user_id, reel_id, client_timestamp=newer.isoformat()))
        )
        await handler(
            message_for(make_event(user_id, reel_id, client_timestamp=older.isoformat()))
        )

        assert user_stats(session_factory, user_id, reel_id)[4] == newer

    async def test_stats_are_scoped_per_user(self, handler, session_factory, reel_id):
        liker = f"user-{uuid.uuid4()}"
        skipper = f"user-{uuid.uuid4()}"

        await handler(message_for(make_event(liker, reel_id, event_type="like")))
        await handler(message_for(make_event(skipper, reel_id, event_type="skip")))

        assert user_stats(session_factory, liker, reel_id)[2] is True
        assert user_stats(session_factory, skipper, reel_id)[2] is False


class TestIdempotency:
    async def test_redelivered_event_is_stored_once(
        self, handler, session_factory, user_id, reel_id
    ):
        event = make_event(user_id, reel_id, event_type="reel_impression")

        await handler(message_for(event))
        await handler(message_for(event))

        assert raw_event_count(session_factory, reel_id) == 1

    async def test_redelivered_event_does_not_double_count(
        self, handler, session_factory, user_id, reel_id
    ):
        event = make_event(user_id, reel_id, event_type="reel_impression")

        await handler(message_for(event))
        await handler(message_for(event))

        assert reel_stats(session_factory, reel_id)[0] == 1
        assert user_stats(session_factory, user_id, reel_id)[0] == 1

    async def test_redelivered_event_is_acked_not_dead_lettered(
        self, handler, session_factory, user_id, reel_id
    ):
        event = make_event(user_id, reel_id)
        await handler(message_for(event))

        redelivery = message_for(event)
        await handler(redelivery)

        assert redelivery.acked is True
        assert redelivery.rejected is False

    async def test_distinct_events_are_all_counted(
        self, handler, session_factory, user_id, reel_id
    ):
        for _ in range(3):
            await handler(
                message_for(make_event(user_id, reel_id, event_type="reel_impression"))
            )

        assert reel_stats(session_factory, reel_id)[0] == 3


class TestDeadLettering:
    async def test_unparseable_body_is_rejected_without_requeue(self, handler):
        message = FakeMessage(b"this is not json")

        await handler(message)

        assert message.rejected is True
        assert message.requeued is False
        assert message.acked is False

    async def test_event_missing_required_field_is_rejected(
        self, handler, user_id, reel_id
    ):
        event = make_event(user_id, reel_id)
        del event["event_type"]

        message = message_for(event)
        await handler(message)

        assert message.rejected is True
        assert message.requeued is False

    async def test_invalid_watch_percent_is_rejected(self, handler, user_id, reel_id):
        message = message_for(
            make_event(
                user_id, reel_id,
                event_type="watch_progress", payload={"watch_percent": "loads"},
            )
        )

        await handler(message)

        assert message.rejected is True
        assert message.requeued is False

    async def test_unknown_reel_is_dead_lettered_after_retries(self, handler, user_id):
        message = message_for(make_event(user_id, f"missing-reel-{uuid.uuid4()}"))

        await handler(message)

        assert message.rejected is True
        assert message.requeued is False
        assert message.acked is False


class TestRetryBehaviour:
    async def test_transient_failure_is_retried_then_dead_lettered(self, user_id, reel_id):
        attempts = []

        def failing_factory():
            attempts.append(1)
            raise OperationalError("SELECT 1", {}, Exception("database is down"))

        handler = make_message_handler(session_factory=failing_factory, backoff_seconds=0)
        message = message_for(make_event(user_id, reel_id))

        await handler(message)

        assert len(attempts) == 3
        assert message.rejected is True
        assert message.requeued is False

    async def test_recovery_on_a_later_attempt_acks_the_message(
        self, session_factory, user_id, reel_id
    ):
        attempts = []

        def flaky_factory():
            attempts.append(1)
            if len(attempts) < 3:
                raise OperationalError("SELECT 1", {}, Exception("database is down"))
            return session_factory()

        handler = make_message_handler(session_factory=flaky_factory, backoff_seconds=0)
        message = message_for(make_event(user_id, reel_id, event_type="reel_impression"))

        await handler(message)

        assert len(attempts) == 3
        assert message.acked is True
        assert message.rejected is False
        assert reel_stats(session_factory, reel_id)[0] == 1

    async def test_malformed_event_is_not_retried(self, session_factory, user_id, reel_id):
        attempts = []

        def counting_factory():
            attempts.append(1)
            return session_factory()

        handler = make_message_handler(session_factory=counting_factory, backoff_seconds=0)
        event = make_event(user_id, reel_id)
        del event["user_id"]

        message = message_for(event)
        await handler(message)

        assert len(attempts) == 1
        assert message.rejected is True
        assert message.requeued is False


@requires_broker
class TestBrokerRoundTrip:
    async def test_event_published_to_exchange_updates_stats(
        self, session_factory, user_id, reel_id
    ):
        import asyncio

        import aio_pika

        from reelang_ai.events.consumer import declare_topology

        event = make_event(user_id, reel_id, event_type="reel_completed")

        connection = await aio_pika.connect_robust(RABBITMQ_URL)
        try:
            channel = await connection.channel()
            queue = await declare_topology(channel)
            await queue.purge()

            consumer_tag = await queue.consume(
                make_message_handler(session_factory=session_factory, backoff_seconds=0)
            )

            exchange = await channel.get_exchange("reelang.events", ensure=False)
            await exchange.publish(
                aio_pika.Message(body=json.dumps(event).encode()),
                routing_key=f"event.{event['event_type']}",
            )

            for _ in range(50):
                await asyncio.sleep(0.1)
                if reel_stats(session_factory, reel_id) is not None:
                    break

            await queue.cancel(consumer_tag)
        finally:
            await connection.close()

        row = reel_stats(session_factory, reel_id)
        assert row is not None, "consumer never persisted the event"
        assert row[1] == 1
        assert user_stats(session_factory, user_id, reel_id)[1] == pytest.approx(100.0)
