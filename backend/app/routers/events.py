import logging

from fastapi import APIRouter, Depends, HTTPException, Request, status

from ..dependencies import get_rate_limited_user_id
from ..rate_limit import limiter, user_id_key
from ..schemas import EventBatch, EventBatchResponse
from ..services.event_publisher import EventPublisher, get_publisher

router = APIRouter(prefix="/events", tags=["events"])
logger = logging.getLogger(__name__)

EVENT_BATCH_RATE_LIMIT = "10/minute"


@router.post("", status_code=status.HTTP_202_ACCEPTED, response_model=EventBatchResponse)
@limiter.limit(EVENT_BATCH_RATE_LIMIT, key_func=user_id_key)
async def ingest_events(
    request: Request,
    batch: EventBatch,
    user_id: str = Depends(get_rate_limited_user_id),
    publisher: EventPublisher = Depends(get_publisher),
):
    for event in batch.events:
        if event.user_id != user_id:
            raise HTTPException(
                status_code=403, detail="event.user_id does not match authenticated user"
            )

    try:
        published = await publisher.publish_events(batch.events)
    except Exception as e:
        logger.error(f"Failed to publish event batch for {user_id}: {e}")
        raise HTTPException(status_code=503, detail="Event broker unavailable")

    return EventBatchResponse(accepted=published)
