import httpx
import pytest


@pytest.fixture
def mock_httpx_client(monkeypatch):
    routes = []

    def register(method: str, url_substring: str, response):
        routes.append((method.upper(), url_substring, response))

    def handler(request: httpx.Request) -> httpx.Response:
        for method, url_substring, response in routes:
            if request.method == method and url_substring in str(request.url):
                if callable(response):
                    return response(request)
                return response
        return httpx.Response(404, json={"error": f"no mock registered for {request.method} {request.url}"})

    class MockAsyncClient(httpx.AsyncClient):
        def __init__(self, *args, **kwargs):
            kwargs["transport"] = httpx.MockTransport(handler)
            super().__init__(*args, **kwargs)

    monkeypatch.setattr(httpx, "AsyncClient", MockAsyncClient)
    return register
