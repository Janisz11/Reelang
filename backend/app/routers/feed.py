import logging
from typing import List

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Reel, ReelLike, SavedReel
from ..schemas import ReelResponse

router = APIRouter(prefix="/feed", tags=["feed"])
logger = logging.getLogger(__name__)


@router.get("", response_model=List[ReelResponse])
def get_feed(
    user_id: str = Query(...),
    limit: int = Query(10, ge=1, le=20),
    db: Session = Depends(get_db),
):
    rows = db.execute(
        text("""
            SELECT r.* FROM user_feed_queue q
            JOIN reels r ON r.id = q.reel_id
            WHERE q.user_id = :uid AND q.consumed = false
            ORDER BY q.score DESC, q.added_at ASC
            LIMIT :limit
        """),
        {"uid": user_id, "limit": limit},
    ).fetchall()

    if not rows:
        reels = db.query(Reel).order_by(Reel.created_at.desc()).limit(limit).all()
        rows = [r.__dict__ for r in reels]

    liked_ids = {
        r.reel_id for r in db.query(ReelLike).filter(ReelLike.user_id == user_id).all()
    }
    saved_ids = {
        r.reel_id for r in db.query(SavedReel).filter(SavedReel.user_id == user_id).all()
    }

    result = []
    for row in rows:
        d = dict(row._mapping) if hasattr(row, "_mapping") else dict(row)
        d["is_liked"] = d.get("id") in liked_ids
        d["is_saved"] = d.get("id") in saved_ids
        result.append(d)
    return result


@router.post("/consumed/{reel_id}")
def mark_consumed(
    reel_id: str,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
   
    db.execute(
        text("""
            UPDATE user_feed_queue
            SET consumed = true
            WHERE user_id = :uid AND reel_id = :rid
        """),
        {"uid": user_id, "rid": reel_id},
    )
    db.commit()

    remaining = db.execute(
        text("""
            SELECT COUNT(*) FROM user_feed_queue
            WHERE user_id = :uid AND consumed = false
        """),
        {"uid": user_id},
    ).scalar()

    return {"ok": True, "remaining_queue": remaining}


@router.post("/refill")
def trigger_refill(
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    """Check queue size and trigger AI agent refill if needed."""
    import httpx

    remaining = db.execute(
        text(
            "SELECT COUNT(*) FROM user_feed_queue WHERE user_id = :uid AND consumed = false"
        ),
        {"uid": user_id},
    ).scalar()

    if remaining < 5:
        try:
            resp = httpx.post(
                f"http://reelang_ai:8001/trigger/{user_id}",
                timeout=5.0,
            )
            logger.info(f"Agent trigger response: {resp.status_code}")
        except Exception as e:
            logger.warning(f"Agent trigger failed: {e}")

    return {"remaining": remaining, "refill_needed": remaining < 5}
