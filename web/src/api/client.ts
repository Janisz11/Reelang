import { auth } from "../firebase";

const RAW_BASE = import.meta.env.VITE_API_BASE_URL ?? "";
export const BASE_URL = RAW_BASE.endsWith("/") ? RAW_BASE : `${RAW_BASE}/`;

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function authHeader(): Promise<Record<string, string>> {
  const user = auth.currentUser;
  if (!user) return {};
  const token = await user.getIdToken();
  return { Authorization: `Bearer ${token}` };
}

type Query = Record<string, string | number | boolean | undefined | null>;

function withQuery(path: string, query?: Query): string {
  if (!query) return `${BASE_URL}${path}`;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${BASE_URL}${path}?${qs}` : `${BASE_URL}${path}`;
}

async function parseError(res: Response): Promise<never> {
  let detail = res.statusText;
  try {
    const body = (await res.json()) as { detail?: unknown };
    if (typeof body.detail === "string") detail = body.detail;
  } catch {
    // response had no JSON body — keep the status text
  }
  throw new ApiError(detail || `Request failed (${res.status})`, res.status);
}

async function request<T>(
  method: string,
  path: string,
  options: { query?: Query; body?: unknown; formData?: FormData } = {},
): Promise<T> {
  const headers: Record<string, string> = await authHeader();
  let payload: BodyInit | undefined;

  if (options.formData) {
    payload = options.formData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(options.body);
  }

  const res = await fetch(withQuery(path, options.query), { method, headers, body: payload });
  if (!res.ok) await parseError(res);
  if (res.status === 204) return undefined as T;

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export const http = {
  get: <T>(path: string, query?: Query) => request<T>("GET", path, { query }),
  post: <T>(path: string, options?: { query?: Query; body?: unknown; formData?: FormData }) =>
    request<T>("POST", path, options ?? {}),
  put: <T>(path: string, options?: { query?: Query; body?: unknown }) =>
    request<T>("PUT", path, options ?? {}),
  del: <T>(path: string, query?: Query) => request<T>("DELETE", path, { query }),
};

/**
 * Stream and thumbnail endpoints are unauthenticated and redirect to a
 * pre-signed R2 URL, so they can be used directly as <video>/<img> sources.
 */
export const streamUrl = (reelId: string) => `${BASE_URL}reels/${reelId}/stream`;
export const thumbnailUrl = (reelId: string) => `${BASE_URL}reels/${reelId}/thumbnail`;
