import logging
import random
from typing import Dict, List

import httpx

from ..config import YOUTUBE_API_KEY

logger = logging.getLogger(__name__)

LANGUAGE_SEARCH_QUERIES = {
    "es": ["aprende español shorts", "español para principiantes", "habla español", "spanish learning shorts"],
    "en": ["learn english shorts", "english phrases daily", "english conversation shorts"],
    "fr": ["apprendre français shorts", "français quotidien", "parler français"],
    "de": ["deutsch lernen shorts", "deutsch für anfänger", "german learning shorts"],
    "ja": ["日本語 shorts", "japanese learning shorts", "nihongo shorts"],
    "pt": ["aprender português shorts", "português do dia shorts"],
}

LEVEL_FILTERS = {
    "A1": ["beginner", "débutant", "principiante", "anfänger"],
    "A2": ["elementary", "básico", "grundlagen"],
    "B1": ["intermediate", "intermedio", "mittelstufe"],
    "B2": ["upper intermediate", "avanzado"],
    "C1": ["advanced", "avanzado", "fortgeschritten"],
    "C2": ["proficiency", "fluent", "nativo"],
}


async def search_youtube_shorts(
    language: str,
    level: str = "A1",
    max_results: int = 10,
    exclude_ids: List[str] = [],
    tags: List[str] = [],
) -> List[Dict]:
    """Search YouTube for language learning Shorts."""
    if not YOUTUBE_API_KEY:
        logger.error("YOUTUBE_API_KEY not set")
        return []

    queries = LANGUAGE_SEARCH_QUERIES.get(language.lower(), [f"learn {language} shorts"])
    query = random.choice(queries)

    level_hints = LEVEL_FILTERS.get(level.upper(), [])
    if tags:
        query += f" {random.choice(tags)}"
    elif level_hints:
        query += f" {random.choice(level_hints)}"

    params = {
        "part": "snippet",
        "q": query,
        "type": "video",
        "videoDuration": "short",
        "maxResults": max_results + len(exclude_ids),
        "key": YOUTUBE_API_KEY,
        "relevanceLanguage": language.lower(),
        "order": "relevance",
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.get(
                "https://www.googleapis.com/youtube/v3/search",
                params=params,
            )
            resp.raise_for_status()
            data = resp.json()
    except Exception as e:
        logger.error(f"YouTube API error: {e}")
        return []

    results = []
    for item in data.get("items", []):
        video_id = item.get("id", {}).get("videoId")
        if not video_id or video_id in exclude_ids:
            continue
        snippet = item.get("snippet", {})
        results.append({
            "youtube_id": video_id,
            "title": snippet.get("title", ""),
            "channel_name": snippet.get("channelTitle", ""),
            "thumbnail_url": snippet.get("thumbnails", {}).get("high", {}).get("url"),
            "language": language.lower(),
            "level": level,
            "topic": "language_learning",
            "tags": None,
        })
        if len(results) >= max_results:
            break

    video_ids = [r["youtube_id"] for r in results]
    tags_map = await fetch_video_tags(video_ids)
    for r in results:
        if r["youtube_id"] in tags_map:
            r["tags"] = tags_map[r["youtube_id"]]

    logger.info(f"Found {len(results)} YouTube Shorts for {language}/{level}")
    return results


async def fetch_video_tags(video_ids: List[str]) -> Dict[str, str]:
    """Fetch tags for video IDs via YouTube videos.list API."""
    if not video_ids or not YOUTUBE_API_KEY:
        return {}

    params = {
        "part": "snippet",
        "id": ",".join(video_ids),
        "key": YOUTUBE_API_KEY,
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(
                "https://www.googleapis.com/youtube/v3/videos",
                params=params,
            )
            resp.raise_for_status()
            data = resp.json()
    except Exception as e:
        logger.error(f"YouTube videos.list error: {e}")
        return {}

    tags_map = {}
    for item in data.get("items", []):
        vid_id = item.get("id")
        tags = item.get("snippet", {}).get("tags", [])
        if vid_id and tags:
            tags_map[vid_id] = ",".join(tags[:10])

    return tags_map
