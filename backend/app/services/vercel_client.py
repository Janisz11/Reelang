from __future__ import annotations

import os
from datetime import datetime, timezone
from typing import Any, Dict, Optional

import httpx

from ..schemas import DeploymentState, DeploymentStatus

VERCEL_API_URL = "https://api.vercel.com/v7/deployments"
REQUEST_TIMEOUT_SECONDS = 5.0

_STATE_BY_READY_STATE: Dict[str, DeploymentState] = {
    "READY": "success",
    "BUILDING": "building",
    "INITIALIZING": "building",
    "QUEUED": "building",
    "ERROR": "failed",
}


def _unknown(error: str) -> DeploymentStatus:
    return DeploymentStatus(platform="vercel", status="unknown", error=error)


def _parse_created(value: Any) -> Optional[datetime]:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return None
    try:
        return datetime.fromtimestamp(value / 1000, tz=timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None


def _commit_sha(meta: Any) -> Optional[str]:
    if not isinstance(meta, dict):
        return None
    sha = meta.get("githubCommitSha")
    return sha if isinstance(sha, str) and sha else None


def _deployment_url(deployment: Dict[str, Any]) -> Optional[str]:
    url = deployment.get("url")
    if not isinstance(url, str) or not url:
        return None
    return url if url.startswith("http") else f"https://{url}"


def _to_status(deployment: Dict[str, Any]) -> DeploymentStatus:
    raw_status = deployment.get("readyState") or deployment.get("state")
    raw_status = raw_status if isinstance(raw_status, str) else None

    return DeploymentStatus(
        platform="vercel",
        status=_STATE_BY_READY_STATE.get(raw_status or "", "unknown"),
        raw_status=raw_status,
        deployed_at=_parse_created(deployment.get("created")),
        commit_sha=_commit_sha(deployment.get("meta")),
        url=_deployment_url(deployment),
    )


async def get_latest_deployment() -> DeploymentStatus:
    """Latest deployment of the configured Vercel project. Never raises: failures become status "unknown"."""
    token = os.getenv("VERCEL_API_TOKEN")
    project_id = os.getenv("VERCEL_PROJECT_ID")
    team_id = os.getenv("VERCEL_TEAM_ID")

    if not token or not project_id:
        return _unknown("Vercel is not configured")

    params = {"projectId": project_id, "limit": 1}
    if team_id:
        params["teamId"] = team_id

    try:
        async with httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS) as client:
            response = await client.get(
                VERCEL_API_URL,
                params=params,
                headers={"Authorization": f"Bearer {token}"},
            )
    except httpx.HTTPError:
        return _unknown("Vercel API unreachable")

    if response.status_code != 200:
        return _unknown(f"Vercel API returned HTTP {response.status_code}")

    try:
        body = response.json()
    except ValueError:
        return _unknown("Vercel API returned a malformed response")

    deployments = body.get("deployments") if isinstance(body, dict) else None
    if not isinstance(deployments, list):
        return _unknown("Vercel API returned an unexpected shape")

    if not deployments:
        return _unknown("No Vercel deployments found")

    if not isinstance(deployments[0], dict):
        return _unknown("Vercel API returned an unexpected shape")

    return _to_status(deployments[0])
