package com.example.reelang.unit

import com.example.reelang.data.local.entities.EventOutboxEntity
import com.example.reelang.data.local.entities.OUTBOX_FAILED
import com.example.reelang.data.local.entities.OUTBOX_PENDING
import com.example.reelang.data.local.entities.OUTBOX_SENT
import com.example.reelang.events.EventUploader
import com.example.reelang.events.OutboxSync
import com.example.reelang.events.SyncOutcome
import com.example.reelang.network.models.EventEnvelopeRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OutboxSyncTest {

    private val dao = FakeEventOutboxDao()
    private var clock = 1_000_000L

    private class RecordingUploader(var succeed: Boolean = true) : EventUploader {
        val batches = mutableListOf<List<EventEnvelopeRequest>>()
        var thrown: Throwable? = null

        override suspend fun upload(events: List<EventEnvelopeRequest>): Boolean {
            batches += events
            thrown?.let { throw it }
            return succeed
        }
    }

    private fun sync(
        uploader: EventUploader,
        batchLimit: Int = 50,
        maxRetries: Int = 10
    ) = OutboxSync(
        dao = dao,
        uploader = uploader,
        userId = { "user-42" },
        batchLimit = batchLimit,
        maxRetries = maxRetries,
        now = { clock }
    )

    private suspend fun seed(
        eventType: String = "reel_impression",
        reelId: String = "reel-1",
        payload: String = "{}",
        status: String = OUTBOX_PENDING,
        createdAt: Long = clock,
        retryCount: Int = 0
    ): EventOutboxEntity {
        val entity = EventOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            reelId = reelId,
            sessionId = "11111111-1111-1111-1111-111111111111",
            clientTimestamp = "2026-08-26T10:00:00.000Z",
            payload = payload,
            status = status,
            createdAt = createdAt,
            retryCount = retryCount
        )
        dao.insert(entity)
        return entity
    }

    @Test
    fun anEmptyOutboxReportsNothingPending() = runTest {
        assertEquals(SyncOutcome.NOTHING_PENDING, sync(RecordingUploader()).syncOnce())
    }

    @Test
    fun successfulUploadMarksTheBatchSent() = runTest {
        val first = seed(reelId = "a")
        val second = seed(reelId = "b")
        val uploader = RecordingUploader(succeed = true)

        assertEquals(SyncOutcome.UPLOADED, sync(uploader).syncOnce())

        assertEquals(OUTBOX_SENT, dao.findById(first.eventId)?.status)
        assertEquals(OUTBOX_SENT, dao.findById(second.eventId)?.status)
        assertEquals(0, dao.pendingCount())
    }

    @Test
    fun uploadedEnvelopeCarriesTheAuthenticatedUserAndPlatform() = runTest {
        seed(eventType = "reel_load_timing", reelId = "reel-9", payload = """{"buffering_ms":40}""")
        val uploader = RecordingUploader()

        sync(uploader).syncOnce()

        val envelope = uploader.batches.single().single()
        assertEquals("user-42", envelope.userId)
        assertEquals("android", envelope.platform)
        assertEquals("reel_load_timing", envelope.eventType)
        assertEquals("reel-9", envelope.reelId)
        assertEquals(40, envelope.payload.get("buffering_ms").asInt)
    }

    @Test
    fun failedUploadLeavesEventsPendingAndBumpsRetryCount() = runTest {
        val event = seed()
        val uploader = RecordingUploader(succeed = false)

        assertEquals(SyncOutcome.RETRY_LATER, sync(uploader).syncOnce())

        val stored = dao.findById(event.eventId)
        assertEquals(OUTBOX_PENDING, stored?.status)
        assertEquals(1, stored?.retryCount)
    }

    @Test
    fun aThrowingUploaderIsTreatedAsAFailedAttempt() = runTest {
        val event = seed()
        val uploader = RecordingUploader().apply { thrown = java.io.IOException("offline") }

        assertEquals(SyncOutcome.RETRY_LATER, sync(uploader).syncOnce())

        assertEquals(OUTBOX_PENDING, dao.findById(event.eventId)?.status)
        assertEquals(1, dao.findById(event.eventId)?.retryCount)
    }

    @Test
    fun eventsAreGivenUpOnceTheRetryCeilingIsReached() = runTest {
        val event = seed()
        val uploader = RecordingUploader(succeed = false)
        val sync = sync(uploader, maxRetries = 3)

        repeat(3) { sync.syncOnce() }

        assertEquals(OUTBOX_FAILED, dao.findById(event.eventId)?.status)
        assertEquals(3, dao.findById(event.eventId)?.retryCount)
    }

    @Test
    fun failedEventsAreNeverUploadedAgain() = runTest {
        seed()
        val uploader = RecordingUploader(succeed = false)
        val sync = sync(uploader, maxRetries = 2)

        repeat(4) { sync.syncOnce() }

        assertEquals(2, uploader.batches.size)
        assertEquals(SyncOutcome.NOTHING_PENDING, sync.syncOnce())
    }

    @Test
    fun aBatchNeverExceedsTheConfiguredLimit() = runTest {
        repeat(120) { seed(createdAt = clock + it) }
        val uploader = RecordingUploader()

        sync(uploader, batchLimit = 50).syncOnce()

        assertEquals(50, uploader.batches.single().size)
        assertEquals(70, dao.pendingCount())
    }

    @Test
    fun sentEventsOlderThanTheTtlAreSweptAway() = runTest {
        val stale = seed(status = OUTBOX_SENT, createdAt = clock - 25 * 60 * 60 * 1000L)
        val fresh = seed(status = OUTBOX_SENT, createdAt = clock)

        sync(RecordingUploader()).syncOnce()

        assertNull(dao.findById(stale.eventId))
        assertNotNull(dao.findById(fresh.eventId))
    }

    @Test
    fun hasPendingReflectsTheRemainingBacklog() = runTest {
        val sync = sync(RecordingUploader())
        assertFalse(sync.hasPending())

        seed()
        assertTrue(sync.hasPending())

        sync.syncOnce()
        assertFalse(sync.hasPending())
    }
}
