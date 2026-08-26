package com.example.reelang.unit

import com.example.reelang.data.local.dao.EventOutboxDao
import com.example.reelang.data.local.entities.EventOutboxEntity
import com.example.reelang.data.local.entities.OUTBOX_FAILED
import com.example.reelang.data.local.entities.OUTBOX_PENDING
import com.example.reelang.data.local.entities.OUTBOX_SENT

/** In-memory stand-in for the Room DAO, mirroring the semantics of its @Query statements. */
class FakeEventOutboxDao : EventOutboxDao {

    val rows = linkedMapOf<String, EventOutboxEntity>()

    override suspend fun insert(event: EventOutboxEntity) {
        rows.putIfAbsent(event.eventId, event)
    }

    override suspend fun pendingEvents(limit: Int): List<EventOutboxEntity> =
        rows.values.filter { it.status == OUTBOX_PENDING }.sortedBy { it.createdAt }.take(limit)

    override suspend fun pendingCount(): Int = rows.values.count { it.status == OUTBOX_PENDING }

    override suspend fun markAsSent(ids: List<String>) = update(ids) { it.copy(status = OUTBOX_SENT) }

    override suspend fun incrementRetryCount(ids: List<String>) =
        update(ids) { it.copy(retryCount = it.retryCount + 1) }

    override suspend fun failExhausted(maxRetries: Int) {
        val doomed = rows.values
            .filter { it.status == OUTBOX_PENDING && it.retryCount >= maxRetries }
            .map { it.eventId }
        update(doomed) { it.copy(status = OUTBOX_FAILED) }
    }

    override suspend fun deleteOld(sentBefore: Long) {
        rows.values
            .filter { it.status == OUTBOX_SENT && it.createdAt < sentBefore }
            .map { it.eventId }
            .forEach { rows.remove(it) }
    }

    override suspend fun findById(eventId: String): EventOutboxEntity? = rows[eventId]

    private fun update(ids: List<String>, block: (EventOutboxEntity) -> EventOutboxEntity) {
        for (id in ids) rows[id]?.let { rows[id] = block(it) }
    }
}
