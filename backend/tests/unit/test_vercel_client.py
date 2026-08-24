from datetime import timezone

import httpx
import pytest

from app.services import vercel_client
from tests.unit.fake_http import FakeHttp, FakeResponse

PROJECT_ID = "prj_0c5jsWo6EylhjOjkjSARPUST0ZNp"
COMMIT_SHA = "cbc1e31ec5ce64c5904d355e2225db4ba9243aee"
CREATED_MS = 1787498366817


def deployment(**overrides):
    base = {
        "uid": "dpl_4A2TmjARb3hcYtxCjKpLUQaAuhUo",
        "state": "READY",
        "readyState": "READY",
        "created": CREATED_MS,
        "url": "reelang-a8zfl9puu-janisz11s-projects.vercel.app",
        "meta": {"githubCommitSha": COMMIT_SHA, "githubCommitRef": "master"},
    }
    base.update(overrides)
    return base


def body(*deployments):
    return {"deployments": list(deployments)}


@pytest.fixture
def configured(monkeypatch):
    monkeypatch.setenv("VERCEL_API_TOKEN", "vercel-secret")
    monkeypatch.setenv("VERCEL_PROJECT_ID", PROJECT_ID)
    monkeypatch.delenv("VERCEL_TEAM_ID", raising=False)


def install(monkeypatch, response=None, error=None) -> FakeHttp:
    fake = FakeHttp(response=response, error=error)
    monkeypatch.setattr(vercel_client.httpx, "AsyncClient", fake.client_factory)
    return fake


class TestSuccess:
    @pytest.mark.asyncio
    async def test_maps_a_ready_deployment(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment())))

        result = await vercel_client.get_latest_deployment()

        assert result.platform == "vercel"
        assert result.status == "success"
        assert result.raw_status == "READY"
        assert result.commit_sha == COMMIT_SHA
        assert result.error is None

    @pytest.mark.asyncio
    async def test_epoch_milliseconds_become_a_utc_timestamp(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment())))

        deployed_at = (await vercel_client.get_latest_deployment()).deployed_at

        assert deployed_at is not None
        assert deployed_at.tzinfo is not None
        assert int(deployed_at.astimezone(timezone.utc).timestamp() * 1000) == CREATED_MS

    @pytest.mark.asyncio
    async def test_url_gets_an_https_scheme(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment())))

        result = await vercel_client.get_latest_deployment()

        assert result.url == "https://reelang-a8zfl9puu-janisz11s-projects.vercel.app"

    @pytest.mark.asyncio
    async def test_falls_back_to_state_when_ready_state_is_absent(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment(readyState=None))))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.raw_status) == ("success", "READY")

    @pytest.mark.asyncio
    async def test_missing_meta_still_yields_a_status(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment(meta=None))))

        result = await vercel_client.get_latest_deployment()

        assert result.status == "success"
        assert result.commit_sha is None

    @pytest.mark.asyncio
    async def test_missing_created_is_tolerated(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body(deployment(created=None))))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.deployed_at) == ("success", None)


class TestStatusMapping:
    @pytest.mark.parametrize(
        "raw,expected",
        [
            ("READY", "success"),
            ("BUILDING", "building"),
            ("INITIALIZING", "building"),
            ("QUEUED", "building"),
            ("ERROR", "failed"),
            ("CANCELED", "unknown"),
            ("SOMETHING_NEW", "unknown"),
        ],
    )
    @pytest.mark.asyncio
    async def test_ready_state_maps_to_a_display_state(
        self, monkeypatch, configured, raw, expected
    ):
        install(monkeypatch, FakeResponse(payload=body(deployment(readyState=raw, state=raw))))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.raw_status) == (expected, raw)


class TestRequestShape:
    @pytest.mark.asyncio
    async def test_calls_v7_deployments_with_a_bearer_token(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        await vercel_client.get_latest_deployment()

        call = fake.calls[0]
        assert call["method"] == "GET"
        assert call["url"] == "https://api.vercel.com/v7/deployments"
        assert call["headers"] == {"Authorization": "Bearer vercel-secret"}

    @pytest.mark.asyncio
    async def test_asks_for_a_single_deployment_of_the_configured_project(
        self, monkeypatch, configured
    ):
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        await vercel_client.get_latest_deployment()

        assert fake.calls[0]["params"] == {"projectId": PROJECT_ID, "limit": 1}

    @pytest.mark.asyncio
    async def test_team_id_is_omitted_when_unset(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        await vercel_client.get_latest_deployment()

        assert "teamId" not in fake.calls[0]["params"]

    @pytest.mark.asyncio
    async def test_team_id_is_sent_when_set(self, monkeypatch, configured):
        monkeypatch.setenv("VERCEL_TEAM_ID", "team_abc")
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        await vercel_client.get_latest_deployment()

        assert fake.calls[0]["params"]["teamId"] == "team_abc"

    @pytest.mark.asyncio
    async def test_request_is_bounded_by_a_timeout(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        await vercel_client.get_latest_deployment()

        assert fake.calls[0]["client_kwargs"]["timeout"] == vercel_client.REQUEST_TIMEOUT_SECONDS


class TestMissingConfiguration:
    @pytest.mark.parametrize("missing", ["VERCEL_API_TOKEN", "VERCEL_PROJECT_ID"])
    @pytest.mark.asyncio
    async def test_any_missing_variable_yields_unknown_without_calling_the_api(
        self, monkeypatch, configured, missing
    ):
        monkeypatch.delenv(missing, raising=False)
        fake = install(monkeypatch, FakeResponse(payload=body(deployment())))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "Vercel is not configured")
        assert fake.called is False


class TestFailures:
    @pytest.mark.asyncio
    async def test_timeout_yields_unknown_instead_of_raising(self, monkeypatch, configured):
        install(monkeypatch, error=httpx.TimeoutException("timed out"))

        result = await vercel_client.get_latest_deployment()

        assert (result.platform, result.status) == ("vercel", "unknown")
        assert result.error == "Vercel API unreachable"

    @pytest.mark.asyncio
    async def test_connection_error_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, error=httpx.ConnectError("no route"))

        assert (await vercel_client.get_latest_deployment()).status == "unknown"

    @pytest.mark.asyncio
    async def test_http_error_status_is_reported(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(status_code=403, payload={}))

        result = await vercel_client.get_latest_deployment()

        assert result.error == "Vercel API returned HTTP 403"

    @pytest.mark.asyncio
    async def test_malformed_json_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(malformed=True))

        assert (await vercel_client.get_latest_deployment()).status == "unknown"

    @pytest.mark.asyncio
    async def test_unexpected_shape_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload={"error": {"code": "forbidden"}}))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "Vercel API returned an unexpected shape")

    @pytest.mark.asyncio
    async def test_no_deployments_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=body()))

        result = await vercel_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "No Vercel deployments found")

    @pytest.mark.asyncio
    async def test_failure_never_leaks_the_token(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(status_code=401, payload={}))

        result = await vercel_client.get_latest_deployment()

        assert "vercel-secret" not in result.model_dump_json()
