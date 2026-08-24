from __future__ import annotations
from datetime import datetime
from typing import Any, Dict, List, Literal, Optional
from urllib.parse import urlparse
from uuid import UUID
from pydantic import BaseModel, Field, HttpUrl, field_validator

YOUTUBE_HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"}


# ── Reel ────────────────────────────────────────────────────────────────────

class ReelImportRequest(BaseModel):
    youtube_url: str
    language: str
    level: Optional[str] = None
    topic: Optional[str] = None
    tags: Optional[str] = None

    @field_validator("youtube_url")
    @classmethod
    def validate_youtube_url(cls, v: str) -> str:
        host = (urlparse(v).hostname or "").lower()
        if host not in YOUTUBE_HOSTS:
            raise ValueError("youtube_url must be a youtube.com or youtu.be URL")
        return v


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
    owner_user_id: Optional[str] = None
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
    term: str = Field(..., max_length=200)
    language: str = Field(..., max_length=10)
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


EVENT_BATCH_MAX_SIZE = 50

EventType = Literal[
    "reel_impression",
    "watch_progress",
    "reel_completed",
    "like",
    "unlike",
    "save",
    "unsave",
    "skip",
    "replay",
    "share",
]

Platform = Literal["android", "web"]


class EventEnvelope(BaseModel):
    event_id: UUID
    event_type: EventType
    user_id: str
    reel_id: str
    session_id: UUID
    platform: Platform
    client_timestamp: datetime
    payload: Dict[str, Any] = Field(default_factory=dict)


class EventBatch(BaseModel):
    events: List[EventEnvelope] = Field(..., min_length=1, max_length=EVENT_BATCH_MAX_SIZE)


class EventBatchResponse(BaseModel):
    accepted: int


class SchemaColumnResponse(BaseModel):
    name: str
    type: str
    nullable: bool
    primary_key: bool


class SchemaForeignKeyResponse(BaseModel):
    column: str
    references_table: str
    references_column: str


class SchemaTableResponse(BaseModel):
    name: str
    columns: List[SchemaColumnResponse]
    foreign_keys: List[SchemaForeignKeyResponse]


class SchemaResponse(BaseModel):
    tables: List[SchemaTableResponse]


DeploymentPlatform = Literal["railway", "vercel"]
DeploymentState = Literal["success", "building", "failed", "unknown"]


class DeploymentStatus(BaseModel):
    platform: DeploymentPlatform
    status: DeploymentState
    raw_status: Optional[str] = None
    deployed_at: Optional[datetime] = None
    commit_sha: Optional[str] = None
    url: Optional[str] = None
    error: Optional[str] = None


class DeploymentsResponse(BaseModel):
    deployments: List[DeploymentStatus]
