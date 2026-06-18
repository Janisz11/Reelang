import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.database import get_db
from app.rate_limit import limiter


@pytest.fixture(autouse=True)
def _reset_limiter():
    limiter._storage.reset()
    yield
    limiter._storage.reset()


@pytest.fixture
def security_client(db):
    def override_get_db():
        yield db

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


class TestAdminToken:
    def test_missing_returns_403(self, security_client):
        r = security_client.post(
            "/api/v1/admin/cookies",
            files={"file": ("c.txt", b"x", "text/plain")},
        )
        assert r.status_code == 403

    def test_wrong_token_returns_403(self, security_client, monkeypatch):
        monkeypatch.setenv("ADMIN_TOKEN", "correct")
        r = security_client.post(
            "/api/v1/admin/cookies",
            headers={"X-Admin-Token": "wrong"},
            files={"file": ("c.txt", b"x", "text/plain")},
        )
        assert r.status_code == 403

    def test_correct_token_is_accepted(self, security_client, monkeypatch):
        monkeypatch.setenv("ADMIN_TOKEN", "secret")
        r = security_client.post(
            "/api/v1/admin/cookies",
            headers={"X-Admin-Token": "secret"},
            files={"file": ("c.txt", b"x", "text/plain")},
        )
        assert r.status_code == 200


class TestInternalToken:
    def test_missing_returns_403(self, security_client):
        r = security_client.post(
            "/api/v1/reels/import",
            json={"youtube_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"},
        )
        assert r.status_code == 403

    def test_wrong_token_returns_403(self, security_client, monkeypatch):
        monkeypatch.setenv("INTERNAL_API_TOKEN", "correct")
        r = security_client.post(
            "/api/v1/reels/import",
            headers={"X-Internal-Token": "wrong"},
            json={"youtube_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"},
        )
        assert r.status_code == 403


class TestFirebaseAuth:
    def test_missing_auth_header_returns_401(self, security_client):
        r = security_client.post(
            "/api/v1/reels/upload",
            files={"file": ("v.mp4", b"data", "video/mp4")},
            data={"title": "t", "language": "en"},
        )
        assert r.status_code == 401

    def test_non_bearer_scheme_returns_401(self, security_client):
        r = security_client.post(
            "/api/v1/reels/upload",
            headers={"Authorization": "Basic dXNlcjpwYXNz"},
            files={"file": ("v.mp4", b"data", "video/mp4")},
            data={"title": "t", "language": "en"},
        )
        assert r.status_code == 401

    def test_missing_auth_on_words_endpoint_returns_401(self, security_client):
        r = security_client.post("/api/v1/words", json={"term": "hello", "language": "en"})
        assert r.status_code == 401


class TestRateLimit:
    def test_admin_cookies_blocked_after_3_requests(self, security_client, monkeypatch):
        monkeypatch.setenv("ADMIN_TOKEN", "secret")
        headers = {"X-Admin-Token": "secret"}
        for _ in range(3):
            security_client.post(
                "/api/v1/admin/cookies",
                headers=headers,
                files={"file": ("c.txt", b"x", "text/plain")},
            )
        r = security_client.post(
            "/api/v1/admin/cookies",
            headers=headers,
            files={"file": ("c.txt", b"x", "text/plain")},
        )
        assert r.status_code == 429

    def test_429_response_body_describes_limit(self, security_client, monkeypatch):
        monkeypatch.setenv("ADMIN_TOKEN", "secret")
        headers = {"X-Admin-Token": "secret"}
        for _ in range(3):
            security_client.post(
                "/api/v1/admin/cookies",
                headers=headers,
                files={"file": ("c.txt", b"x", "text/plain")},
            )
        r = security_client.post(
            "/api/v1/admin/cookies",
            headers=headers,
            files={"file": ("c.txt", b"x", "text/plain")},
        )
        assert r.status_code == 429
        assert "3 per 1 minute" in r.text
