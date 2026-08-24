import asyncio
from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.routers import admin as admin_router
from app.schemas import DeploymentStatus

ADMIN_HEADERS = {"X-Admin-Token": "deployments-secret"}
COMMIT_SHA = "cbc1e31ec5ce64c5904d355e2225db4ba9243aee"


@pytest.fixture
def client():
    """Shadows the shared fixture: /admin/deployments needs neither the database nor the event publisher."""
    return TestClient(app)


@pytest.fixture(autouse=True)
def _fresh_cache():
    admin_router.clear_deployments_cache()
    yield
    admin_router.clear_deployments_cache()


@pytest.fixture
def admin_token(monkeypatch):
    monkeypatch.setenv("ADMIN_TOKEN", "deployments-secret")


def railway_ok() -> DeploymentStatus:
    return DeploymentStatus(
        platform="railway",
        status="success",
        raw_status="SUCCESS",
        deployed_at=datetime(2026, 8, 23, 15, 19, 24, tzinfo=timezone.utc),
        commit_sha=COMMIT_SHA,
        url="https://reelang-production.up.railway.app",
    )


def vercel_ok() -> DeploymentStatus:
    return DeploymentStatus(
        platform="vercel",
        status="success",
        raw_status="READY",
        deployed_at=datetime(2026, 8, 23, 15, 21, 6, tzinfo=timezone.utc),
        commit_sha=COMMIT_SHA,
        url="https://reelang.vercel.app",
    )


def down(platform: str, error: str) -> DeploymentStatus:
    return DeploymentStatus(platform=platform, status="unknown", error=error)


class Stub:
    """Stands in for a platform client and counts how many times the endpoint calls it."""

    def __init__(self, result: DeploymentStatus):
        self.result = result
        self.calls = 0

    async def __call__(self, *args, **kwargs) -> DeploymentStatus:
        self.calls += 1
        return self.result


def install(monkeypatch, railway: DeploymentStatus, vercel: DeploymentStatus):
    railway_stub, vercel_stub = Stub(railway), Stub(vercel)
    monkeypatch.setattr(admin_router.railway_client, "get_latest_deployment", railway_stub)
    monkeypatch.setattr(admin_router.vercel_client, "get_latest_deployment", vercel_stub)
    return railway_stub, vercel_stub


def fetch(client) -> dict:
    response = client.get("/api/v1/admin/deployments", headers=ADMIN_HEADERS)
    assert response.status_code == 200
    return {d["platform"]: d for d in response.json()["deployments"]}


class TestAuth:
    def test_missing_token_returns_403(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        assert client.get("/api/v1/admin/deployments").status_code == 403

    def test_wrong_token_returns_403(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        response = client.get(
            "/api/v1/admin/deployments", headers={"X-Admin-Token": "not-the-secret"}
        )

        assert response.status_code == 403

    def test_unset_admin_token_still_rejects(self, client, monkeypatch):
        monkeypatch.delenv("ADMIN_TOKEN", raising=False)
        install(monkeypatch, railway_ok(), vercel_ok())

        assert client.get("/api/v1/admin/deployments", headers=ADMIN_HEADERS).status_code == 403

    def test_firebase_user_token_does_not_grant_access(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        response = client.get(
            "/api/v1/admin/deployments", headers={"Authorization": "Bearer some-user"}
        )

        assert response.status_code == 403

    def test_rejected_request_does_not_call_the_platforms(self, client, admin_token, monkeypatch):
        railway, vercel = install(monkeypatch, railway_ok(), vercel_ok())

        client.get("/api/v1/admin/deployments")

        assert (railway.calls, vercel.calls) == (0, 0)


class TestHappyPath:
    def test_both_platforms_are_reported(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        assert set(fetch(client)) == {"railway", "vercel"}

    def test_railway_fields_are_passed_through(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        railway = fetch(client)["railway"]

        assert railway["status"] == "success"
        assert railway["raw_status"] == "SUCCESS"
        assert railway["commit_sha"] == COMMIT_SHA
        assert railway["error"] is None

    def test_deployed_at_is_serialized_as_iso8601(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        deployed_at = fetch(client)["vercel"]["deployed_at"]

        assert datetime.fromisoformat(deployed_at) == datetime(
            2026, 8, 23, 15, 21, 6, tzinfo=timezone.utc
        )


class TestPartialFailure:
    def test_vercel_still_shows_when_railway_is_down(self, client, admin_token, monkeypatch):
        install(monkeypatch, down("railway", "Railway API unreachable"), vercel_ok())

        deployments = fetch(client)

        assert deployments["vercel"]["status"] == "success"
        assert deployments["railway"]["status"] == "unknown"
        assert deployments["railway"]["error"] == "Railway API unreachable"

    def test_railway_still_shows_when_vercel_is_down(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), down("vercel", "Vercel API unreachable"))

        deployments = fetch(client)

        assert deployments["railway"]["status"] == "success"
        assert deployments["vercel"]["status"] == "unknown"

    def test_both_down_still_returns_200(self, client, admin_token, monkeypatch):
        install(
            monkeypatch,
            down("railway", "Railway is not configured"),
            down("vercel", "Vercel is not configured"),
        )

        deployments = fetch(client)

        assert [d["status"] for d in deployments.values()] == ["unknown", "unknown"]

    def test_unconfigured_platforms_do_not_break_the_endpoint(self, client, admin_token, monkeypatch):
        for name in (
            "RAILWAY_API_TOKEN",
            "RAILWAY_PROJECT_ID",
            "RAILWAY_ENVIRONMENT_ID",
            "RAILWAY_SERVICE_ID",
            "VERCEL_API_TOKEN",
            "VERCEL_PROJECT_ID",
        ):
            monkeypatch.delenv(name, raising=False)

        deployments = fetch(client)

        assert set(deployments) == {"railway", "vercel"}
        assert deployments["railway"]["error"] == "Railway is not configured"
        assert deployments["vercel"]["error"] == "Vercel is not configured"


class TestCaching:
    def test_second_request_is_served_from_cache(self, client, admin_token, monkeypatch):
        railway, vercel = install(monkeypatch, railway_ok(), vercel_ok())

        fetch(client)
        fetch(client)

        assert (railway.calls, vercel.calls) == (1, 1)

    def test_cached_response_is_identical(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        assert fetch(client) == fetch(client)

    def test_expired_cache_refetches(self, client, admin_token, monkeypatch):
        railway, vercel = install(monkeypatch, railway_ok(), vercel_ok())

        fetch(client)
        admin_router.clear_deployments_cache()
        fetch(client)

        assert (railway.calls, vercel.calls) == (2, 2)

    def test_ttl_matches_the_widget_refresh_interval(self):
        assert admin_router.DEPLOYMENTS_CACHE_TTL_SECONDS == 60


class TestConcurrency:
    def test_platforms_are_queried_in_parallel(self, client, admin_token, monkeypatch):
        railway_started = asyncio.Event()
        vercel_started = asyncio.Event()

        async def railway_call(*args, **kwargs):
            railway_started.set()
            await asyncio.wait_for(vercel_started.wait(), timeout=2)
            return railway_ok()

        async def vercel_call(*args, **kwargs):
            vercel_started.set()
            await asyncio.wait_for(railway_started.wait(), timeout=2)
            return vercel_ok()

        monkeypatch.setattr(admin_router.railway_client, "get_latest_deployment", railway_call)
        monkeypatch.setattr(admin_router.vercel_client, "get_latest_deployment", vercel_call)

        assert set(fetch(client)) == {"railway", "vercel"}


class TestResponseShape:
    def test_response_exposes_only_cleaned_status_fields(self, client, admin_token, monkeypatch):
        install(monkeypatch, railway_ok(), vercel_ok())

        body = client.get("/api/v1/admin/deployments", headers=ADMIN_HEADERS).json()

        assert set(body) == {"deployments"}
        for deployment in body["deployments"]:
            assert set(deployment) == {
                "platform",
                "status",
                "raw_status",
                "deployed_at",
                "commit_sha",
                "url",
                "error",
            }

    def test_no_platform_secret_reaches_the_response(self, client, admin_token, monkeypatch):
        monkeypatch.setenv("RAILWAY_API_TOKEN", "railway-secret")
        monkeypatch.setenv("VERCEL_API_TOKEN", "vercel-secret")
        monkeypatch.setenv("RAILWAY_PROJECT_ID", "railway-project")
        monkeypatch.setenv("RAILWAY_ENVIRONMENT_ID", "railway-environment")
        monkeypatch.setenv("RAILWAY_SERVICE_ID", "railway-service")
        monkeypatch.setenv("VERCEL_PROJECT_ID", "vercel-project")
        install(monkeypatch, railway_ok(), vercel_ok())

        raw = client.get("/api/v1/admin/deployments", headers=ADMIN_HEADERS).text

        for secret in (
            "railway-secret",
            "vercel-secret",
            "railway-project",
            "railway-environment",
            "railway-service",
            "vercel-project",
        ):
            assert secret not in raw
