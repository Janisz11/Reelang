import httpx
import pytest

from reelang_ai.sources import youtube_source
from reelang_ai.sources.youtube_source import fetch_video_tags, search_youtube_shorts

SEARCH_RESPONSE = {
    "items": [
        {
            "id": {"videoId": "abc123"},
            "snippet": {
                "title": "Learn Spanish Fast",
                "channelTitle": "Spanish Channel",
                "thumbnails": {"high": {"url": "http://example.com/thumb1.jpg"}},
            },
        },
        {
            "id": {"videoId": "def456"},
            "snippet": {
                "title": "Spanish Vocabulary",
                "channelTitle": "Another Channel",
                "thumbnails": {"high": {"url": "http://example.com/thumb2.jpg"}},
            },
        },
    ]
}

VIDEOS_RESPONSE = {
    "items": [
        {"id": "abc123", "snippet": {"tags": ["spanish", "beginner", "vocabulary"]}},
        {"id": "def456", "snippet": {"tags": []}},
    ]
}


@pytest.fixture(autouse=True)
def youtube_api_key(monkeypatch):
    monkeypatch.setattr(youtube_source, "YOUTUBE_API_KEY", "fake-key")


class TestSearchYoutubeShorts:
    async def test_returns_empty_list_when_api_key_missing(self, monkeypatch):
        monkeypatch.setattr(youtube_source, "YOUTUBE_API_KEY", None)

        results = await search_youtube_shorts(language="es")

        assert results == []

    async def test_maps_search_results_to_reel_dicts(self, mock_httpx_client):
        mock_httpx_client("GET", "youtube/v3/search", httpx.Response(200, json=SEARCH_RESPONSE))
        mock_httpx_client("GET", "youtube/v3/videos", httpx.Response(200, json=VIDEOS_RESPONSE))

        results = await search_youtube_shorts(language="es", level="A1", max_results=2)

        assert len(results) == 2
        assert results[0]["youtube_id"] == "abc123"
        assert results[0]["language"] == "es"
        assert results[0]["level"] == "A1"
        assert results[0]["tags"] == "spanish,beginner,vocabulary"
        assert results[1]["tags"] is None

    async def test_excludes_already_seen_video_ids(self, mock_httpx_client):
        mock_httpx_client("GET", "youtube/v3/search", httpx.Response(200, json=SEARCH_RESPONSE))
        mock_httpx_client("GET", "youtube/v3/videos", httpx.Response(200, json={"items": []}))

        results = await search_youtube_shorts(language="es", exclude_ids=["abc123"], max_results=5)

        assert len(results) == 1
        assert results[0]["youtube_id"] == "def456"



    async def test_returns_empty_list_on_http_error(self, mock_httpx_client):
        mock_httpx_client("GET", "youtube/v3/search", httpx.Response(500))

        results = await search_youtube_shorts(language="es")

        assert results == []


class TestFetchVideoTags:
    async def test_returns_empty_dict_for_empty_input(self):
        assert await fetch_video_tags([]) == {}

    async def test_maps_video_ids_to_comma_joined_tags(self, mock_httpx_client):
        mock_httpx_client("GET", "youtube/v3/videos", httpx.Response(200, json=VIDEOS_RESPONSE))

        result = await fetch_video_tags(["abc123", "def456"])

        assert result == {"abc123": "spanish,beginner,vocabulary"}

    async def test_returns_empty_dict_on_http_error(self, mock_httpx_client):
        mock_httpx_client("GET", "youtube/v3/videos", httpx.Response(500))

        result = await fetch_video_tags(["abc123"])

        assert result == {}
