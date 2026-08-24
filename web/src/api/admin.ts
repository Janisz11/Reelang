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

export type LogLevel = "WARNING" | "ERROR" | "CRITICAL";

export interface AppLogEntry {
  id: number;
  level: string;
  logger_name: string;
  message: string;
  context: Record<string, unknown> | null;
  created_at: string;
}

export type EventStatsWindow = "24h" | "14d";

export interface EventTimeBucket {
  bucket: string;
  event_type: string;
  count: number;
}

export interface ReelRateEntry {
  reel_id: string;
  title: string | null;
  impressions: number;
  count: number;
  rate: number;
}

export interface RecentEvent {
  event_id: string;
  event_type: string;
  reel_id: string;
  platform: string;
  server_timestamp: string;
}

export interface EventStatsResponse {
  window: EventStatsWindow;
  time_series: EventTimeBucket[];
  top_completion: ReelRateEntry[];
  top_skip: ReelRateEntry[];
  recent_events: RecentEvent[];
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

type AdminQuery = Record<string, string | number | undefined | null>;

async function requestAdmin<T>(path: string, token: string, query?: AdminQuery): Promise<T> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  }
  const suffix = params.toString() ? `?${params}` : "";

  const res = await fetch(`${BASE_URL}${path}${suffix}`, {
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

export function fetchEventStats(
  token: string,
  window: EventStatsWindow,
): Promise<EventStatsResponse> {
  return requestAdmin<EventStatsResponse>("admin/event-stats", token, { window });
}

export function fetchLogs(
  token: string,
  options: { level?: LogLevel; limit?: number; before?: string } = {},
): Promise<AppLogEntry[]> {
  return requestAdmin<AppLogEntry[]>("admin/logs", token, {
    level: options.level,
    limit: options.limit,
    before: options.before,
  });
}
