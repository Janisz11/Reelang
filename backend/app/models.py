import uuid
from datetime import datetime, date
from sqlalchemy import Column, String, Integer, BigInteger, DateTime, Date, ForeignKey, Text, Enum, Float, Boolean, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import relationship
from .database import Base


def gen_uuid() -> str:
    return str(uuid.uuid4())


class Reel(Base):
    __tablename__ = "reels"

    id = Column(String, primary_key=True, default=gen_uuid)
    youtube_id = Column(String, unique=True, nullable=True, index=True)
    title = Column(String, nullable=False)
    channel_name = Column(String, nullable=True)
    thumbnail_url = Column(String)
    duration_ms = Column(Integer, nullable=True)
    language = Column(String, nullable=False, index=True)
    file_path = Column(String, nullable=True)
    level = Column(String, nullable=True, index=True)
    topic = Column(String, nullable=True, index=True)
    tags = Column(String, nullable=True)
    owner_user_id = Column(String, nullable=True)
    likes_count = Column(Integer, default=0, nullable=False)
    r2_key = Column(String, nullable=True)
    r2_thumb_key = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    segments = relationship("CaptionSegment", back_populates="reel", cascade="all, delete-orphan")
    words = relationship("Word", back_populates="reel")


class CaptionSegment(Base):
    __tablename__ = "caption_segments"

    id = Column(String, primary_key=True, default=gen_uuid)
    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), nullable=False, index=True)
    start_ms = Column(Integer, nullable=False)
    end_ms = Column(Integer, nullable=False)
    original_text = Column(Text, nullable=False)
    translated_text = Column(Text, nullable=True)

    reel = relationship("Reel", back_populates="segments")


class Word(Base):
    __tablename__ = "words"

    id = Column(String, primary_key=True, default=gen_uuid)
    user_id = Column(String, default="default_user", nullable=False, index=True)
    term = Column(String, nullable=False)
    definition = Column(Text, nullable=True)
    language = Column(String, nullable=False)
    status = Column(
    Enum("learning", "mastered", name="word_status", create_type=False),
    default="learning",
    nullable=False
    )
    reel_id = Column(String, ForeignKey("reels.id", ondelete="SET NULL"), nullable=True)
    segment_id = Column(String, ForeignKey("caption_segments.id", ondelete="SET NULL"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    repetitions = Column(Integer, default=0, nullable=False)
    easiness = Column(Float, default=2.5, nullable=False)
    interval_days = Column(Integer, default=1, nullable=False)
    next_review = Column(DateTime, nullable=True)

    reel = relationship("Reel", back_populates="words")


class Profile(Base):
    __tablename__ = "profiles"

    user_id = Column(String, primary_key=True)
    username = Column(String, nullable=False)
    bio = Column(String, nullable=True)
    avatar_initials = Column(String, nullable=True)
    followers_count = Column(Integer, default=0, nullable=False)
    following_count = Column(Integer, default=0, nullable=False)
    total_likes = Column(Integer, default=0, nullable=False)
    level = Column(Integer, default=1, nullable=False)
    streak_days = Column(Integer, default=0, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class Follow(Base):
    __tablename__ = "follows"

    follower_id = Column(String, nullable=False, primary_key=True)
    following_id = Column(String, nullable=False, primary_key=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class ReelLike(Base):
    __tablename__ = "reel_likes"

    user_id = Column(String, nullable=False, primary_key=True)
    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), nullable=False, primary_key=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class SavedReel(Base):
    __tablename__ = "saved_reels"

    user_id = Column(String, nullable=False, primary_key=True)
    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), nullable=False, primary_key=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class ActivityLog(Base):
    __tablename__ = "activity_logs"

    id = Column(String, primary_key=True, default=gen_uuid)
    user_id = Column(String, nullable=False, index=True)
    date = Column(Date, nullable=False)
    watch_time_ms = Column(BigInteger, default=0)
    reels_watched = Column(Integer, default=0)
    words_saved = Column(Integer, default=0)


class ReelEvent(Base):
    __tablename__ = "reel_events"

    event_id = Column(UUID(as_uuid=True), primary_key=True)
    event_type = Column(String(32), nullable=False)
    user_id = Column(String, nullable=False)
    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), nullable=False)
    session_id = Column(UUID(as_uuid=True), nullable=False)
    platform = Column(String(16), nullable=False)
    client_timestamp = Column(DateTime(timezone=True), nullable=False)
    server_timestamp = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    payload = Column(JSONB, nullable=True)


class ReelStats(Base):
    __tablename__ = "reel_stats"

    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), primary_key=True)
    impressions = Column(BigInteger, server_default="0", nullable=False)
    completions = Column(BigInteger, server_default="0", nullable=False)
    skips = Column(BigInteger, server_default="0", nullable=False)
    likes = Column(BigInteger, server_default="0", nullable=False)
    saves = Column(BigInteger, server_default="0", nullable=False)
    avg_watch_percent = Column(Float, nullable=True)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class UserReelStats(Base):
    __tablename__ = "user_reel_stats"

    user_id = Column(String, primary_key=True)
    reel_id = Column(String, ForeignKey("reels.id", ondelete="CASCADE"), primary_key=True)
    watch_count = Column(Integer, server_default="0", nullable=False)
    max_watch_percent = Column(Float, server_default="0", nullable=False)
    liked = Column(Boolean, server_default="false", nullable=False)
    saved = Column(Boolean, server_default="false", nullable=False)
    last_interaction = Column(DateTime(timezone=True), nullable=True)
