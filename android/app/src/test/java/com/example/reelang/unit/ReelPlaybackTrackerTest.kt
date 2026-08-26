package com.example.reelang.unit

import com.example.reelang.events.EventTypes
import com.example.reelang.events.ReelPlaybackTracker
import org.junit.Assert.*
import org.junit.Test

class ReelPlaybackTrackerTest {

    private var clock = 0L
    private val emitted = mutableListOf<Pair<String, Map<String, Any?>>>()

    private fun tracker(wasPrefetched: Boolean = false) = ReelPlaybackTracker(
        reelId = "reel-1",
        wasPrefetched = wasPrefetched,
        networkType = { "wifi" },
        now = { clock },
        emit = { type, reelId, payload ->
            assertEquals("reel-1", reelId)
            emitted += type to payload
        }
    )

    private fun types() = emitted.map { it.first }

    private fun payloadOf(type: String) = emitted.first { it.first == type }.second

    @Test
    fun anInactiveNeighbourPageEmitsNothing() {
        val tracker = tracker()
        tracker.onFirstFrameRendered()
        clock += 1_000
        tracker.onProgress(1_000, 10_000)
        tracker.onPlaybackEnded()
        tracker.onLeft()

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun firstFrameEmitsLoadTimingOnce() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 640

        tracker.onFirstFrameRendered()
        tracker.onFirstFrameRendered()

        assertEquals(listOf(EventTypes.REEL_LOAD_TIMING), types())
        val payload = payloadOf(EventTypes.REEL_LOAD_TIMING)
        assertEquals(640L, payload["time_to_first_frame_ms"])
        assertEquals(false, payload["was_prefetched"])
        assertEquals("wifi", payload["network_type"])
    }

    @Test
    fun loadTimingSumsBufferingSpentBeforeTheFirstFrame() {
        val tracker = tracker()
        tracker.onActivated()
        tracker.onBufferingStarted()
        clock += 300
        tracker.onBufferingEnded()
        clock += 100
        tracker.onBufferingStarted()
        clock += 200
        tracker.onFirstFrameRendered()

        assertEquals(500L, payloadOf(EventTypes.REEL_LOAD_TIMING)["buffering_ms"])
        assertEquals(600L, payloadOf(EventTypes.REEL_LOAD_TIMING)["time_to_first_frame_ms"])
    }

    @Test
    fun loadTimingIsNotRepeatedOnReplay() {
        val tracker = tracker()
        tracker.onActivated()
        tracker.onFirstFrameRendered()
        clock += 10_000
        tracker.onProgress(9_600, 10_000)
        clock += 3_000
        tracker.onProgress(200, 10_000)

        assertEquals(1, types().count { it == EventTypes.REEL_LOAD_TIMING })
        assertTrue(types().contains(EventTypes.REPLAY))
    }

    @Test
    fun prefetchFlagIsPassedThrough() {
        val tracker = tracker(wasPrefetched = true)
        tracker.onActivated()
        tracker.onFirstFrameRendered()

        assertEquals(true, payloadOf(EventTypes.REEL_LOAD_TIMING)["was_prefetched"])
    }

    @Test
    fun watchProgressCarriesTheDocumentedShape() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000

        tracker.onProgress(2_500, 10_000)

        val payload = payloadOf(EventTypes.WATCH_PROGRESS)
        assertEquals(2_500L, payload["watched_ms"])
        assertEquals(10_000L, payload["video_duration_ms"])
        assertEquals(25.0, payload["percent"])
        assertEquals(25.0, payload["watch_percent"])
    }

    @Test
    fun watchProgressIsThrottledToTheSampleInterval() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(2_500, 10_000)
        clock += 500
        tracker.onProgress(3_000, 10_000)
        clock += 2_500
        tracker.onProgress(5_500, 10_000)

        assertEquals(2, types().count { it == EventTypes.WATCH_PROGRESS })
    }

    @Test
    fun forcedProgressBypassesTheThrottle() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(2_500, 10_000)
        clock += 100
        tracker.onProgress(2_600, 10_000, force = true)

        assertEquals(2, types().count { it == EventTypes.WATCH_PROGRESS })
    }

    @Test
    fun crossingNinetyFivePercentCompletesTheReel() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(9_000, 10_000)
        assertFalse(types().contains(EventTypes.REEL_COMPLETED))

        clock += 3_000
        tracker.onProgress(9_600, 10_000)

        assertEquals(1, types().count { it == EventTypes.REEL_COMPLETED })
        assertEquals(96.0, payloadOf(EventTypes.REEL_COMPLETED)["percent"])
    }

    @Test
    fun completionIsEmittedOnlyOnce() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(9_600, 10_000)
        clock += 3_000
        tracker.onProgress(9_900, 10_000)

        assertEquals(1, types().count { it == EventTypes.REEL_COMPLETED })
    }

    @Test
    fun rewindAfterCompletionCountsAsAReplay() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(9_800, 10_000)
        clock += 3_000
        tracker.onProgress(300, 10_000)

        assertEquals(1, types().count { it == EventTypes.REPLAY })
        assertEquals(0L, payloadOf(EventTypes.REPLAY).size.toLong())
    }

    @Test
    fun rewindBeforeCompletionIsNotAReplay() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(4_000, 10_000)
        clock += 3_000
        tracker.onProgress(200, 10_000)

        assertFalse(types().contains(EventTypes.REPLAY))
    }

    @Test
    fun leavingUnderThreeSecondsIsASkip() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(1_800, 60_000)

        tracker.onLeft()

        assertTrue(types().contains(EventTypes.SKIP))
        assertEquals(1_800L, payloadOf(EventTypes.SKIP)["watched_ms"])
    }

    @Test
    fun leavingUnderThirtyPercentIsASkipEvenAfterThreeSeconds() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 5_000
        tracker.onProgress(5_000, 60_000)

        tracker.onLeft()

        assertTrue(types().contains(EventTypes.SKIP))
    }

    @Test
    fun leavingPastBothThresholdsIsNotASkip() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 20_000
        tracker.onProgress(20_000, 60_000)

        tracker.onLeft()

        assertFalse(types().contains(EventTypes.SKIP))
    }

    @Test
    fun aCompletedReelIsNeverReportedAsSkipped() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(9_700, 10_000)

        tracker.onLeft()

        assertFalse(types().contains(EventTypes.SKIP))
    }

    @Test
    fun playbackEndedCompletesTheReelWhenProgressNeverReachedTheThreshold() {
        val tracker = tracker()
        tracker.onActivated()
        clock += 3_000
        tracker.onProgress(4_000, 10_000)

        tracker.onPlaybackEnded()

        assertEquals(1, types().count { it == EventTypes.REEL_COMPLETED })
        assertEquals(100.0, payloadOf(EventTypes.REEL_COMPLETED)["percent"])
    }

    @Test
    fun nothingIsEmittedAfterTheReelIsLeft() {
        val tracker = tracker()
        tracker.onActivated()
        tracker.onLeft()
        val afterLeaving = emitted.size

        clock += 5_000
        tracker.onProgress(5_000, 10_000)
        tracker.onPlaybackEnded()
        tracker.onLeft()

        assertEquals(afterLeaving, emitted.size)
    }
}
