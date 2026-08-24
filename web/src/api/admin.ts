import { ApiError, BASE_URL } from "./client";

export interface SchemaColumn {
  name: string;
  type: string;
  nullable: boolean;
  primary_key: boolean;
}

export interface SchemaForeignKey {
  column: string;
  references_table: string;
  references_column: string;
}

export interface SchemaTable {
  name: string;
  columns: SchemaColumn[];
  foreign_keys: SchemaForeignKey[];
}

export interface SchemaResponse {
  tables: SchemaTable[];
}

export type DeploymentPlatform = "railway" | "vercel";

export type DeploymentState = "success" | "building" | "failed" | "unknown";

export interface DeploymentStatus {
  platform: DeploymentPlatform;
  status: DeploymentState;
  raw_status: string | null;
  deployed_at: string | null;
  commit_sha: string | null;
  url: string | null;
  error: string | null;
}

export interface DeploymentsResponse {
  deployments: DeploymentStatus[];
}

export const ADMIN_TOKEN_STORAGE_KEY = "reelang_admin_token";

export const ADMIN_TOKEN_CHANGED_EVENT = "reelang:admin-token-changed";

export function readStoredAdminToken(): string {
  try {
    return sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

export function storeAdminToken(token: string): void {
  try {
    if (token) sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token);
    else sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY);
  } catch {
    return;
  }
  window.dispatchEvent(new Event(ADMIN_TOKEN_CHANGED_EVENT));
}

async function requestAdmin<T>(path: string, token: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "GET",
    headers: { "X-Admin-Token": token },
  });

  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = (await res.json()) as { detail?: unknown };
      if (typeof body.detail === "string") detail = body.detail;
    } catch {
      detail = res.statusText;
    }
    throw new ApiError(detail || `Request failed (${res.status})`, res.status);
  }

  return (await res.json()) as T;
}

export function fetchSchema(token: string): Promise<SchemaResponse> {
  return requestAdmin<SchemaResponse>("admin/schema", token);
}

export function fetchDeployments(token: string): Promise<DeploymentsResponse> {
  return requestAdmin<DeploymentsResponse>("admin/deployments", token);
}
