from datetime import datetime, date, timedelta
from typing import List, Optional

import sqlalchemy as sa
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import func
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import ActivityLog, Follow, Profile, Reel, Word

router = APIRouter(prefix="/profiles", tags=["profiles"])

LANGUAGE_META = {
    "es": {"flag": "🇪🇸", "name": "Spanish"},
    "fr": {"flag": "🇫🇷", "name": "French"},
    "de": {"flag": "🇩🇪", "name": "German"},
    "ja": {"flag": "🇯🇵", "name": "Japanese"},
    "en": {"flag": "🇬🇧", "name": "English"},
    "pt": {"flag": "🇵🇹", "name": "Portuguese"},
    "pl": {"flag": "🇵🇱", "name": "Polish"},
}


class ProfileResponse(BaseModel):
    user_id: str
    username: str
    bio: Optional[str]
    avatar_initials: Optional[str]
    followers_count: int
    following_count: int
    total_likes: int
    level: int
    streak_days: int
    is_following: bool = False

    class Config:
        from_attributes = True


class ProfileUpdateRequest(BaseModel):
    username: Optional[str] = None
    bio: Optional[str] = None
    avatar_initials: Optional[str] = None


class ActivityStatsResponse(BaseModel):
    vocabulary_mastered: int
    streak_days: int
    hours_watched: float
    weekly_activity: List[dict]
    target_languages: List[dict]


def _is_following(db: Session, viewer_id: Optional[str], target_id: str) -> bool:
    if not viewer_id:
        return False
    return (
        db.query(Follow)
        .filter(Follow.follower_id == viewer_id, Follow.following_id == target_id)
        .first()
        is not None
    )


# NOTE: /me/stats must be defined before /{profile_user_id} to avoid route shadowing
@router.get("/me/stats", response_model=ActivityStatsResponse)
def get_my_stats(user_id: str = Query(...), db: Session = Depends(get_db)):
    vocab_count = db.query(Word).filter(Word.user_id == user_id).count()

    total_watch_ms = (
        db.query(func.sum(ActivityLog.watch_time_ms))
        .filter(ActivityLog.user_id == user_id)
        .scalar()
        or 0
    )
    hours_watched = round(total_watch_ms / 3_600_000, 1)

    today = date.today()
    day_data: dict[date, int] = {}
    max_words = 1
    for i in range(6, -1, -1):
        d = today - timedelta(days=i)
        log = (
            db.query(ActivityLog)
            .filter(ActivityLog.user_id == user_id, ActivityLog.date == d)
            .first()
        )
        words = log.words_saved if log else 0
        day_data[d] = words
        if words > max_words:
            max_words = words

    weekly_activity = [
        {"day": (today - timedelta(days=i)).strftime("%a"), "value": day_data[today - timedelta(days=i)] / max_words}
        for i in range(6, -1, -1)
    ]

    lang_counts = (
        db.query(Word.language, func.count(Word.id))
        .filter(Word.user_id == user_id)
        .group_by(Word.language)
        .all()
    )
    total_words = sum(c for _, c in lang_counts) or 1
    target_languages = []
    for lang, count in sorted(lang_counts, key=lambda x: -x[1])[:4]:
        meta = LANGUAGE_META.get(lang.lower(), {"flag": "🌍", "name": lang})
        percent = int(count / total_words * 100)
        target_languages.append({
            "flag": meta["flag"],
            "name": meta["name"],
            "progress": count / total_words,
            "percent": percent,
        })

    profile = db.query(Profile).filter(Profile.user_id == user_id).first()
    streak = profile.streak_days if profile else 0

    return ActivityStatsResponse(
        vocabulary_mastered=vocab_count,
        streak_days=streak,
        hours_watched=hours_watched,
        weekly_activity=weekly_activity,
        target_languages=target_languages,
    )


@router.get("/me", response_model=ProfileResponse)
def get_my_profile(user_id: str = Query(...), db: Session = Depends(get_db)):
    profile = db.query(Profile).filter(Profile.user_id == user_id).first()
    if not profile:
        profile = Profile(
            user_id=user_id,
            username=f"user_{user_id[:8]}",
            avatar_initials=user_id[:2].upper(),
            created_at=datetime.utcnow(),
        )
        db.add(profile)
        db.commit()
        db.refresh(profile)
    return ProfileResponse(**profile.__dict__, is_following=False)


@router.put("/me", response_model=ProfileResponse)
def update_my_profile(
    payload: ProfileUpdateRequest,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    profile = db.query(Profile).filter(Profile.user_id == user_id).first()
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    if payload.username is not None:
        profile.username = payload.username
    if payload.bio is not None:
        profile.bio = payload.bio
    if payload.avatar_initials is not None:
        profile.avatar_initials = payload.avatar_initials
    db.commit()
    db.refresh(profile)
    return ProfileResponse(**profile.__dict__, is_following=False)


@router.get("/search", response_model=List[ProfileResponse])
def search_profiles(
    q: str = Query(..., min_length=1),
    user_id: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    profiles = (
        db.query(Profile).filter(Profile.username.ilike(f"%{q}%")).limit(20).all()
    )
    return [
        ProfileResponse(**p.__dict__, is_following=_is_following(db, user_id, p.user_id))
        for p in profiles
    ]


@router.get("/{profile_user_id}", response_model=ProfileResponse)
def get_profile(
    profile_user_id: str,
    user_id: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    profile = db.query(Profile).filter(Profile.user_id == profile_user_id).first()
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    return ProfileResponse(
        **profile.__dict__,
        is_following=_is_following(db, user_id, profile_user_id),
    )


@router.post("/{profile_user_id}/follow")
def follow_user(
    profile_user_id: str,
    user_id: str = Query(...),
    db: Session = Depends(get_db),
):
    if user_id == profile_user_id:
        raise HTTPException(status_code=400, detail="Cannot follow yourself")

    existing = (
        db.query(Follow)
        .filter(Follow.follower_id == user_id, Follow.following_id == profile_user_id)
        .first()
    )
    if existing:
        db.delete(existing)
        db.query(Profile).filter(Profile.user_id == profile_user_id).update(
            {"followers_count": Profile.followers_count - 1}
        )
        db.query(Profile).filter(Profile.user_id == user_id).update(
            {"following_count": Profile.following_count - 1}
        )
        db.commit()
        return {"following": False}

    db.add(Follow(follower_id=user_id, following_id=profile_user_id, created_at=datetime.utcnow()))
    db.query(Profile).filter(Profile.user_id == profile_user_id).update(
        {"followers_count": Profile.followers_count + 1}
    )
    db.query(Profile).filter(Profile.user_id == user_id).update(
        {"following_count": Profile.following_count + 1}
    )
    db.commit()
    return {"following": True}
