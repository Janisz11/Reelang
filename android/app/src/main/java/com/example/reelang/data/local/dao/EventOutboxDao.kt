package com.example.reelang.data.local.dao

import androidx.room.*
import com.example.reelang.data.local.entities.EventOutboxEntity

@Dao
interface EventOutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: EventOutboxEntity)

    @Query(
        "SELECT * FROM event_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun pendingEvents(limit: Int): List<EventOutboxEntity>

    @Query("SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("UPDATE event_outbox SET status = 'SENT' WHERE eventId IN (:ids)")
    suspend fun markAsSent(ids: List<String>)

    @Query("UPDATE event_outbox SET retryCount = retryCount + 1 WHERE eventId IN (:ids)")
    suspend fun incrementRetryCount(ids: List<String>)

    @Query(
        "UPDATE event_outbox SET status = 'FAILED' " +
            "WHERE status = 'PENDING' AND retryCount >= :maxRetries"
    )
    suspend fun failExhausted(maxRetries: Int)

    @Query("DELETE FROM event_outbox WHERE status = 'SENT' AND createdAt < :sentBefore")
    suspend fun deleteOld(sentBefore: Long)

    @Query("SELECT * FROM event_outbox WHERE eventId = :eventId")
    suspend fun findById(eventId: String): EventOutboxEntity?
}
