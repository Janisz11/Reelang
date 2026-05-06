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


class ReelImportResponse(BaseModel):
    reel_id: str
    stream_url: Optional[str] = None
    captions_count: int


class ReelResponse(BaseModel):
    id: str
    youtube_id: str
    title: str
    channel_name: str
    thumbnail_url: Optional[str]
    duration_ms: int
    language: str
    level: Optional[str]
    topic: Optional[str]
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

    class Config:
        from_attributes = True


# Resolve forward reference
ReelDetailResponse.model_rebuild()
