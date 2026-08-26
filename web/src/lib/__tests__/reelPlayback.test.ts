import { beforeEach, describe, expect, it } from "vitest";
import { createPlaybackTracker } from "../reelPlayback";

let clock = 0;
let emitted: Array<{ type: string; reelId: string; payload: Record<string, unknown> }> = [];

function tracker(wasPrefetched = false) {
  return createPlaybackTracker("reel-1", {
    wasPrefetched,
    networkType: () => "4g",
    now: () => clock,
    emit: (type, reelId, payload) => emitted.push({ type, reelId, payload }),
  });
}

const types = () => emitted.map((event) => event.type);
const payloadOf = (type: string) => emitted.find((event) => event.type === type)!.payload;
const countOf = (type: string) => types().filter((entry) => entry === type).length;

beforeEach(() => {
  clock = 0;
  emitted = [];
});

describe("inactive neighbours", () => {
  it("emit nothing until the reel becomes the visible one", () => {
    const player = tracker();
    player.onLoadStarted();
    player.onFirstFrameRendered();
    clock += 1_000;
    player.onProgress(1_000, 10_000);
    player.onPlaybackEnded();
    player.onLeft();

    expect(emitted).toHaveLength(0);
  });
});

describe("load timing", () => {
  it("reports time to first frame once", () => {
    const player = tracker();
    player.onActivated();
    clock += 720;

    player.onFirstFrameRendered();
    player.onFirstFrameRendered();

    expect(countOf("reel_load_timing")).toBe(1);
    expect(payloadOf("reel_load_timing")).toEqual({
      time_to_first_frame_ms: 720,
      was_prefetched: false,
      buffering_ms: 0,
      network_type: "4g",
    });
  });

  it("sums the waiting stretches before the first frame", () => {
    const player = tracker();
    player.onActivated();
    player.onBufferingStarted();
    clock += 300;
    player.onBufferingEnded();
    clock += 100;
    player.onBufferingStarted();
    clock += 200;
    player.onFirstFrameRendered();

    expect(payloadOf("reel_load_timing").buffering_ms).toBe(500);
    expect(payloadOf("reel_load_timing").time_to_first_frame_ms).toBe(600);
  });

  it("passes the prefetch flag through", () => {
    const player = tracker(true);
    player.onActivated();
    player.onLoadStarted();
    player.onFirstFrameRendered();

    expect(payloadOf("reel_load_timing").was_prefetched).toBe(true);
  });

  it("is not repeated on replay", () => {
    const player = tracker();
    player.onActivated();
    player.onLoadStarted();
    player.onFirstFrameRendered();
    clock += 5_000;
    player.onProgress(9_600, 10_000);
    clock += 3_000;
    player.onProgress(200, 10_000);

    expect(countOf("reel_load_timing")).toBe(1);
    expect(countOf("replay")).toBe(1);
  });
});

describe("watch progress", () => {
  it("carries the documented payload shape", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;

    player.onProgress(2_500, 10_000);

    expect(payloadOf("watch_progress")).toEqual({
      watched_ms: 2_500,
      video_duration_ms: 10_000,
      percent: 25,
      watch_percent: 25,
    });
  });

  it("is throttled to the sample interval", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(2_500, 10_000);
    clock += 500;
    player.onProgress(3_000, 10_000);
    clock += 2_500;
    player.onProgress(5_500, 10_000);

    expect(countOf("watch_progress")).toBe(2);
  });

  it("can be forced past the throttle, as on pause", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(2_500, 10_000);
    clock += 100;
    player.onProgress(2_600, 10_000, true);

    expect(countOf("watch_progress")).toBe(2);
  });
});

describe("completion and replay", () => {
  it("completes once past 95 percent", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(9_000, 10_000);
    expect(countOf("reel_completed")).toBe(0);

    clock += 3_000;
    player.onProgress(9_600, 10_000);

    expect(countOf("reel_completed")).toBe(1);
    expect(payloadOf("reel_completed").percent).toBe(96);
  });

  it("completes only once", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(9_600, 10_000);
    clock += 3_000;
    player.onProgress(9_900, 10_000);

    expect(countOf("reel_completed")).toBe(1);
  });

  it("counts a rewind after completion as a replay", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(9_800, 10_000);
    clock += 3_000;
    player.onProgress(300, 10_000);

    expect(countOf("replay")).toBe(1);
    expect(payloadOf("replay")).toEqual({});
  });

  it("does not call a rewind before completion a replay", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(4_000, 10_000);
    clock += 3_000;
    player.onProgress(200, 10_000);

    expect(countOf("replay")).toBe(0);
  });

  it("treats the ended event as a completion", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(4_000, 10_000);

    player.onPlaybackEnded();

    expect(countOf("reel_completed")).toBe(1);
    expect(payloadOf("reel_completed").percent).toBe(100);
  });
});

describe("skips", () => {
  it("reports a skip below three seconds watched", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(1_800, 60_000);

    player.onLeft();

    expect(payloadOf("skip")).toEqual({ watched_ms: 1_800 });
  });

  it("reports a skip below 30 percent even past three seconds", () => {
    const player = tracker();
    player.onActivated();
    clock += 5_000;
    player.onProgress(5_000, 60_000);

    player.onLeft();

    expect(countOf("skip")).toBe(1);
  });

  it("does not report a skip past both thresholds", () => {
    const player = tracker();
    player.onActivated();
    clock += 20_000;
    player.onProgress(20_000, 60_000);

    player.onLeft();

    expect(countOf("skip")).toBe(0);
  });

  it("never reports a completed reel as skipped", () => {
    const player = tracker();
    player.onActivated();
    clock += 3_000;
    player.onProgress(9_700, 10_000);

    player.onLeft();

    expect(countOf("skip")).toBe(0);
  });

  it("emits nothing after the reel is left", () => {
    const player = tracker();
    player.onActivated();
    player.onLeft();
    const afterLeaving = emitted.length;

    clock += 5_000;
    player.onProgress(5_000, 10_000);
    player.onPlaybackEnded();
    player.onLeft();

    expect(emitted).toHaveLength(afterLeaving);
  });
});
