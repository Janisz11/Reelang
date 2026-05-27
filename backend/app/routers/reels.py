from __future__ import annotations

import logging
import re
import shutil
import subprocess
import uuid
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import FileResponse, StreamingResponse
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import CaptionSegment, Reel, ReelLike, SavedReel, Profile
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
CHUNK_SIZE = 1024 * 1024  # 1mb

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
   
    try:
        info = yt_dlp_service.fetch_video_info(payload.youtube_url)
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"yt-dlp error: {exc}")

    meta = yt_dlp_service.extract_metadata(info)
    youtube_id = meta["youtube_id"]

   
    existing = db.query(Reel).filter(Reel.youtube_id == youtube_id).first()
    if existing:
        return ReelImportResponse(reel_id=existing.id, stream_url=None, captions_count=0)

    
    reel = Reel(
        **meta,
        language=payload.language.lower(),
        level=payload.level,
        topic=payload.topic,
        tags=payload.tags,
    )
    db.add(reel)
    db.commit()
    db.refresh(reel)

   
    background_tasks.add_task(
        transcribe_and_save_captions,
        reel.id,
        reel.youtube_id,
        reel.language,
        db,
    )

    return ReelImportResponse(reel_id=reel.id, stream_url=None, captions_count=0)


def generate_thumbnail(video_path: str, reel_id: str) -> Optional[str]:
    thumb_dir = Path("/tmp/thumbnails")
    thumb_dir.mkdir(parents=True, exist_ok=True)
    thumb_path = thumb_dir / f"{reel_id}.jpg"
    try:
        subprocess.run(
            ["ffmpeg", "-i", str(video_path), "-ss", "00:00:01", "-vframes", "1", "-q:v", "2", str(thumb_path), "-y"],
            check=True,
            capture_output=True,
            timeout=30,
        )
        if thumb_path.exists():
            return str(thumb_path)
    except Exception as e:
        logger.warning(f"Thumbnail generation failed: {e}")
    return None


# ── POST /reels/upload ────────────────────────────────────────────────────────

@router.post("/upload", response_model=ReelUploadResponse, status_code=201)
async def upload_reel(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    title: str = Form(...),
    language: str = Form(...),
    tags: Optional[str] = Form(None),
    owner_user_id: Optional[str] = Form(None),
    db: Session = Depends(get_db),
):
    if file.content_type and not (
        file.content_type.startswith("video/") or
        file.content_type.startswith("image/")
    ):
        raise HTTPException(status_code=422, detail="Only video or image files are accepted")

    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    reel_id = str(uuid.uuid4())
    content_type = file.content_type or "video/mp4"
    if content_type.startswith("image/jpeg") or content_type.startswith("image/jpg"):
        file_ext = ".jpg"
    elif content_type.startswith("image/png"):
        file_ext = ".png"
    elif content_type.startswith("image/webp"):
        file_ext = ".webp"
    else:
        file_ext = ".mp4"
    dest = UPLOAD_DIR / f"{reel_id}{file_ext}"

    try:
        with dest.open("wb") as out:
            while chunk := await file.read(CHUNK_SIZE):
                out.write(chunk)
    except Exception as exc:
        dest.unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"File save failed: {exc}")

    if file.content_type and file.content_type.startswith("image/"):
        thumb_dir = Path("/tmp/thumbnails")
        thumb_dir.mkdir(parents=True, exist_ok=True)
        thumb_path = thumb_dir / f"{reel_id}.jpg"
        shutil.copy2(str(dest), str(thumb_path))
        thumbnail_url = f"/api/v1/reels/{reel_id}/thumbnail"
    else:
        thumb_path_str = generate_thumbnail(str(dest), reel_id)
        thumbnail_url = f"/api/v1/reels/{reel_id}/thumbnail" if thumb_path_str else None

    reel = Reel(
        id=reel_id,
        title=title,
        language=language.lower(),
        file_path=str(dest),
        tags=tags,
        owner_user_id=owner_user_id,
        thumbnail_url=thumbnail_url,
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
        tags=reel.tags,
    )


# ── GET /reels ─────────────────────────────────────────────────────────────────

@router.get("", response_model=List[ReelResponse])
def list_reels(
    language: Optional[str] = Query(None),
    level: Optional[str] = Query(None),
    topic: Optional[str] = Query(None),
    tags: Optional[str] = Query(None),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user_id: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    from sqlalchemy import or_
    q = db.query(Reel)
    if language:
        q = q.filter(Reel.language == language.lower())
    if level:
        q = q.filter(Reel.level == level)
    if topic:
        q = q.filter(Reel.topic == topic)
    if tags:
        tag_list = [t.strip() for t in tags.split(",")]
        q = q.filter(or_(*[Reel.tags.contains(tag) for tag in tag_list]))
    reels = q.order_by(Reel.created_at.desc()).offset(offset).limit(limit).all()

    if user_id:
        liked_ids = {
            r.reel_id for r in db.query(ReelLike).filter(ReelLike.user_id == user_id).all()
        }
        saved_ids = {
            r.reel_id for r in db.query(SavedReel).filter(SavedReel.user_id == user_id).all()
        }
        result = []
        for reel in reels:
            d = {c.name: getattr(reel, c.name) for c in reel.__table__.columns}
            d["is_liked"] = reel.id in liked_ids
            d["is_saved"] = reel.id in saved_ids
            result.append(d)
        return result

    return reels


# ── GET /reels/saved ──────────────────────────────────────────────────────────

@router.get("/saved", response_model=List[ReelResponse])
def get_saved_reels(user_id: str = Query(...), db: Session = Depends(get_db)):
    from sqlalchemy import text
    rows = db.execute(
        text("""
            SELECT r.* FROM saved_reels s
            JOIN reels r ON r.id = s.reel_id
            WHERE s.user_id = :uid
            ORDER BY s.created_at DESC
        """),
        {"uid": user_id},
    ).fetchall()
    return [dict(r._mapping) | {"is_saved": True} for r in rows]


# ── GET /reels/user/{user_id} ─────────────────────────────────────────────────

@router.get("/user/{user_id}", response_model=List[ReelResponse])
def get_user_reels(user_id: str, db: Session = Depends(get_db)):
    return (
        db.query(Reel)
        .filter(Reel.owner_user_id == user_id)
        .order_by(Reel.created_at.desc())
        .all()
    )


# ── POST /reels/{reel_id}/like ────────────────────────────────────────────────

@router.post("/{reel_id}/like")
def toggle_like(
    reel_id: str,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    existing = db.query(ReelLike).filter(
        ReelLike.user_id == user_id,
        ReelLike.reel_id == reel_id,
    ).first()

    if existing:
        db.delete(existing)
        reel.likes_count = max(0, reel.likes_count - 1)
        if reel.owner_user_id:
            owner = db.query(Profile).filter(Profile.user_id == reel.owner_user_id).first()
            if owner:
                owner.total_likes = max(0, owner.total_likes - 1)
        db.commit()
        return {"liked": False, "likes_count": reel.likes_count}

    db.add(ReelLike(user_id=user_id, reel_id=reel_id, created_at=datetime.utcnow()))
    reel.likes_count += 1
    if reel.owner_user_id:
        owner = db.query(Profile).filter(Profile.user_id == reel.owner_user_id).first()
        if owner:
            owner.total_likes += 1
    db.commit()
    return {"liked": True, "likes_count": reel.likes_count}


# ── POST /reels/{reel_id}/save ────────────────────────────────────────────────

@router.post("/{reel_id}/save")
def toggle_save(
    reel_id: str,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")

    existing = db.query(SavedReel).filter(
        SavedReel.user_id == user_id,
        SavedReel.reel_id == reel_id,
    ).first()

    if existing:
        db.delete(existing)
        db.commit()
        return {"saved": False}

    db.add(SavedReel(user_id=user_id, reel_id=reel_id, created_at=datetime.utcnow()))
    db.commit()
    return {"saved": True}


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


# ── GET /reels/{reel_id}/thumbnail ───────────────────────────────────────────

@router.get("/{reel_id}/thumbnail")
def get_thumbnail(reel_id: str, db: Session = Depends(get_db)):
    reel = db.query(Reel).filter(Reel.id == reel_id).first()
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    thumb_path = Path(f"/tmp/thumbnails/{reel_id}.jpg")
    if not thumb_path.exists():
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    return FileResponse(str(thumb_path), media_type="image/jpeg")


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

    if video_path.suffix.lower() in ['.jpg', '.jpeg', '.png', '.webp']:
        return FileResponse(str(video_path), media_type="image/jpeg")

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
