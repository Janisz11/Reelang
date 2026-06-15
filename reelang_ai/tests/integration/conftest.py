import os

import httpx
import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker

TEST_DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL", "postgresql://reelang:reelang@localhost:5432/reelang_test"
)

engine = create_engine(TEST_DATABASE_URL)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@pytest.fixture
def db():
    """A SQLAlchemy session whose changes are rolled back after the test."""
    connection = engine.connect()
    transaction = connection.begin()
    session = TestingSessionLocal(bind=connection)

    nested = connection.begin_nested()

    @event.listens_for(session, "after_transaction_end")
    def restart_savepoint(sess, trans):
        nonlocal nested
        if not nested.is_active:
            nested = connection.begin_nested()

    try:
        yield session
    finally:
        session.close()
        transaction.rollback()
        connection.close()


@pytest.fixture
def mock_httpx_client(monkeypatch):
    """Patch httpx.AsyncClient so outgoing requests are served by registered mocks.

    Usage:
        mock_httpx_client("GET", "youtube/v3/search", httpx.Response(200, json={...}))
        mock_httpx_client("POST", "/api/v1/reels/import", lambda request: httpx.Response(200, json={...}))
    """
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
