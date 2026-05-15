from typing import List

from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Reel
from ..schemas import ReelResponse

router = APIRouter(prefix="/feed", tags=["feed"])


@router.get("", response_model=List[ReelResponse])
def get_feed(
    user_id: str = Query(...),
    limit: int = Query(10, ge=1, le=20),
    db: Session = Depends(get_db),
):
    """Return user's personalized feed queue, falling back to latest reels."""
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
        return db.query(Reel).order_by(Reel.created_at.desc()).limit(limit).all()

    return [dict(r._mapping) for r in rows]


@router.post("/consumed/{reel_id}")
def mark_consumed(
    reel_id: str,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    """Mark a reel as consumed (watched) in the feed queue."""
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
