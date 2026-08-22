import json
import uuid
from datetime import datetime, timezone

import aio_pika
import pytest

from app.schemas import EventEnvelope
from app.services import event_publisher as publisher_module
from app.services.event_publisher import EVENTS_EXCHANGE, EventPublisher, get_publisher


def make_envelope(**overrides) -> EventEnvelope:
    event = {
        "event_id": uuid.uuid4(),
        "event_type": "watch_progress",
        "user_id": "user-1",
        "reel_id": "reel-1",
        "session_id": uuid.uuid4(),
        "platform": "android",
        "client_timestamp": datetime.now(timezone.utc),
        "payload": {"watch_percent": 30},
    }
    event.update(overrides)
    return EventEnvelope(**event)


class FakeExchange:
    def __init__(self):
        self.published = []

    async def publish(self, message, routing_key):
        self.published.append((message, routing_key))


class FakeChannel:
    def __init__(self, broker):
        self.broker = broker
        self.closed = False

    async def declare_exchange(self, name, type_, durable=False):
        self.broker.declared_exchanges.append((name, type_, durable))
        return self.broker.exchange

    async def get_exchange(self, name, ensure=True):
        self.broker.fetched_exchanges.append((name, ensure))
        return self.broker.exchange

    async def close(self):
        self.closed = True

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc_info):
        await self.close()
        return False


class FakeChannelHandle:
    """Mimics aio_pika's channel(), which is both awaitable and an async context manager."""

    def __init__(self, channel):
        self._channel = channel

    def __await__(self):
        async def _resolve():
            return self._channel

        return _resolve().__await__()

    async def __aenter__(self):
        return self._channel

    async def __aexit__(self, *exc_info):
        return False


class FakeConnection:
    def __init__(self, broker):
        self.broker = broker
        self.is_closed = False

    def channel(self):
        channel = FakeChannel(self.broker)
        self.broker.channels.append(channel)
        return FakeChannelHandle(channel)

    async def close(self):
        self.is_closed = True


class FakeBroker:
    def __init__(self):
        self.exchange = FakeExchange()
        self.declared_exchanges = []
        self.fetched_exchanges = []
        self.channels = []
        self.connect_urls = []
        self.connection = None

    async def connect_robust(self, url, *args, **kwargs):
        self.connect_urls.append(url)
        self.connection = FakeConnection(self)
        return self.connection


@pytest.fixture
def broker(monkeypatch):
    """Replace connect_robust on the aio_pika reference the publisher module resolves."""
    fake = FakeBroker()
    monkeypatch.setattr(publisher_module.aio_pika, "connect_robust", fake.connect_robust)
    return fake


@pytest.fixture(autouse=True)
def reset_singleton():
    publisher_module._publisher = None
    yield
    publisher_module._publisher = None


class TestConnect:
    @pytest.mark.asyncio
    async def test_declares_events_exchange_as_durable_topic(self, broker):
        await EventPublisher(url="amqp://test/").connect()

        assert broker.declared_exchanges == [
            (EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, True)
        ]

    @pytest.mark.asyncio
    async def test_uses_configured_url(self, broker):
        await EventPublisher(url="amqp://configured/").connect()

        assert broker.connect_urls == ["amqp://configured/"]

    @pytest.mark.asyncio
    async def test_reads_url_from_environment(self, broker, monkeypatch):
        monkeypatch.setenv("RABBITMQ_URL", "amqp://from-env/")

        await EventPublisher().connect()

        assert broker.connect_urls == ["amqp://from-env/"]

    @pytest.mark.asyncio
    async def test_connecting_twice_reuses_the_connection(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()
        await pub.connect()

        assert len(broker.connect_urls) == 1

    @pytest.mark.asyncio
    async def test_is_connected_reflects_state(self, broker):
        pub = EventPublisher(url="amqp://test/")
        assert pub.is_connected is False

        await pub.connect()
        assert pub.is_connected is True

        await pub.close()
        assert pub.is_connected is False


class TestPublishEvents:
    @pytest.mark.asyncio
    async def test_publishes_one_message_per_event(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()

        published = await pub.publish_events([make_envelope() for _ in range(3)])

        assert published == 3
        assert len(broker.exchange.published) == 3

    @pytest.mark.asyncio
    async def test_routing_key_is_derived_from_event_type(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()

        await pub.publish_events(
            [make_envelope(event_type="like"), make_envelope(event_type="reel_completed")]
        )

        routing_keys = [rk for _, rk in broker.exchange.published]
        assert routing_keys == ["event.like", "event.reel_completed"]

    @pytest.mark.asyncio
    async def test_body_is_the_serialized_envelope(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()
        envelope = make_envelope(payload={"watch_percent": 77})

        await pub.publish_events([envelope])

        message, _ = broker.exchange.published[0]
        body = json.loads(message.body)
        assert body["event_id"] == str(envelope.event_id)
        assert body["reel_id"] == envelope.reel_id
        assert body["payload"] == {"watch_percent": 77}

    @pytest.mark.asyncio
    async def test_messages_are_persistent_and_carry_event_id(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()
        envelope = make_envelope()

        await pub.publish_events([envelope])

        message, _ = broker.exchange.published[0]
        assert message.message_id == str(envelope.event_id)
        assert message.delivery_mode == aio_pika.DeliveryMode.PERSISTENT

    @pytest.mark.asyncio
    async def test_publishing_without_connect_establishes_connection(self, broker):
        pub = EventPublisher(url="amqp://test/")

        await pub.publish_events([make_envelope()])

        assert broker.connect_urls == ["amqp://test/"]
        assert len(broker.exchange.published) == 1

    @pytest.mark.asyncio
    async def test_exchange_is_fetched_without_redeclaring(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()

        await pub.publish_events([make_envelope()])

        assert broker.fetched_exchanges == [(EVENTS_EXCHANGE, False)]
        assert len(broker.declared_exchanges) == 1

    @pytest.mark.asyncio
    async def test_repeated_publishes_reuse_pooled_channel(self, broker):
        pub = EventPublisher(url="amqp://test/", pool_size=1)
        await pub.connect()
        channels_after_connect = len(broker.channels)

        await pub.publish_events([make_envelope()])
        await pub.publish_events([make_envelope()])

        assert len(broker.channels) == channels_after_connect + 1

    @pytest.mark.asyncio
    async def test_empty_iterable_publishes_nothing(self, broker):
        pub = EventPublisher(url="amqp://test/")
        await pub.connect()

        assert await pub.publish_events([]) == 0
        assert broker.exchange.published == []


class TestGetPublisher:
    def test_returns_the_same_instance(self):
        assert get_publisher() is get_publisher()

    @pytest.mark.asyncio
    async def test_shutdown_closes_and_clears_singleton(self, broker):
        pub = get_publisher()
        pub._url = "amqp://test/"
        await pub.connect()

        await publisher_module.shutdown_publisher()

        assert pub.is_connected is False
        assert get_publisher() is not pub
