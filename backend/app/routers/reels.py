from __future__ import annotations

import logging
import re
import uuid
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, StreamingResponse
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import CaptionSegment, Reel
from ..schemas import (
    CaptionSegmentResponse,
    ReelDetailResponse,
    ReelImportRequest,
    ReelImportResponse,
    ReelResponse,
    ReelUploadResponse,
)
from ..services import yt_dlp_service, translation_service
from ..services.whisper_service import transcribe_audio, transcribe_file

UPLOAD_DIR = Path("/tmp/reels")
CHUNK_SIZE = 1024 * 1024  # 1 MB

router = APIRouter(prefix="/reels", tags=["reels"])
logger = logging.getLogger(__name__)


async def transcribe_and_save_captions(
    reel_id: str,
    youtube_id: str,
    language: str,
    db: Session,
) -> None:
    try:
        segments = await transcribe_audio(youtube_id, language)
        for seg in segments:
            caption = CaptionSegment(
                id=str(uuid.uuid4()),
                reel_id=reel_id,
                start_ms=seg["start_ms"],
                end_ms=seg["end_ms"],
                original_text=seg["original_text"],
                translated_text=seg.get("translated_text"),
            )
            db.add(caption)
        db.commit()
        logger.info(f"Transcribed {len(segments)} segments for reel {reel_id}")
    except Exception as e:
        logger.error(f"Transcription failed for reel {reel_id}: {e}")
        db.rollback()


async def _transcribe_uploaded_reel(
    reel_id: str,
    file_path: str,
    language: str,
    db: Session,
) -> None:
    try:
        segments = await transcribe_file(file_path, language)
        for seg in segments:
            caption = CaptionSegment(
                id=str(uuid.uuid4()),
                reel_id=reel_id,
                start_ms=seg["start_ms"],
                end_ms=seg["end_ms"],
                original_text=seg["original_text"],
                translated_text=seg.get("translated_text"),
            )
            db.add(caption)
        db.commit()
        logger.info(f"Transcribed {len(segments)} segments for uploaded reel {reel_id}")
    except Exception as e:
        logger.error(f"Transcription failed for uploaded reel {reel_id}: {e}")
        db.rollback()


# ── POST /reels/import ────────────────────────────────────────────────────────

@router.post("/import", response_model=ReelImportResponse, status_code=201)
async def import_reel(
    payload: ReelImportRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
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

    # 4. Transcribe in background
    background_tasks.add_task(
        transcribe_and_save_captions,
        reel.id,
        reel.youtube_id,
        reel.language,
        db,
    )

    return ReelImportResponse(reel_id=reel.id, stream_url=None, captions_count=0)


# ── POST /reels/upload ────────────────────────────────────────────────────────

@router.post("/upload", response_model=ReelUploadResponse, status_code=201)
async def upload_reel(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(..., media_type="video/mp4"),
    title: str = Form(...),
    language: str = Form(...),
    db: Session = Depends(get_db),
):
    if file.content_type and not file.content_type.startswith("video/"):
       raise HTTPException(status_code=422, detail="Only video files are accepted")

    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    reel_id = str(uuid.uuid4())
    dest = UPLOAD_DIR / f"{reel_id}.mp4"

    try:
        with dest.open("wb") as out:
            while chunk := await file.read(CHUNK_SIZE):
                out.write(chunk)
    except Exception as exc:
        dest.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"File save failed: {exc}")

    reel = Reel(
        id=reel_id,
        title=title,
        language=language,
        file_path=str(dest),
    )
    db.add(reel)
    db.commit()
    db.refresh(reel)

    background_tasks.add_task(_transcribe_uploaded_reel, reel.id, str(dest), language, db)

    return ReelUploadResponse(
        id=reel.id,
        title=reel.title,
        language=reel.language,
        stream_url=f"/api/v1/reels/{reel.id}/stream",
    )


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


# ── POST /reels/{reel_id}/transcribe ─────────────────────────────────────────

@router.post("/{reel_id}/transcribe")
async def transcribe_reel(reel_id: str, db: Session = Depends(get_db)):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    try:
        segments = await transcribe_audio(reel.youtube_id, reel.language)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Transcription failed: {exc}")

    for seg in segments:
        db.add(
            CaptionSegment(
                id=str(uuid.uuid4()),
                reel_id=reel_id,
                start_ms=seg["start_ms"],
                end_ms=seg["end_ms"],
                original_text=seg["original_text"],
            )
        )
    db.commit()

    return {"captions_count": len(segments)}


# ── GET /reels/{reel_id}/stream ───────────────────────────────────────────────

def _resolve_video_path(reel: Reel) -> Path:
    if reel.file_path:
        return Path(reel.file_path)
    if reel.youtube_id:
        p = Path(f"/tmp/reels/{reel.youtube_id}.mp4")
        if not p.exists():
            p = yt_dlp_service.ensure_video_downloaded(
                reel.youtube_id,
                f"https://www.youtube.com/watch?v={reel.youtube_id}",
            )
        return p
    raise HTTPException(status_code=404, detail="No video source for this reel")


@router.get("/{reel_id}/stream")
def stream_reel(reel_id: str, request: Request, db: Session = Depends(get_db)):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    try:
        video_path = _resolve_video_path(reel)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Video unavailable: {exc}")

    if not video_path.exists():
        raise HTTPException(status_code=404, detail="Video file not found on disk")

    file_size = video_path.stat().st_size
    range_header = request.headers.get("Range")

    if range_header:
        match = re.match(r"bytes=(\d+)-(\d*)", range_header)
        if match:
            start = int(match.group(1))
            end = int(match.group(2)) if match.group(2) else file_size - 1
            end = min(end, file_size - 1)
            if start > end or start >= file_size:
                raise HTTPException(
                    status_code=416,
                    detail="Range Not Satisfiable",
                    headers={"Content-Range": f"bytes */{file_size}"},
                )
            length = end - start + 1

            def iter_range():
                with video_path.open("rb") as f:
                    f.seek(start)
                    remaining = length
                    while remaining > 0:
                        data = f.read(min(CHUNK_SIZE, remaining))
                        if not data:
                            break
                        remaining -= len(data)
                        yield data

            return StreamingResponse(
                iter_range(),
                status_code=206,
                media_type="video/mp4",
                headers={
                    "Content-Range": f"bytes {start}-{end}/{file_size}",
                    "Accept-Ranges": "bytes",
                    "Content-Length": str(length),
                },
            )

    return FileResponse(
        path=str(video_path),
        media_type="video/mp4",
        headers={"Accept-Ranges": "bytes", "Content-Length": str(file_size)},
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
