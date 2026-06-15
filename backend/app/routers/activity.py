import uuid
from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import get_current_user_id
from ..models import ActivityLog, Profile

router = APIRouter(prefix="/activity", tags=["activity"])


class ActivityUpdateRequest(BaseModel):
    watch_time_ms: int = 0
    reels_watched: int = 0
    words_saved: int = 0


@router.post("/log")
def log_activity(
    payload: ActivityUpdateRequest,
    user_id: str = Depends(get_current_user_id),
    db: Session = Depends(get_db),
):
    today = date.today()
    log = (
        db.query(ActivityLog)
        .filter(ActivityLog.user_id == user_id, ActivityLog.date == today)
        .first()
    )

    was_active_today = log is not None and log.reels_watched > 0

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

    db.flush()

    if payload.reels_watched > 0 and not was_active_today:
        profile = db.query(Profile).filter(Profile.user_id == user_id).first()
        if not profile:
            profile = Profile(
                user_id=user_id,
                username=f"user_{user_id[:8]}",
                avatar_initials=user_id[:2].upper(),
                created_at=datetime.utcnow(),
                streak_days=0,
            )
            db.add(profile)
            db.flush()

        yesterday = today - timedelta(days=1)
        yesterday_log = (
            db.query(ActivityLog)
            .filter(ActivityLog.user_id == user_id, ActivityLog.date == yesterday)
            .first()
        )

        if yesterday_log and yesterday_log.reels_watched > 0:
            profile.streak_days += 1
        else:
            profile.streak_days = 1

    db.commit()
    return {"ok": True}
