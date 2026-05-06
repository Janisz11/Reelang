from __future__ import annotations

from typing import Any, Dict, List, Optional

from fastapi import APIRouter, HTTPException, Query

from ..services.youtube_search_service import get_video_details, search_videos

router = APIRouter(prefix="/search", tags=["search"])


@router.get("", response_model=Dict[str, Any])
def search(
    q: str = Query(..., description="Search query"),
    language: str = Query("en", description="Relevance language (ISO 639-1)"),
    max_results: int = Query(10, ge=1, le=50),
    page_token: Optional[str] = Query(None),
):
    """
    Search YouTube for videos with closed captions in the given language.
    Returns simplified video list + next_page_token for pagination.
    """
    try:
        return search_videos(q, language, max_results, page_token)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YouTube API error: {exc}")


@router.get("/details", response_model=List[Dict[str, Any]])
def video_details(
    ids: str = Query(..., description="Comma-separated YouTube video IDs"),
):
    """
    Enrich one or more videos with duration, view count, etc.
    """
    video_ids = [v.strip() for v in ids.split(",") if v.strip()]
    if not video_ids:
        raise HTTPException(status_code=422, detail="No video IDs provided")
    try:
        return get_video_details(video_ids)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc))
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"YouTube API error: {exc}")
