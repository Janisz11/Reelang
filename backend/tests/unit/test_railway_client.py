from datetime import timezone

import httpx
import pytest

from app.services import railway_client
from tests.unit.fake_http import FakeHttp, FakeResponse

PROJECT_ID = "project-uuid"
ENVIRONMENT_ID = "environment-uuid"
SERVICE_ID = "service-uuid"
COMMIT_SHA = "cbc1e31ec5ce64c5904d355e2225db4ba9243aee"


def node(**overrides):
    base = {
        "id": "deployment-uuid",
        "status": "SUCCESS",
        "createdAt": "2026-08-23T15:19:24.834Z",
        "staticUrl": "reelang-production.up.railway.app",
        "meta": {"commitHash": COMMIT_SHA, "branch": "master"},
    }
    base.update(overrides)
    return base


def graphql_body(*nodes):
    return {"data": {"deployments": {"edges": [{"node": n} for n in nodes]}}}


@pytest.fixture
def configured(monkeypatch):
    monkeypatch.setenv("RAILWAY_API_TOKEN", "railway-secret")
    monkeypatch.setenv("RAILWAY_PROJECT_ID", PROJECT_ID)
    monkeypatch.setenv("RAILWAY_ENVIRONMENT_ID", ENVIRONMENT_ID)
    monkeypatch.setenv("RAILWAY_SERVICE_ID", SERVICE_ID)


def install(monkeypatch, response=None, error=None) -> FakeHttp:
    fake = FakeHttp(response=response, error=error)
    monkeypatch.setattr(railway_client.httpx, "AsyncClient", fake.client_factory)
    return fake


class TestSuccess:
    @pytest.mark.asyncio
    async def test_maps_a_successful_deployment(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        result = await railway_client.get_latest_deployment()

        assert result.platform == "railway"
        assert result.status == "success"
        assert result.raw_status == "SUCCESS"
        assert result.commit_sha == COMMIT_SHA
        assert result.error is None

    @pytest.mark.asyncio
    async def test_parses_iso_timestamp_as_utc(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        deployed_at = (await railway_client.get_latest_deployment()).deployed_at

        assert deployed_at is not None
        assert deployed_at.astimezone(timezone.utc).isoformat() == "2026-08-23T15:19:24.834000+00:00"

    @pytest.mark.asyncio
    async def test_static_url_gets_an_https_scheme(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        result = await railway_client.get_latest_deployment()

        assert result.url == "https://reelang-production.up.railway.app"

    @pytest.mark.asyncio
    async def test_missing_meta_still_yields_a_status(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body(node(meta=None))))

        result = await railway_client.get_latest_deployment()

        assert result.status == "success"
        assert result.commit_sha is None

    @pytest.mark.asyncio
    async def test_missing_static_url_is_tolerated(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body(node(staticUrl=None))))

        assert (await railway_client.get_latest_deployment()).url is None


class TestStatusMapping:
    @pytest.mark.parametrize(
        "raw,expected",
        [
            ("SUCCESS", "success"),
            ("SLEEPING", "success"),
            ("BUILDING", "building"),
            ("DEPLOYING", "building"),
            ("INITIALIZING", "building"),
            ("QUEUED", "building"),
            ("WAITING", "building"),
            ("NEEDS_APPROVAL", "building"),
            ("FAILED", "failed"),
            ("CRASHED", "failed"),
            ("REMOVED", "unknown"),
            ("SKIPPED", "unknown"),
            ("SOMETHING_NEW", "unknown"),
        ],
    )
    @pytest.mark.asyncio
    async def test_raw_status_maps_to_a_display_state(
        self, monkeypatch, configured, raw, expected
    ):
        install(monkeypatch, FakeResponse(payload=graphql_body(node(status=raw))))

        result = await railway_client.get_latest_deployment()

        assert (result.status, result.raw_status) == (expected, raw)


class TestRequestShape:
    @pytest.mark.asyncio
    async def test_posts_to_the_graphql_endpoint_with_the_project_token(
        self, monkeypatch, configured
    ):
        fake = install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        await railway_client.get_latest_deployment()

        call = fake.calls[0]
        assert call["method"] == "POST"
        assert call["url"] == "https://backboard.railway.com/graphql/v2"
        assert call["headers"] == {"Project-Access-Token": "railway-secret"}

    @pytest.mark.asyncio
    async def test_sends_all_three_ids_as_query_variables(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        await railway_client.get_latest_deployment()

        assert fake.calls[0]["json"]["variables"]["input"] == {
            "projectId": PROJECT_ID,
            "environmentId": ENVIRONMENT_ID,
            "serviceId": SERVICE_ID,
        }

    @pytest.mark.asyncio
    async def test_explicit_service_id_overrides_the_environment(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        await railway_client.get_latest_deployment("other-service")

        assert fake.calls[0]["json"]["variables"]["input"]["serviceId"] == "other-service"

    @pytest.mark.asyncio
    async def test_request_is_bounded_by_a_timeout(self, monkeypatch, configured):
        fake = install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        await railway_client.get_latest_deployment()

        assert fake.calls[0]["client_kwargs"]["timeout"] == railway_client.REQUEST_TIMEOUT_SECONDS


class TestMissingConfiguration:
    @pytest.mark.parametrize(
        "missing",
        [
            "RAILWAY_API_TOKEN",
            "RAILWAY_PROJECT_ID",
            "RAILWAY_ENVIRONMENT_ID",
            "RAILWAY_SERVICE_ID",
        ],
    )
    @pytest.mark.asyncio
    async def test_any_missing_variable_yields_unknown_without_calling_the_api(
        self, monkeypatch, configured, missing
    ):
        monkeypatch.delenv(missing, raising=False)
        fake = install(monkeypatch, FakeResponse(payload=graphql_body(node())))

        result = await railway_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "Railway is not configured")
        assert fake.called is False


class TestFailures:
    @pytest.mark.asyncio
    async def test_timeout_yields_unknown_instead_of_raising(self, monkeypatch, configured):
        install(monkeypatch, error=httpx.TimeoutException("timed out"))

        result = await railway_client.get_latest_deployment()

        assert (result.platform, result.status) == ("railway", "unknown")
        assert result.error == "Railway API unreachable"

    @pytest.mark.asyncio
    async def test_connection_error_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, error=httpx.ConnectError("no route"))

        assert (await railway_client.get_latest_deployment()).status == "unknown"

    @pytest.mark.asyncio
    async def test_http_error_status_is_reported(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(status_code=401, payload={}))

        result = await railway_client.get_latest_deployment()

        assert result.error == "Railway API returned HTTP 401"

    @pytest.mark.asyncio
    async def test_graphql_errors_yield_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload={"errors": [{"message": "Not Authorized"}]}))

        result = await railway_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "Railway API rejected the query")

    @pytest.mark.asyncio
    async def test_malformed_json_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(malformed=True))

        assert (await railway_client.get_latest_deployment()).status == "unknown"

    @pytest.mark.asyncio
    async def test_unexpected_shape_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload={"data": {"somethingElse": {}}}))

        result = await railway_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "Railway API returned an unexpected shape")

    @pytest.mark.asyncio
    async def test_no_deployments_yields_unknown(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(payload=graphql_body()))

        result = await railway_client.get_latest_deployment()

        assert (result.status, result.error) == ("unknown", "No Railway deployments found")

    @pytest.mark.asyncio
    async def test_failure_never_leaks_the_token(self, monkeypatch, configured):
        install(monkeypatch, FakeResponse(status_code=403, payload={}))

        result = await railway_client.get_latest_deployment()

        assert "railway-secret" not in result.model_dump_json()
