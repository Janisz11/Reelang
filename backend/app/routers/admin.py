import asyncio
import time
from datetime import datetime
from typing import List, Optional, Tuple

from fastapi import APIRouter, Depends, HTTPException, Query, Request, UploadFile
from sqlalchemy.orm import Session

from ..database import get_db
from ..dependencies import verify_admin_token
from ..models import AppLog
from ..rate_limit import limiter
from ..schemas import (
    APP_LOGS_DEFAULT_LIMIT,
    APP_LOGS_MAX_LIMIT,
    AppLogEntry,
    DeploymentsResponse,
    EventStatsResponse,
    EventStatsWindow,
    LogLevel,
    SchemaResponse,
)
from ..services import railway_client, vercel_client
from ..services.event_stats import get_event_stats
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


@router.get("/logs", response_model=List[AppLogEntry])
def get_app_logs(
    level: Optional[LogLevel] = None,
    limit: int = Query(APP_LOGS_DEFAULT_LIMIT, ge=1, le=APP_LOGS_MAX_LIMIT),
    before: Optional[datetime] = None,
    db: Session = Depends(get_db),
    _: None = Depends(verify_admin_token),
):
    query = db.query(AppLog)

    if level is not None:
        query = query.filter(AppLog.level == level)
    if before is not None:
        query = query.filter(AppLog.created_at < before)

    return (
        query.order_by(AppLog.created_at.desc(), AppLog.id.desc()).limit(limit).all()
    )


@router.get("/event-stats", response_model=EventStatsResponse)
def get_admin_event_stats(
    window: EventStatsWindow = "24h",
    db: Session = Depends(get_db),
    _: None = Depends(verify_admin_token),
):
    return get_event_stats(db, window)
