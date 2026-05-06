from __future__ import annotations

from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import CaptionSegment, Reel
from ..schemas import (
    CaptionSegmentResponse,
    ReelDetailResponse,
    ReelImportRequest,
    ReelImportResponse,
    ReelResponse,
)
from ..services import yt_dlp_service, translation_service

router = APIRouter(prefix="/reels", tags=["reels"])


# ── POST /reels/import ────────────────────────────────────────────────────────

@router.post("/import", response_model=ReelImportResponse, status_code=201)
def import_reel(payload: ReelImportRequest, db: Session = Depends(get_db)):
    # 1. Fetch metadata only (no video download)
    try:
        info = yt_dlp_service.fetch_video_info(payload.youtube_url)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"yt-dlp error: {exc}")

    meta = yt_dlp_service.extract_metadata(info)
    youtube_id = meta["youtube_id"]

    # 2. Return existing reel if already imported
    existing = db.query(Reel).filter(Reel.youtube_id == youtube_id).first()
    if existing:
        return ReelImportResponse(reel_id=existing.id, stream_url=None, captions_count=0)

    # 3. Persist Reel
    reel = Reel(
        **meta,
        language=payload.language,
        level=payload.level,
        topic=payload.topic,
    )
    db.add(reel)
    db.commit()
    db.refresh(reel)
    return ReelImportResponse(reel_id=reel.id, stream_url=None, captions_count=0)


# ── GET /reels ─────────────────────────────────────────────────────────────────

@router.get("", response_model=List[ReelResponse])
def list_reels(
    language: Optional[str] = Query(None),
    level: Optional[str] = Query(None),
    topic: Optional[str] = Query(None),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
):
    q = db.query(Reel)
    if language:
        q = q.filter(Reel.language == language)
    if level:
        q = q.filter(Reel.level == level)
    if topic:
        q = q.filter(Reel.topic == topic)
    return q.order_by(Reel.created_at.desc()).offset(offset).limit(limit).all()


# ── GET /reels/{reel_id} ───────────────────────────────────────────────────────

@router.get("/{reel_id}", response_model=ReelDetailResponse)
def get_reel(reel_id: str, db: Session = Depends(get_db)):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    return reel


# ── GET /reels/{reel_id}/stream ───────────────────────────────────────────────

@router.get("/{reel_id}/stream")
def stream_reel(reel_id: str, db: Session = Depends(get_db)):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    video_path = Path(f"/tmp/reels/{reel.youtube_id}.mp4")

    if not video_path.exists():
        try:
            video_path = yt_dlp_service.ensure_video_downloaded(
                reel.youtube_id,
                f"https://www.youtube.com/watch?v={reel.youtube_id}",
            )
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Video download failed: {exc}")

    return FileResponse(
        path=str(video_path),
        media_type="video/mp4",
        headers={"Accept-Ranges": "bytes"},
    )


# ── GET /reels/{reel_id}/captions ─────────────────────────────────────────────

@router.get("/{reel_id}/captions", response_model=List[CaptionSegmentResponse])
def get_captions(
    reel_id: str,
    target_lang: str = Query("en", description="Target language for translation"),
    db: Session = Depends(get_db),
):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    segments = (
        db.query(CaptionSegment)
        .filter(CaptionSegment.reel_id == reel_id)
        .order_by(CaptionSegment.start_ms)
        .all()
    )

    # Translate segments that don't yet have a translation
    untranslated = [s for s in segments if s.translated_text is None]
    if untranslated and reel.language != target_lang:
        seg_dicts = [
            {"original_text": s.original_text, "translated_text": None}
            for s in untranslated
        ]
        translation_service.translate_segments(seg_dicts, reel.language, target_lang)
        for seg, d in zip(untranslated, seg_dicts):
            seg.translated_text = d.get("translated_text")
        db.commit()

    return segments
