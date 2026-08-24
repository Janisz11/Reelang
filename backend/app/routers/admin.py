import asyncio
import time
from typing import Optional, Tuple

from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import verify_admin_token
from ..rate_limit import limiter
from ..schemas import DeploymentsResponse, SchemaResponse
from ..services import railway_client, vercel_client
from ..services.schema_introspection import get_schema_snapshot

router = APIRouter(prefix="/admin", tags=["admin"])

_COOKIES_PATH = "/tmp/cookies.txt"
_MAX_COOKIES_SIZE = 1_000_000

DEPLOYMENTS_CACHE_TTL_SECONDS = 60

_deployments_cache: Optional[Tuple[float, DeploymentsResponse]] = None


def clear_deployments_cache() -> None:
    global _deployments_cache
    _deployments_cache = None


@router.post("/cookies")
@limiter.limit("3/minute")
async def upload_cookies(
    request: Request,
    file: UploadFile,
    _: None = Depends(verify_admin_token),
):
    contents = await file.read()
    if len(contents) > _MAX_COOKIES_SIZE:
        raise HTTPException(status_code=413, detail="Cookies file too large")
    with open(_COOKIES_PATH, "wb") as f:
        f.write(contents)
    return {"status": "ok"}


@router.get("/schema", response_model=SchemaResponse)
def get_database_schema(
    db: Session = Depends(get_db),
    _: None = Depends(verify_admin_token),
):
    return get_schema_snapshot(db.get_bind())


@router.get("/deployments", response_model=DeploymentsResponse)
async def get_deployment_status(
    _: None = Depends(verify_admin_token),
):
    global _deployments_cache

    now = time.monotonic()
    if _deployments_cache is not None and now - _deployments_cache[0] < DEPLOYMENTS_CACHE_TTL_SECONDS:
        return _deployments_cache[1]

    railway, vercel = await asyncio.gather(
        railway_client.get_latest_deployment(),
        vercel_client.get_latest_deployment(),
    )

    snapshot = DeploymentsResponse(deployments=[railway, vercel])
    _deployments_cache = (now, snapshot)
    return snapshot
