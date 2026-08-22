import asyncio
import json
import os
import socket
import uuid
from datetime import datetime, timezone
from urllib.parse import urlparse

import aio_pika
import pytest

from app.dependencies import get_current_user_id
from app.main import app
from app.routers.events import EVENT_BATCH_RATE_LIMIT
from app.services.event_publisher import EVENTS_EXCHANGE, get_publisher

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


def make_event(user_id: str, **overrides) -> dict:
    event = {
        "event_id": str(uuid.uuid4()),
        "event_type": "watch_progress",
        "user_id": user_id,
        "reel_id": "reel-1",
        "session_id": str(uuid.uuid4()),
        "platform": "android",
        "client_timestamp": datetime.now(timezone.utc).isoformat(),
        "payload": {"watch_percent": 55},
    }
    event.update(overrides)
    return event


def auth(user_id: str) -> dict:
    return {"Authorization": f"Bearer {user_id}"}


def new_user() -> str:
    return f"events-user-{uuid.uuid4()}"


class RecordingPublisher:
    def __init__(self, error=None):
        self.batches = []
        self.error = error

    async def publish_events(self, events):
        if self.error:
            raise self.error
        events = list(events)
        self.batches.append(events)
        return len(events)


@pytest.fixture
def publisher(client):
    fake = RecordingPublisher()
    app.dependency_overrides[get_publisher] = lambda: fake
    yield fake
    app.dependency_overrides.pop(get_publisher, None)


class TestIngestEvents:
    def test_accepts_batch_and_returns_202(self, client, publisher):
        user = new_user()

        response = client.post(
            "/api/v1/events",
            headers=auth(user),
            json={"events": [make_event(user), make_event(user)]},
        )

        assert response.status_code == 202
        assert response.json() == {"accepted": 2}

    def test_publishes_every_event_in_the_batch(self, client, publisher):
        user = new_user()
        events = [make_event(user, event_type="like"), make_event(user, event_type="skip")]

        client.post("/api/v1/events", headers=auth(user), json={"events": events})

        published = publisher.batches[0]
        assert [e.event_type for e in published] == ["like", "skip"]
        assert [str(e.event_id) for e in published] == [e["event_id"] for e in events]

    def test_requires_authentication(self, client, publisher):
        override = app.dependency_overrides.pop(get_current_user_id)
        try:
            response = client.post(
                "/api/v1/events", json={"events": [make_event("someone")]}
            )
        finally:
            app.dependency_overrides[get_current_user_id] = override

        assert response.status_code == 401
        assert publisher.batches == []

    def test_rejects_event_for_a_different_user(self, client, publisher):
        user = new_user()

        response = client.post(
            "/api/v1/events",
            headers=auth(user),
            json={"events": [make_event("somebody-else")]},
        )

        assert response.status_code == 403
        assert publisher.batches == []

    def test_rejects_batch_over_fifty_events(self, client, publisher):
        user = new_user()

        response = client.post(
            "/api/v1/events",
            headers=auth(user),
            json={"events": [make_event(user) for _ in range(51)]},
        )

        assert response.status_code == 422
        assert publisher.batches == []

    def test_accepts_batch_of_exactly_fifty_events(self, client, publisher):
        user = new_user()

        response = client.post(
            "/api/v1/events",
            headers=auth(user),
            json={"events": [make_event(user) for _ in range(50)]},
        )

        assert response.status_code == 202
        assert response.json() == {"accepted": 50}

    def test_rejects_empty_batch(self, client, publisher):
        user = new_user()

        response = client.post("/api/v1/events", headers=auth(user), json={"events": []})

        assert response.status_code == 422
        assert publisher.batches == []

    def test_rejects_unknown_event_type(self, client, publisher):
        user = new_user()

        response = client.post(
            "/api/v1/events",
            headers=auth(user),
            json={"events": [make_event(user, event_type="teleport")]},
        )

        assert response.status_code == 422
        assert publisher.batches == []

    def test_returns_503_when_broker_publish_fails(self, client):
        user = new_user()
        failing = RecordingPublisher(error=RuntimeError("broker down"))
        app.dependency_overrides[get_publisher] = lambda: failing
        try:
            response = client.post(
                "/api/v1/events", headers=auth(user), json={"events": [make_event(user)]}
            )
        finally:
            app.dependency_overrides.pop(get_publisher, None)

        assert response.status_code == 503


class TestRateLimiting:
    def test_blocks_user_after_configured_number_of_batches(self, client, publisher):
        user = new_user()
        allowed = int(EVENT_BATCH_RATE_LIMIT.split("/")[0])

        statuses = [
            client.post(
                "/api/v1/events", headers=auth(user), json={"events": [make_event(user)]}
            ).status_code
            for _ in range(allowed + 1)
        ]

        assert statuses[:allowed] == [202] * allowed
        assert statuses[-1] == 429

    def test_limit_is_per_user_not_global(self, client, publisher):
        noisy = new_user()
        quiet = new_user()
        allowed = int(EVENT_BATCH_RATE_LIMIT.split("/")[0])

        for _ in range(allowed + 1):
            client.post(
                "/api/v1/events", headers=auth(noisy), json={"events": [make_event(noisy)]}
            )

        response = client.post(
            "/api/v1/events", headers=auth(quiet), json={"events": [make_event(quiet)]}
        )

        assert response.status_code == 202


@requires_broker
class TestPublishesToRealBroker:
    def test_event_reaches_the_events_exchange(self, client):
        user = new_user()
        queue_name = f"test.events.{uuid.uuid4().hex}"
        event = make_event(user, event_type="reel_completed")

        state = {}

        async def declare():
            connection = await aio_pika.connect_robust(RABBITMQ_URL)
            channel = await connection.channel()
            exchange = await channel.declare_exchange(
                EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True
            )
            queue = await channel.declare_queue(queue_name, durable=False, auto_delete=True)
            await queue.bind(exchange, routing_key="event.#")
            state["connection"] = connection
            state["queue"] = queue

        async def drain():
            for _ in range(20):
                message = await state["queue"].get(fail=False, timeout=5)
                if message is not None:
                    async with message.process():
                        return json.loads(message.body), message.routing_key
                await asyncio.sleep(0.1)
            return None, None

        loop = asyncio.new_event_loop()
        try:
            loop.run_until_complete(declare())

            response = client.post(
                "/api/v1/events", headers=auth(user), json={"events": [event]}
            )
            assert response.status_code == 202

            body, routing_key = loop.run_until_complete(drain())
        finally:
            loop.run_until_complete(state["connection"].close())
            loop.close()

        assert body is not None, "event never arrived on the events exchange"
        assert routing_key == "event.reel_completed"
        assert body["event_id"] == event["event_id"]
        assert body["user_id"] == user
        assert body["reel_id"] == event["reel_id"]
