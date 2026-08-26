package com.example.reelang.events

const val COMPLETION_PERCENT = 95.0
const val SKIP_MAX_WATCHED_MS = 3_000L
const val SKIP_MAX_PERCENT = 30.0
const val PROGRESS_INTERVAL_MS = 2_500L
private const val REPLAY_REWIND_MS = 1_500L

/**
 * Turns raw player callbacks into the event stream for one reel. Free of Android and
 * ExoPlayer types so the thresholds can be unit tested.
 */
class ReelPlaybackTracker(
    private val reelId: String,
    private val wasPrefetched: Boolean = false,
    private val networkType: () -> String = { NETWORK_UNKNOWN },
    private val now: () -> Long = System::currentTimeMillis,
    private val emit: (String, String, Map<String, Any?>) -> Unit
) {

    private var activated = false
    private var loadStartedAt: Long? = null
    private var bufferingStartedAt: Long? = null
    private var bufferingMs = 0L
    private var timingSent = false

    private var watchedMs = 0L
    private var durationMs = 0L
    private var lastPositionMs = 0L
    private var lastProgressAt = 0L
    private var completed = false
    private var finished = false

    /**
     * Called when the reel becomes the active page. Neighbouring pages stay composed, so
     * everything downstream is gated on this to keep unwatched reels out of the stream.
     */
    fun onActivated() {
        activated = true
        if (loadStartedAt == null) loadStartedAt = now()
    }

    fun onBufferingStarted() {
        if (bufferingStartedAt == null) bufferingStartedAt = now()
    }

    fun onBufferingEnded() {
        val startedAt = bufferingStartedAt ?: return
        bufferingMs += now() - startedAt
        bufferingStartedAt = null
    }

    fun onFirstFrameRendered() {
        if (!activated || timingSent) return
        timingSent = true
        val startedAt = loadStartedAt ?: now()
        emit(
            EventTypes.REEL_LOAD_TIMING,
            reelId,
            mapOf(
                "time_to_first_frame_ms" to (now() - startedAt).coerceAtLeast(0L),
                "was_prefetched" to wasPrefetched,
                "buffering_ms" to pendingBufferingMs(),
                "network_type" to networkType()
            )
        )
    }

    fun onProgress(positionMs: Long, videoDurationMs: Long, force: Boolean = false) {
        if (!activated || finished) return
        if (videoDurationMs > 0) durationMs = videoDurationMs

        if (completed && positionMs < REPLAY_REWIND_MS && lastPositionMs > positionMs) {
            completed = false
            watchedMs = 0
            emit(EventTypes.REPLAY, reelId, emptyMap())
        }

        lastPositionMs = positionMs
        watchedMs = maxOf(watchedMs, positionMs)

        val moment = now()
        if (force || moment - lastProgressAt >= PROGRESS_INTERVAL_MS) {
            lastProgressAt = moment
            emit(EventTypes.WATCH_PROGRESS, reelId, progressPayload())
        }

        if (!completed && percent() >= COMPLETION_PERCENT) {
            completed = true
            emit(EventTypes.REEL_COMPLETED, reelId, progressPayload())
        }
    }

    fun onPlaybackEnded() {
        if (!activated || finished || completed) return
        completed = true
        watchedMs = maxOf(watchedMs, durationMs)
        emit(EventTypes.REEL_COMPLETED, reelId, progressPayload())
    }

    /** Called when the reel scrolls away or the player is torn down. */
    fun onLeft() {
        if (finished) return
        finished = true
        onBufferingEnded()
        if (!activated) return
        if (!completed && (watchedMs < SKIP_MAX_WATCHED_MS || percent() < SKIP_MAX_PERCENT)) {
            emit(EventTypes.SKIP, reelId, mapOf("watched_ms" to watchedMs))
        }
    }

    private fun pendingBufferingMs(): Long {
        val startedAt = bufferingStartedAt ?: return bufferingMs
        return bufferingMs + (now() - startedAt)
    }

    private fun percent(): Double =
        if (durationMs <= 0) 0.0 else (watchedMs.toDouble() / durationMs * 100).coerceIn(0.0, 100.0)

    /**
     * `watch_percent` duplicates `percent` because the deployed aggregator averages that key
     * into reel_stats.avg_watch_percent.
     */
    private fun progressPayload(): Map<String, Any?> = mapOf(
        "watched_ms" to watchedMs,
        "video_duration_ms" to durationMs,
        "percent" to round1(percent()),
        "watch_percent" to round1(percent())
    )
}

private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
