package com.example.reelang.events

import com.example.reelang.data.local.dao.EventOutboxDao
import com.example.reelang.data.local.entities.EventOutboxEntity
import com.example.reelang.network.models.EventEnvelopeRequest

const val OUTBOX_BATCH_LIMIT = 50
const val OUTBOX_MAX_RETRIES = 10
const val OUTBOX_SENT_TTL_MS = 24 * 60 * 60 * 1000L

fun interface EventUploader {
    /** Returns true when the backend answered 2xx, false for any other outcome. */
    suspend fun upload(events: List<EventEnvelopeRequest>): Boolean
}

enum class SyncOutcome { NOTHING_PENDING, UPLOADED, RETRY_LATER }

/**
 * Drains one batch out of the outbox. Kept free of Android types so the retry and
 * give-up rules can be exercised as plain unit tests.
 */
class OutboxSync(
    private val dao: EventOutboxDao,
    private val uploader: EventUploader,
    private val userId: () -> String,
    private val batchLimit: Int = OUTBOX_BATCH_LIMIT,
    private val maxRetries: Int = OUTBOX_MAX_RETRIES,
    private val sentTtlMs: Long = OUTBOX_SENT_TTL_MS,
    private val now: () -> Long = System::currentTimeMillis
) {

    suspend fun syncOnce(): SyncOutcome {
        dao.deleteOld(now() - sentTtlMs)

        val pending = dao.pendingEvents(batchLimit)
        if (pending.isEmpty()) return SyncOutcome.NOTHING_PENDING

        val ids = pending.map { it.eventId }
        val uploaded = runCatching { uploader.upload(pending.map { it.toEnvelope(userId()) }) }
            .getOrDefault(false)

        return if (uploaded) {
            dao.markAsSent(ids)
            SyncOutcome.UPLOADED
        } else {
            dao.incrementRetryCount(ids)
            dao.failExhausted(maxRetries)
            SyncOutcome.RETRY_LATER
        }
    }

    suspend fun hasPending(): Boolean = dao.pendingCount() > 0
}

internal fun EventOutboxEntity.toEnvelope(userId: String) = EventEnvelopeRequest(
    eventId = eventId,
    eventType = eventType,
    userId = userId,
    reelId = reelId,
    sessionId = sessionId,
    platform = EVENT_PLATFORM_ANDROID,
    clientTimestamp = clientTimestamp,
    payload = decodeEventPayload(payload)
)
