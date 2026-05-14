from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Word
from ..schemas import WordCreateRequest, WordResponse
from ..services.dictionary_service import fetch_definition

router = APIRouter(prefix="/words", tags=["words"])

HARDCODED_USER_ID = "default_user"


# ── POST /words ───────────────────────────────────────────────────────────────

@router.post("", response_model=WordResponse, status_code=201)
def add_word(payload: WordCreateRequest, db: Session = Depends(get_db)):
    # Check for duplicate per user + term + language
    existing = (
        db.query(Word)
        .filter(
            Word.user_id == HARDCODED_USER_ID,
            Word.term == payload.term,
            Word.language == payload.language,
        )
        .first()
    )
    if existing:
        return existing

    definition = fetch_definition(payload.term, payload.language)

    word = Word(
        user_id=HARDCODED_USER_ID,
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


# ── GET /words ────────────────────────────────────────────────────────────────

@router.get("", response_model=List[WordResponse])
def list_words(db: Session = Depends(get_db)):
    return (
        db.query(Word)
        .filter(Word.user_id == HARDCODED_USER_ID)
        .order_by(Word.created_at.desc())
        .all()
    )
