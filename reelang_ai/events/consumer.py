import asyncio
import json
import logging

import aio_pika
import uvicorn
from fastapi import FastAPI
from sqlalchemy import text

from ..config import EVENTS_HEALTH_PORT, EVENTS_PREFETCH_COUNT, RABBITMQ_PRIVATE_URL
from ..database import SessionLocal
from .aggregator import MalformedEvent, persist_event

logger = logging.getLogger(__name__)

EVENTS_EXCHANGE = "reelang.events"
DEAD_LETTER_EXCHANGE = "reelang.events.dlx"
PERSIST_QUEUE = "events.persist"
DEAD_QUEUE = "events.dead"
EVENTS_BINDING_KEY = "event.#"

MAX_ATTEMPTS = 3
RETRY_BACKOFF_SECONDS = 0.5


class ConsumerState:
    def __init__(self):
        self.connection = None

    @property
    def broker_connected(self) -> bool:
        return self.connection is not None and not self.connection.is_closed


state = ConsumerState()


async def declare_topology(channel):
    """Declare the events exchange, the persist queue and its dead-letter path."""
    events_exchange = await channel.declare_exchange(
        EVENTS_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True
    )
    dlx = await channel.declare_exchange(
        DEAD_LETTER_EXCHANGE, aio_pika.ExchangeType.TOPIC, durable=True
    )

    dead_queue = await channel.declare_queue(DEAD_QUEUE, durable=True)
    await dead_queue.bind(dlx, routing_key="#")

    persist_queue = await channel.declare_queue(
        PERSIST_QUEUE,
        durable=True,
        arguments={"x-dead-letter-exchange": DEAD_LETTER_EXCHANGE},
    )
    await persist_queue.bind(events_exchange, routing_key=EVENTS_BINDING_KEY)

    return persist_queue


def _persist_in_session(event: dict, session_factory) -> bool:
    db = session_factory()
    try:
        return persist_event(event, db)
    finally:
        db.close()


def make_message_handler(
    session_factory=SessionLocal,
    max_attempts: int = MAX_ATTEMPTS,
    backoff_seconds: float = RETRY_BACKOFF_SECONDS,
):
    """Build the AMQP callback. Retries transient failures, dead-letters the rest."""

    async def on_message(message: aio_pika.abc.AbstractIncomingMessage) -> None:
        try:
            event = json.loads(message.body)
        except (ValueError, TypeError) as e:
            logger.error(f"Dead-lettering unparseable message: {e}")
            await message.reject(requeue=False)
            return

        for attempt in range(1, max_attempts + 1):
            try:
                await asyncio.to_thread(_persist_in_session, event, session_factory)
                await message.ack()
                return
            except MalformedEvent as e:
                logger.error(f"Dead-lettering malformed event: {e}")
                await message.reject(requeue=False)
                return
            except Exception as e:
                if attempt == max_attempts:
                    logger.error(
                        f"Dead-lettering event after {max_attempts} attempts: {e}"
                    )
                    await message.reject(requeue=False)
                    return
                delay = backoff_seconds * (2 ** (attempt - 1))
                logger.warning(
                    f"Attempt {attempt}/{max_attempts} failed ({e}); retrying in {delay}s"
                )
                await asyncio.sleep(delay)

    return on_message


health_api = FastAPI()


@health_api.get("/health")
async def health():
    try:
        db = SessionLocal()
        try:
            db.execute(text("SELECT 1"))
            postgres_ok = True
        finally:
            db.close()
    except Exception as e:
        logger.warning(f"Health check: postgres unreachable: {e}")
        postgres_ok = False

    rabbitmq_ok = state.broker_connected
    return {
        "status": "ok" if (postgres_ok and rabbitmq_ok) else "degraded",
        "rabbitmq": "up" if rabbitmq_ok else "down",
        "postgres": "up" if postgres_ok else "down",
    }


async def run_consumer() -> None:
    connection = await aio_pika.connect_robust(RABBITMQ_PRIVATE_URL)
    state.connection = connection

    channel = await connection.channel()
    await channel.set_qos(prefetch_count=EVENTS_PREFETCH_COUNT)

    queue = await declare_topology(channel)
    await queue.consume(make_message_handler())

    logger.info(f"Consuming {PERSIST_QUEUE} (binding {EVENTS_BINDING_KEY})")
    await asyncio.Future()


async def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    config = uvicorn.Config(
        health_api, host="0.0.0.0", port=EVENTS_HEALTH_PORT, log_level="warning"
    )
    server = uvicorn.Server(config)

    await asyncio.gather(run_consumer(), server.serve())


if __name__ == "__main__":
    asyncio.run(main())
