import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  EVENTS_PATH,
  FLUSH_AT_QUEUE_SIZE,
  SESSION_ID,
  __testing,
  buildEnvelope,
  enqueueEvent,
  flushEvents,
  flushEventsOnExit,
} from "../eventTracking";

const BASE_URL = "https://api.test/api/v1/";
const USER_ID = "user-42";

function configure(overrides: Partial<Parameters<typeof __testing.configure>[0]> = {}) {
  __testing.configure({
    baseUrl: BASE_URL,
    getUserId: () => USER_ID,
    getAuthToken: async () => "id-token",
    ...overrides,
  });
}

function okFetch() {
  return vi.fn().mockResolvedValue({ ok: true } as Response);
}

function bodyOf(fetchMock: ReturnType<typeof okFetch>, call = 0) {
  return JSON.parse(fetchMock.mock.calls[call][1].body as string);
}

beforeEach(() => {
  __testing.reset();
  configure();
});

afterEach(() => {
  __testing.reset();
  vi.unstubAllGlobals();
});

describe("buildEnvelope", () => {
  it("produces the shape the API contract requires", () => {
    const envelope = buildEnvelope("watch_progress", "reel-1", USER_ID, { watched_ms: 2500 });

    expect(envelope).toMatchObject({
      event_type: "watch_progress",
      user_id: USER_ID,
      reel_id: "reel-1",
      session_id: SESSION_ID,
      platform: "web",
      payload: { watched_ms: 2500 },
    });
  });

  it("mints a fresh uuid per event", () => {
    const first = buildEnvelope("like", "reel-1", USER_ID);
    const second = buildEnvelope("like", "reel-1", USER_ID);

    expect(first.event_id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    );
    expect(first.event_id).not.toBe(second.event_id);
  });

  it("reuses one session id for the whole page load", () => {
    expect(buildEnvelope("like", "reel-1", USER_ID).session_id).toBe(
      buildEnvelope("skip", "reel-2", USER_ID).session_id,
    );
  });

  it("stamps an ISO-8601 client timestamp", () => {
    const envelope = buildEnvelope("share", "reel-1", USER_ID);

    expect(new Date(envelope.client_timestamp).toISOString()).toBe(envelope.client_timestamp);
  });

  it("defaults the payload to an empty object", () => {
    expect(buildEnvelope("like", "reel-1", USER_ID).payload).toEqual({});
  });
});

describe("enqueueEvent", () => {
  it("appends to the in-memory queue", () => {
    enqueueEvent("reel_impression", "reel-1");
    enqueueEvent("skip", "reel-2", { watched_ms: 800 });

    expect(__testing.queue().map((event) => event.event_type)).toEqual(["reel_impression", "skip"]);
  });

  it("drops events with no signed-in user", () => {
    configure({ getUserId: () => "" });

    enqueueEvent("reel_impression", "reel-1");

    expect(__testing.queue()).toHaveLength(0);
  });

  it("does nothing once tracking has been stopped", () => {
    __testing.reset();

    enqueueEvent("reel_impression", "reel-1");

    expect(__testing.queue()).toHaveLength(0);
  });

  it("flushes as soon as the queue reaches the batch size", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);

    for (let i = 0; i < FLUSH_AT_QUEUE_SIZE; i += 1) enqueueEvent("reel_impression", `reel-${i}`);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

    expect(bodyOf(fetchMock).events).toHaveLength(FLUSH_AT_QUEUE_SIZE);
    expect(__testing.queue()).toHaveLength(0);
  });

  it("does not flush below the batch size", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);

    for (let i = 0; i < FLUSH_AT_QUEUE_SIZE - 1; i += 1)
      enqueueEvent("reel_impression", `reel-${i}`);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(__testing.queue()).toHaveLength(FLUSH_AT_QUEUE_SIZE - 1);
  });
});

describe("flushEvents", () => {
  it("POSTs the batch to the events endpoint with the bearer token", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);
    enqueueEvent("like", "reel-1");

    await flushEvents();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${BASE_URL}${EVENTS_PATH}`);
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer id-token");
    expect(bodyOf(fetchMock).events[0].event_type).toBe("like");
  });

  it("clears the queue on success", async () => {
    vi.stubGlobal("fetch", okFetch());
    enqueueEvent("like", "reel-1");

    await expect(flushEvents()).resolves.toBe(true);
    expect(__testing.queue()).toHaveLength(0);
  });

  it("keeps the batch queued when the server rejects it", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 503 } as Response));
    enqueueEvent("like", "reel-1");

    await expect(flushEvents()).resolves.toBe(false);
    expect(__testing.queue().map((event) => event.event_type)).toEqual(["like"]);
  });

  it("keeps the batch queued when the network throws", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    enqueueEvent("like", "reel-1");

    await expect(flushEvents()).resolves.toBe(false);
    expect(__testing.queue()).toHaveLength(1);
  });

  it("restores a failed batch ahead of events queued meanwhile", async () => {
    let release: (value: Response) => void = () => undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockReturnValue(
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
      ),
    );
    enqueueEvent("like", "reel-1");

    const pending = flushEvents();
    enqueueEvent("share", "reel-2");
    release({ ok: false } as Response);
    await pending;

    expect(__testing.queue().map((event) => event.event_type)).toEqual(["like", "share"]);
  });

  it("does not call the API for an empty queue", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);

    await expect(flushEvents()).resolves.toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("keeps events queued when no auth token is available", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);
    configure({ getAuthToken: async () => null });
    enqueueEvent("like", "reel-1");

    await expect(flushEvents()).resolves.toBe(false);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(__testing.queue()).toHaveLength(1);
  });
});

describe("flushEventsOnExit", () => {
  it("uses a keepalive fetch so the batch survives the tab going away", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);
    enqueueEvent("watch_progress", "reel-1", { watched_ms: 4000 });

    await expect(flushEventsOnExit()).resolves.toBe(true);

    expect(fetchMock.mock.calls[0][1].keepalive).toBe(true);
    expect(__testing.queue()).toHaveLength(0);
  });

  it("falls back to sendBeacon when the keepalive fetch fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("unloading")));
    const sendBeacon = vi.fn().mockReturnValue(true);
    vi.stubGlobal("navigator", { sendBeacon });
    enqueueEvent("share", "reel-1");

    await expect(flushEventsOnExit()).resolves.toBe(true);

    expect(sendBeacon).toHaveBeenCalledTimes(1);
    expect(sendBeacon.mock.calls[0][0]).toBe(`${BASE_URL}${EVENTS_PATH}`);
    expect(__testing.queue()).toHaveLength(0);
  });

  it("falls back to sendBeacon when there is no auth token", async () => {
    const fetchMock = okFetch();
    vi.stubGlobal("fetch", fetchMock);
    const sendBeacon = vi.fn().mockReturnValue(true);
    vi.stubGlobal("navigator", { sendBeacon });
    configure({ getAuthToken: async () => null });
    enqueueEvent("share", "reel-1");

    await flushEventsOnExit();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(sendBeacon).toHaveBeenCalledTimes(1);
  });

  it("puts the batch back when the beacon is refused too", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("unloading")));
    vi.stubGlobal("navigator", { sendBeacon: vi.fn().mockReturnValue(false) });
    enqueueEvent("share", "reel-1");

    await expect(flushEventsOnExit()).resolves.toBe(false);
    expect(__testing.queue()).toHaveLength(1);
  });
});
