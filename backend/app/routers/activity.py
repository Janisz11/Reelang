import uuid
from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import ActivityLog

router = APIRouter(prefix="/activity", tags=["activity"])


class ActivityUpdateRequest(BaseModel):
    watch_time_ms: int = 0
    reels_watched: int = 0
    words_saved: int = 0


@router.post("/log")
def log_activity(
    payload: ActivityUpdateRequest,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    today = date.today()
    log = (
        db.query(ActivityLog)
        .filter(ActivityLog.user_id == user_id, ActivityLog.date == today)
        .first()
    )
    if log:
        log.watch_time_ms += payload.watch_time_ms
        log.reels_watched += payload.reels_watched
        log.words_saved += payload.words_saved
    else:
        log = ActivityLog(
            id=str(uuid.uuid4()),
            user_id=user_id,
            date=today,
            watch_time_ms=payload.watch_time_ms,
            reels_watched=payload.reels_watched,
            words_saved=payload.words_saved,
        )
        db.add(log)
    db.commit()
    return {"ok": True}
