from __future__ import annotations

import os
from typing import Optional

from fastapi import Depends, Header, HTTPException, Request

from .services import auth_service


def _verify_bearer_token(authorization: str) -> str:
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization[len("Bearer "):]
    auth_service.initialize_firebase()
    return auth_service.verify_token(token)


def get_current_user_id(authorization: Optional[str] = Header(None)) -> str:
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    return _verify_bearer_token(authorization)


def get_rate_limited_user_id(
    request: Request, user_id: str = Depends(get_current_user_id)
) -> str:
    """Resolve the caller and expose the id to slowapi's per-user key function."""
    request.state.rate_limit_user_id = user_id
    return user_id


def get_optional_user_id(authorization: Optional[str] = Header(None)) -> Optional[str]:
    if not authorization:
        return None
    return _verify_bearer_token(authorization)


def verify_admin_token(x_admin_token: Optional[str] = Header(None)) -> None:
    expected = os.getenv("ADMIN_TOKEN")
    if not expected or x_admin_token != expected:
        raise HTTPException(status_code=403, detail="Invalid admin token")


def verify_internal_token(x_internal_token: Optional[str] = Header(None)) -> None:
    expected = os.getenv("INTERNAL_API_TOKEN")
    if not expected or x_internal_token != expected:
        raise HTTPException(status_code=403, detail="Invalid internal token")
