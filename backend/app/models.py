import uuid
from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Text, Enum
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

    reel = relationship("Reel", back_populates="words")
