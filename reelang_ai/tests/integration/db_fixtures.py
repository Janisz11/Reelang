import uuid
from datetime import datetime

from sqlalchemy import text
from sqlalchemy.orm import Session


def insert_reel(db: Session, **overrides) -> str:
    values = {
        "id": str(uuid.uuid4()),
        "title": "Test Reel",
        "language": "es",
        "level": "A1",
        "tags": None,
        "owner_user_id": None,
        "r2_key": None,
        "youtube_id": None,
        "likes_count": 0,
        "created_at": datetime.utcnow(),
    }
    values.update(overrides)
    db.execute(
        text("""
            INSERT INTO reels
                (id, title, language, level, tags, owner_user_id, r2_key, youtube_id, likes_count, created_at)
            VALUES
                (:id, :title, :language, :level, :tags, :owner_user_id, :r2_key, :youtube_id, :likes_count, :created_at)
        """),
        values,
    )
    db.commit()
    return values["id"]


def insert_profile(db: Session, user_id: str, **overrides) -> None:
    values = {
        "user_id": user_id,
        "username": f"user_{user_id[:8]}",
        "level": 1,
        "created_at": datetime.utcnow(),
    }
    values.update(overrides)
    db.execute(
        text("""
            INSERT INTO profiles (user_id, username, level, created_at)
            VALUES (:user_id, :username, :level, :created_at)
        """),
        values,
    )
    db.commit()


def insert_word(db: Session, user_id: str, language: str, **overrides) -> str:
    values = {
        "id": str(uuid.uuid4()),
        "user_id": user_id,
        "term": "word",
        "language": language,
        "status": "learning",
        "created_at": datetime.utcnow(),
    }
    values.update(overrides)
    db.execute(
        text("""
            INSERT INTO words (id, user_id, term, language, status, created_at)
            VALUES (:id, :user_id, :term, :language, :status, :created_at)
        """),
        values,
    )
    db.commit()
    return values["id"]


def insert_activity_log(db: Session, user_id: str, date, **overrides) -> str:
    values = {
        "id": str(uuid.uuid4()),
        "user_id": user_id,
        "date": date,
        "watch_time_ms": 0,
        "reels_watched": 0,
        "words_saved": 0,
    }
    values.update(overrides)
    db.execute(
        text("""
            INSERT INTO activity_logs (id, user_id, date, watch_time_ms, reels_watched, words_saved)
            VALUES (:id, :user_id, :date, :watch_time_ms, :reels_watched, :words_saved)
        """),
        values,
    )
    db.commit()
    return values["id"]


def insert_queue_entry(db: Session, user_id: str, reel_id: str, **overrides) -> str:
    values = {
        "id": str(uuid.uuid4()),
        "user_id": user_id,
        "reel_id": reel_id,
        "score": 1.0,
        "consumed": False,
        "added_at": datetime.utcnow(),
    }
    values.update(overrides)
    db.execute(
        text("""
            INSERT INTO user_feed_queue (id, user_id, reel_id, score, consumed, added_at)
            VALUES (:id, :user_id, :reel_id, :score, :consumed, :added_at)
        """),
        values,
    )
    db.commit()
    return values["id"]
