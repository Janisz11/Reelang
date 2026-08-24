"""Minimal stand-in for httpx.AsyncClient so client modules can be tested without network access."""

from __future__ import annotations

from typing import Any, Dict, List, Optional


class FakeResponse:
    def __init__(self, status_code: int = 200, payload: Any = None, malformed: bool = False):
        self.status_code = status_code
        self._payload = payload
        self._malformed = malformed

    def json(self) -> Any:
        if self._malformed:
            raise ValueError("Expecting value")
        return self._payload


class FakeHttp:
    """Records every request and replays a canned response (or raises a canned error)."""

    def __init__(self, response: Optional[FakeResponse] = None, error: Optional[Exception] = None):
        self.response = response
        self.error = error
        self.calls: List[Dict[str, Any]] = []

    @property
    def called(self) -> bool:
        return bool(self.calls)

    def client_factory(self, **client_kwargs: Any) -> Any:
        recorder = self

        class _FakeAsyncClient:
            async def __aenter__(self) -> "_FakeAsyncClient":
                return self

            async def __aexit__(self, *exc_info: Any) -> bool:
                return False

            async def post(self, url: str, **kwargs: Any) -> FakeResponse:
                return recorder._record("POST", url, client_kwargs, kwargs)

            async def get(self, url: str, **kwargs: Any) -> FakeResponse:
                return recorder._record("GET", url, client_kwargs, kwargs)

        return _FakeAsyncClient()

    def _record(
        self, method: str, url: str, client_kwargs: Dict[str, Any], kwargs: Dict[str, Any]
    ) -> FakeResponse:
        self.calls.append({"method": method, "url": url, "client_kwargs": client_kwargs, **kwargs})
        if self.error is not None:
            raise self.error
        assert self.response is not None, "FakeHttp needs either a response or an error"
        return self.response
