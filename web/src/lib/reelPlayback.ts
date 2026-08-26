import { enqueueEvent } from "./eventTracking";

export const COMPLETION_PERCENT = 95;
export const SKIP_MAX_WATCHED_MS = 3_000;
export const SKIP_MAX_PERCENT = 30;
export const PROGRESS_INTERVAL_MS = 2_500;
const REPLAY_REWIND_MS = 1_500;

export interface PlaybackTrackerOptions {
  wasPrefetched?: boolean;
  networkType?: () => string;
  now?: () => number;
  emit?: (eventType: Parameters<typeof enqueueEvent>[0], reelId: string, payload: Record<string, unknown>) => void;
}

export function networkTypeFromConnection(): string {
  const connection = (navigator as { connection?: { effectiveType?: string } }).connection;
  return connection?.effectiveType ?? "unknown";
}

/**
 * Mirrors the Android ReelPlaybackTracker so both clients emit the same shapes.
 * Driven by <video> media events rather than ExoPlayer callbacks.
 */
export function createPlaybackTracker(reelId: string, options: PlaybackTrackerOptions = {}) {
  const {
    wasPrefetched = false,
    networkType = networkTypeFromConnection,
    now = () => Date.now(),
    emit = enqueueEvent,
  } = options;

  let activated = false;
  let loadStartedAt: number | null = null;
  let bufferingStartedAt: number | null = null;
  let bufferingMs = 0;
  let timingSent = false;

  let watchedMs = 0;
  let durationMs = 0;
  let lastPositionMs = 0;
  let lastProgressAt = 0;
  let completed = false;
  let finished = false;

  const percent = () =>
    durationMs <= 0 ? 0 : Math.min(100, Math.max(0, (watchedMs / durationMs) * 100));

  const round1 = (value: number) => Math.round(value * 10) / 10;

  const progressPayload = () => ({
    watched_ms: watchedMs,
    video_duration_ms: durationMs,
    percent: round1(percent()),
    // The deployed aggregator averages `watch_percent` into reel_stats.avg_watch_percent.
    watch_percent: round1(percent()),
  });

  return {
    /**
     * The feed mounts every card at once, so nothing is reported until the reel actually
     * becomes the visible one.
     */
    onActivated() {
      activated = true;
      if (loadStartedAt === null) loadStartedAt = now();
    },

    onLoadStarted() {
      if (activated && loadStartedAt === null) loadStartedAt = now();
    },

    onBufferingStarted() {
      if (bufferingStartedAt === null) bufferingStartedAt = now();
    },

    onBufferingEnded() {
      if (bufferingStartedAt === null) return;
      bufferingMs += now() - bufferingStartedAt;
      bufferingStartedAt = null;
    },

    onFirstFrameRendered() {
      if (!activated || timingSent) return;
      timingSent = true;
      const startedAt = loadStartedAt ?? now();
      const pendingBuffering =
        bufferingStartedAt === null ? bufferingMs : bufferingMs + (now() - bufferingStartedAt);
      emit("reel_load_timing", reelId, {
        time_to_first_frame_ms: Math.max(0, now() - startedAt),
        was_prefetched: wasPrefetched,
        buffering_ms: pendingBuffering,
        network_type: networkType(),
      });
    },

    onProgress(positionMs: number, videoDurationMs: number, force = false) {
      if (!activated || finished) return;
      if (videoDurationMs > 0) durationMs = videoDurationMs;

      if (completed && positionMs < REPLAY_REWIND_MS && lastPositionMs > positionMs) {
        completed = false;
        watchedMs = 0;
        emit("replay", reelId, {});
      }

      lastPositionMs = positionMs;
      watchedMs = Math.max(watchedMs, positionMs);

      const moment = now();
      if (force || moment - lastProgressAt >= PROGRESS_INTERVAL_MS) {
        lastProgressAt = moment;
        emit("watch_progress", reelId, progressPayload());
      }

      if (!completed && percent() >= COMPLETION_PERCENT) {
        completed = true;
        emit("reel_completed", reelId, progressPayload());
      }
    },

    onPlaybackEnded() {
      if (!activated || finished || completed) return;
      completed = true;
      watchedMs = Math.max(watchedMs, durationMs);
      emit("reel_completed", reelId, progressPayload());
    },

    onLeft() {
      if (finished) return;
      finished = true;
      this.onBufferingEnded();
      if (!activated) return;
      if (!completed && (watchedMs < SKIP_MAX_WATCHED_MS || percent() < SKIP_MAX_PERCENT)) {
        emit("skip", reelId, { watched_ms: watchedMs });
      }
    },
  };
}

export type PlaybackTracker = ReturnType<typeof createPlaybackTracker>;
