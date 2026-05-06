"""
YouTube Data API v3 — video search.
"""
from __future__ import annotations

import os
from typing import Any, Dict, List, Optional

from googleapiclient.discovery import build

_API_KEY = os.getenv("YOUTUBE_API_KEY", "")


def _client():
    if not _API_KEY:
        raise RuntimeError("YOUTUBE_API_KEY is not set")
    return build("youtube", "v3", developerKey=_API_KEY)


def search_videos(
    query: str,
    language: str,
    max_results: int = 10,
    page_token: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Search YouTube for videos matching *query* in *language*.
    Returns a dict with:
      - items: list of simplified video objects
      - next_page_token: str | None
      - total_results: int
    """
    youtube = _client()
    params: Dict[str, Any] = {
        "part": "snippet",
        "q": query,
        "relevanceLanguage": language,
        "type": "video",
        "maxResults": max_results,
        "videoCaption": "closedCaption",  # only videos with captions
    }
    if page_token:
        params["pageToken"] = page_token

    response = youtube.search().list(**params).execute()
    return _normalize_search_response(response)


def get_video_details(video_ids: List[str]) -> List[Dict[str, Any]]:
    """
    Return enriched details (duration, view count, etc.) for a list of video IDs.
    Uses the videos.list endpoint (part=contentDetails,statistics,snippet).
    """
    youtube = _client()
    response = (
        youtube.videos()
        .list(
            part="snippet,contentDetails,statistics",
            id=",".join(video_ids),
        )
        .execute()
    )
    return [_normalize_video(item) for item in response.get("items", [])]


# ── normalizers ───────────────────────────────────────────────────────────────

def _normalize_search_response(response: Dict[str, Any]) -> Dict[str, Any]:
    items = []
    for item in response.get("items", []):
        vid_id = item["id"].get("videoId")
        if not vid_id:
            continue
        snippet = item.get("snippet", {})
        items.append(
            {
                "video_id": vid_id,
                "youtube_url": f"https://www.youtube.com/watch?v={vid_id}",
                "title": snippet.get("title", ""),
                "channel_name": snippet.get("channelTitle", ""),
                "thumbnail_url": (
                    snippet.get("thumbnails", {}).get("high", {}).get("url")
                    or snippet.get("thumbnails", {}).get("default", {}).get("url")
                ),
                "published_at": snippet.get("publishedAt"),
                "description": snippet.get("description", "")[:300],
            }
        )
    return {
        "items": items,
        "next_page_token": response.get("nextPageToken"),
        "total_results": response.get("pageInfo", {}).get("totalResults", 0),
    }


def _normalize_video(item: Dict[str, Any]) -> Dict[str, Any]:
    snippet = item.get("snippet", {})
    stats = item.get("statistics", {})
    content = item.get("contentDetails", {})
    vid_id = item["id"]
    return {
        "video_id": vid_id,
        "youtube_url": f"https://www.youtube.com/watch?v={vid_id}",
        "title": snippet.get("title", ""),
        "channel_name": snippet.get("channelTitle", ""),
        "thumbnail_url": (
            snippet.get("thumbnails", {}).get("high", {}).get("url")
            or snippet.get("thumbnails", {}).get("default", {}).get("url")
        ),
        "duration_iso": content.get("duration"),  # e.g. "PT4M13S"
        "view_count": int(stats.get("viewCount", 0)),
        "like_count": int(stats.get("likeCount", 0)),
    }
