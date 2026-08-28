from __future__ import annotations

import logging
import os
from typing import Iterable, Optional

import aio_pika
from aio_pika.abc import AbstractRobustConnection
from aio_pika.pool import Pool

logger = logging.getLogger(__name__)

EVENTS_EXCHANGE = "reelang.events"
DEFAULT_RABBITMQ_PRIVATE_URL = "amqp://guest:guest@localhost:5672/"
CHANNEL_POOL_SIZE = 4


def _rabbitmq_url() -> str:
    return os.getenv("RABBITMQ_PRIVATE_URL", DEFAULT_RABBITMQ_PRIVATE_URL)


class EventPublisher:
    """Publishes events to the reelang.events topic exchange over a reused connection."""

    def __init__(self, url: Optional[str] = None, pool_size: int = CHANNEL_POOL_SIZE):
        self._url = url
        self._pool_size = pool_size
        self._connection: Optional[AbstractRobustConnection] = None
        self._channel_pool: Optional[Pool] = None

    async def connect(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            return

        self._connection = await aio_pika.connect_robust(self._url or _rabbitmq_url())

        async with self._connection.channel() as channel:
            await channel.declare_exchange(
                EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True
            )

        async def acquire_channel():
            return await self._connection.channel()

        self._channel_pool = Pool(acquire_channel, max_size=self._pool_size)
        logger.info("Event publisher connected to RabbitMQ")

    async def close(self) -> None:
        if self._channel_pool is not None:
            await self._channel_pool.close()
            self._channel_pool = None
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
        self._connection = None

    @property
    def is_connected(self) -> bool:
        return self._connection is not None and not self._connection.is_closed

    async def publish_events(self, events: Iterable) -> int:
        if self._channel_pool is None:
            await self.connect()

        published = 0
        async with self._channel_pool.acquire() as channel:
            exchange = await channel.get_exchange(EVENTS_EXCHANGE, ensure=False)
            for event in events:
                body = event.model_dump_json().encode()
                message = aio_pika.Message(
                    body=body,
                    content_type="application/json",
                    message_id=str(event.event_id),
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                )
                await exchange.publish(message, routing_key=f"event.{event.event_type}")
                published += 1
        return published


_publisher: Optional[EventPublisher] = None


def get_publisher() -> EventPublisher:
    global _publisher
    if _publisher is None:
        _publisher = EventPublisher()
    return _publisher


async def shutdown_publisher() -> None:
    global _publisher
    if _publisher is not None:
        await _publisher.close()
        _publisher = None
