from slowapi import Limiter
from slowapi.util import get_remote_address
from starlette.requests import Request

limiter = Limiter(key_func=get_remote_address)


def user_id_key(request: Request) -> str:
    """Rate-limit bucket keyed on the authenticated user, falling back to client IP."""
    return getattr(request.state, "rate_limit_user_id", None) or get_remote_address(request)
