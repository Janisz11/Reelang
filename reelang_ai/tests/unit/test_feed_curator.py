import json

import httpx

from reelang_ai.agents.feed_curator import import_reel_to_backend


class TestImportReelToBackend:
    async def test_returns_reel_id_on_success(self, mock_httpx_client):
        mock_httpx_client(
            "POST", "/api/v1/reels/import",
            httpx.Response(201, json={"reel_id": "reel-123"}),
        )

        reel_data = {
            "youtube_id": "abc123",
            "language": "es",
            "level": "A1",
            "topic": "language_learning",
            "tags": "spanish,beginner",
        }

        result = await import_reel_to_backend(reel_data)

        assert result == "reel-123"

    async def test_returns_none_on_error_status(self, mock_httpx_client):
        mock_httpx_client("POST", "/api/v1/reels/import", httpx.Response(500))

        reel_data = {"youtube_id": "abc123", "language": "es", "level": "A1"}

        result = await import_reel_to_backend(reel_data)

        assert result is None

    async def test_returns_none_on_connection_error(self, mock_httpx_client):
        def raise_connect_error(request):
            raise httpx.ConnectError("connection refused")

        mock_httpx_client("POST", "/api/v1/reels/import", raise_connect_error)

        reel_data = {"youtube_id": "abc123", "language": "es", "level": "A1"}

        result = await import_reel_to_backend(reel_data)

        assert result is None

    async def test_sends_expected_payload(self, mock_httpx_client):
        captured = {}

        def handler(request):
            captured["body"] = json.loads(request.content)
            return httpx.Response(201, json={"reel_id": "reel-1"})

        mock_httpx_client("POST", "/api/v1/reels/import", handler)

        reel_data = {
            "youtube_id": "xyz789",
            "language": "fr",
            "level": "B1",
            "topic": "travel",
            "tags": "food,travel",
        }

        await import_reel_to_backend(reel_data)

        body = captured["body"]
        assert body["youtube_url"] == "https://www.youtube.com/watch?v=xyz789"
        assert body["language"] == "fr"
        assert body["level"] == "B1"
        assert body["topic"] == "travel"
        assert body["tags"] == "food,travel"

    async def test_defaults_topic_and_tags_when_missing(self, mock_httpx_client):
        captured = {}

        def handler(request):
            captured["body"] = json.loads(request.content)
            return httpx.Response(201, json={"reel_id": "reel-1"})

        mock_httpx_client("POST", "/api/v1/reels/import", handler)

        await import_reel_to_backend({"youtube_id": "id1", "language": "en", "level": "A2"})

        assert captured["body"]["topic"] == "language_learning"
        assert captured["body"]["tags"] is None
