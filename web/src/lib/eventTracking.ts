export type EventType =
  | "reel_impression"
  | "watch_progress"
  | "reel_completed"
  | "like"
  | "unlike"
  | "save"
  | "unsave"
  | "skip"
  | "replay"
  | "share"
  | "reel_load_timing";

export interface EventEnvelope {
  event_id: string;
  event_type: EventType;
  user_id: string;
  reel_id: string;
  session_id: string;
  platform: "web";
  client_timestamp: string;
  payload: Record<string, unknown>;
}

export const FLUSH_INTERVAL_MS = 10_000;
export const FLUSH_AT_QUEUE_SIZE = 20;
export const EVENTS_PATH = "events";

/** Regenerated on every page load, unlike Android's per-process id. */
export const SESSION_ID = randomUuid();

interface TrackingConfig {
  baseUrl: string;
  getUserId: () => string;
  getAuthToken: () => Promise<string | null>;
}

let config: TrackingConfig | null = null;
let queue: EventEnvelope[] = [];
let intervalId: number | undefined;
let teardown: (() => void) | undefined;

function randomUuid(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
  // Older Safari has getRandomValues but not randomUUID.
  const bytes = new Uint8Array(16);
  cryptoApi.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function buildEnvelope(
  eventType: EventType,
  reelId: string,
  userId: string,
  payload: Record<string, unknown> = {},
): EventEnvelope {
  return {
    event_id: randomUuid(),
    event_type: eventType,
    user_id: userId,
    reel_id: reelId,
    session_id: SESSION_ID,
    platform: "web",
    client_timestamp: new Date().toISOString(),
    payload,
  };
}

export function enqueueEvent(
  eventType: EventType,
  reelId: string,
  payload: Record<string, unknown> = {},
): void {
  if (!config) return;
  const userId = config.getUserId();
  // The API rejects a batch whose user_id does not match the caller, so anonymous
  // views are dropped rather than poisoning the next flush.
  if (!userId || !reelId) return;

  queue.push(buildEnvelope(eventType, reelId, userId, payload));
  if (queue.length >= FLUSH_AT_QUEUE_SIZE) void flushEvents();
}

/** Hands the pending batch to `send`, restoring it if the send did not go through. */
async function drain(send: (batch: EventEnvelope[]) => Promise<boolean> | boolean): Promise<boolean> {
  if (queue.length === 0) return true;

  const batch = queue;
  queue = [];
  const delivered = await send(batch);
  if (!delivered) queue = [...batch, ...queue];
  return delivered;
}

export async function flushEvents(): Promise<boolean> {
  const active = config;
  if (!active) return false;

  return drain(async (batch) => {
    try {
      const token = await active.getAuthToken();
      if (!token) return false;
      const response = await fetch(`${active.baseUrl}${EVENTS_PATH}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ events: batch }),
      });
      return response.ok;
    } catch {
      return false;
    }
  });
}

/**
 * Last-gasp flush when the tab is hidden or unloading. `/api/v1/events` is Firebase-authed
 * and sendBeacon cannot carry an Authorization header, so a keepalive fetch is the primary
 * path and sendBeacon only covers browsers without it.
 */
export async function flushEventsOnExit(): Promise<boolean> {
  const active = config;
  if (!active) return false;

  return drain(async (batch) => {
    const body = JSON.stringify({ events: batch });
    try {
      const token = await active.getAuthToken();
      if (token) {
        const response = await fetch(`${active.baseUrl}${EVENTS_PATH}`, {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
          body,
          keepalive: true,
        });
        return response.ok;
      }
    } catch {
      // fall through to the beacon
    }
    return (
      navigator.sendBeacon?.(
        `${active.baseUrl}${EVENTS_PATH}`,
        new Blob([body], { type: "application/json" }),
      ) ?? false
    );
  });
}

export function startEventTracking(next: TrackingConfig): () => void {
  stopEventTracking();
  config = next;

  const onVisibilityChange = () => {
    if (document.hidden) void flushEventsOnExit();
  };
  const onBeforeUnload = () => {
    void flushEventsOnExit();
  };

  intervalId = window.setInterval(() => void flushEvents(), FLUSH_INTERVAL_MS);
  document.addEventListener("visibilitychange", onVisibilityChange);
  window.addEventListener("beforeunload", onBeforeUnload);

  teardown = () => {
    document.removeEventListener("visibilitychange", onVisibilityChange);
    window.removeEventListener("beforeunload", onBeforeUnload);
  };
  return stopEventTracking;
}

export function stopEventTracking(): void {
  if (intervalId !== undefined) {
    clearInterval(intervalId);
    intervalId = undefined;
  }
  teardown?.();
  teardown = undefined;
  config = null;
}

/** Test seam: inspect and reset module state without going through the browser APIs. */
export const __testing = {
  queue: () => queue,
  reset: () => {
    stopEventTracking();
    queue = [];
  },
  configure: (next: TrackingConfig) => {
    config = next;
  },
};
