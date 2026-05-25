from __future__ import annotations
from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, HttpUrl


# ── Reel ────────────────────────────────────────────────────────────────────

class ReelImportRequest(BaseModel):
    youtube_url: str
    language: str
    level: Optional[str] = None
    topic: Optional[str] = None
    tags: Optional[str] = None


class ReelImportResponse(BaseModel):
    reel_id: str
    stream_url: Optional[str] = None
    captions_count: int


class ReelUploadResponse(BaseModel):
    id: str
    title: str
    language: str
    stream_url: str
    tags: Optional[str] = None


class ReelResponse(BaseModel):
    id: str
    youtube_id: Optional[str] = None
    title: str
    channel_name: Optional[str] = None
    thumbnail_url: Optional[str]
    duration_ms: Optional[int] = None
    language: str
    level: Optional[str]
    topic: Optional[str]
    tags: Optional[str] = None
    likes_count: int = 0
    is_liked: bool = False
    is_saved: bool = False
    created_at: datetime

    class Config:
        from_attributes = True


class ReelDetailResponse(ReelResponse):
    segments: List[CaptionSegmentResponse] = []

    class Config:
        from_attributes = True


# ── Caption Segments ─────────────────────────────────────────────────────────

class CaptionSegmentResponse(BaseModel):
    id: str
    reel_id: str
    start_ms: int
    end_ms: int
    original_text: str
    translated_text: Optional[str]

    class Config:
        from_attributes = True


# ── Words ────────────────────────────────────────────────────────────────────

class WordCreateRequest(BaseModel):
    term: str
    language: str
    reel_id: Optional[str] = None
    segment_id: Optional[str] = None


class WordResponse(BaseModel):
    id: str
    term: str
    definition: Optional[str]
    language: str
    status: str
    reel_id: Optional[str]
    segment_id: Optional[str]
    created_at: datetime
    repetitions: int = 0
    easiness: float = 2.5
    interval_days: int = 1
    next_review: Optional[datetime] = None

    class Config:
        from_attributes = True



ReelDetailResponse.model_rebuild()
