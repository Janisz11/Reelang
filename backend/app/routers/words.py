from __future__ import annotations

from datetime import datetime, timedelta
from typing import List

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Word
from ..schemas import WordCreateRequest, WordResponse
from ..services.dictionary_service import fetch_definition

router = APIRouter(prefix="/words", tags=["words"])


class ReviewRequest(BaseModel):
    quality: int  # 0-5: 0-2=fail, 3-5=pass


def sm2_update(
    repetitions: int, easiness: float, interval_days: int, quality: int
) -> tuple[int, float, int]:
    if quality >= 3:
        if repetitions == 0:
            new_interval = 1
        elif repetitions == 1:
            new_interval = 6
        else:
            new_interval = round(interval_days * easiness)
        new_repetitions = repetitions + 1
    else:
        new_interval = 1
        new_repetitions = 0

    new_easiness = easiness + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    new_easiness = max(1.3, new_easiness)

    return new_repetitions, new_easiness, new_interval


# ── POST /words ───────────────────────────────────────────────────────────────

@router.post("", response_model=WordResponse, status_code=201)
def add_word(
    payload: WordCreateRequest,
    db: Session = Depends(get_db),
    x_user_id: str = Header(...),
):
    existing = (
        db.query(Word)
        .filter(
            Word.user_id == x_user_id,
            Word.term == payload.term,
            Word.language == payload.language,
        )
        .first()
    )
    if existing:
        return existing

    definition = fetch_definition(payload.term, payload.language)

    word = Word(
        user_id=x_user_id,
        term=payload.term,
        definition=definition,
        language=payload.language,
        reel_id=payload.reel_id,
        segment_id=payload.segment_id,
    )
    db.add(word)
    db.commit()
    db.refresh(word)
    return word


# ── GET /words/lookup ─────────────────────────────────────────────────────────

@router.get("/lookup")
def lookup_word(
    term: str = Query(...),
    language: str = Query(...),
    target_lang: str = Query("en"),
    db: Session = Depends(get_db),
):
    import re
    from ..services.dictionary_service import fetch_definition
    from ..services.translation_service import translate_text

    clean_term = re.sub(r'^[¿¡\W]+', '', term.strip())

    definition = fetch_definition(clean_term, language)

    translation = None
    if language.lower() != target_lang.lower():
        translation = translate_text(clean_term, language.lower(), target_lang.lower())

    return {
        "term": term,
        "clean_term": clean_term,
        "language": language,
        "definition": definition,
        "translation": translation,
        "target_lang": target_lang,
    }


# ── GET /words ────────────────────────────────────────────────────────────────

@router.get("", response_model=List[WordResponse])
def list_words(
    due_only: bool = Query(False),
    db: Session = Depends(get_db),
    x_user_id: str = Header(...),
):
    q = db.query(Word).filter(Word.user_id == x_user_id)
    if due_only:
        q = q.filter(
            (Word.next_review == None) |  # noqa: E711
            (Word.next_review <= datetime.utcnow())
        )
    return q.order_by(
        Word.next_review.asc().nullsfirst(),
        Word.created_at.desc(),
    ).all()


# ── GET /words/{word_id} ──────────────────────────────────────────────────────

@router.get("/{word_id}", response_model=WordResponse)
def get_word(
    word_id: str,
    db: Session = Depends(get_db),
    x_user_id: str = Header(...),
):
    word = (
        db.query(Word)
        .filter(Word.id == word_id, Word.user_id == x_user_id)
        .first()
    )
    if not word:
        raise HTTPException(status_code=404, detail="Word not found")
    return word


# ── POST /words/{word_id}/review ──────────────────────────────────────────────

@router.post("/{word_id}/review", response_model=WordResponse)
def review_word(
    word_id: str,
    payload: ReviewRequest,
    db: Session = Depends(get_db),
    x_user_id: str = Header(...),
):
    word = (
        db.query(Word)
        .filter(Word.id == word_id, Word.user_id == x_user_id)
        .first()
    )
    if not word:
        raise HTTPException(status_code=404, detail="Word not found")

    new_reps, new_ease, new_interval = sm2_update(
        word.repetitions,
        word.easiness,
        word.interval_days,
        payload.quality,
    )

    word.repetitions = new_reps
    word.easiness = new_ease
    word.interval_days = new_interval
    word.next_review = datetime.utcnow() + timedelta(days=new_interval)
    word.status = "mastered" if new_reps >= 3 else "learning"

    db.commit()
    db.refresh(word)
    return word
