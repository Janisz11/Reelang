from __future__ import annotations

import os
from datetime import datetime
from typing import Any, Dict, Optional

import httpx

from ..schemas import DeploymentState, DeploymentStatus

RAILWAY_API_URL = "https://backboard.railway.com/graphql/v2"
REQUEST_TIMEOUT_SECONDS = 5.0

LATEST_DEPLOYMENT_QUERY = """
query LatestDeployment($input: DeploymentListInput!) {
  deployments(first: 1, input: $input) {
    edges {
      node {
        id
        status
        createdAt
        staticUrl
        meta
      }
    }
  }
}
"""

_STATE_BY_STATUS: Dict[str, DeploymentState] = {
    "SUCCESS": "success",
    "SLEEPING": "success",
    "BUILDING": "building",
    "DEPLOYING": "building",
    "INITIALIZING": "building",
    "QUEUED": "building",
    "WAITING": "building",
    "NEEDS_APPROVAL": "building",
    "CRASHED": "failed",
    "FAILED": "failed",
}


def _unknown(error: str) -> DeploymentStatus:
    return DeploymentStatus(platform="railway", status="unknown", error=error)


def _parse_created_at(value: Any) -> Optional[datetime]:
    if not isinstance(value, str):
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def _commit_sha(meta: Any) -> Optional[str]:
    if not isinstance(meta, dict):
        return None
    sha = meta.get("commitHash")
    return sha if isinstance(sha, str) and sha else None


def _static_url(node: Dict[str, Any]) -> Optional[str]:
    url = node.get("staticUrl")
    if not isinstance(url, str) or not url:
        return None
    return url if url.startswith("http") else f"https://{url}"


def _to_status(node: Dict[str, Any]) -> DeploymentStatus:
    raw_status = node.get("status")
    raw_status = raw_status if isinstance(raw_status, str) else None

    return DeploymentStatus(
        platform="railway",
        status=_STATE_BY_STATUS.get(raw_status or "", "unknown"),
        raw_status=raw_status,
        deployed_at=_parse_created_at(node.get("createdAt")),
        commit_sha=_commit_sha(node.get("meta")),
        url=_static_url(node),
    )


async def get_latest_deployment(service_id: Optional[str] = None) -> DeploymentStatus:
    """Latest deployment of one Railway service. Never raises: failures become status "unknown"."""
    token = os.getenv("RAILWAY_API_TOKEN")
    project_id = os.getenv("RAILWAY_PROJECT_ID")
    environment_id = os.getenv("RAILWAY_ENVIRONMENT_ID")
    service_id = service_id or os.getenv("RAILWAY_SERVICE_ID")

    if not all((token, project_id, environment_id, service_id)):
        return _unknown("Railway is not configured")

    payload = {
        "query": LATEST_DEPLOYMENT_QUERY,
        "variables": {
            "input": {
                "projectId": project_id,
                "environmentId": environment_id,
                "serviceId": service_id,
            }
        },
    }

    try:
        async with httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS) as client:
            response = await client.post(
                RAILWAY_API_URL,
                json=payload,
                headers={"Project-Access-Token": token},
            )
    except httpx.HTTPError:
        return _unknown("Railway API unreachable")

    if response.status_code != 200:
        return _unknown(f"Railway API returned HTTP {response.status_code}")

    try:
        body = response.json()
    except ValueError:
        return _unknown("Railway API returned a malformed response")

    if body.get("errors"):
        return _unknown("Railway API rejected the query")

    try:
        edges = body["data"]["deployments"]["edges"]
    except (KeyError, TypeError):
        return _unknown("Railway API returned an unexpected shape")

    if not edges:
        return _unknown("No Railway deployments found")

    node = edges[0].get("node") if isinstance(edges[0], dict) else None
    if not isinstance(node, dict):
        return _unknown("Railway API returned an unexpected shape")

    return _to_status(node)
