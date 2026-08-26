package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

const val OUTBOX_PENDING = "PENDING"
const val OUTBOX_SENT = "SENT"
const val OUTBOX_FAILED = "FAILED"

@Entity(tableName = "event_outbox")
data class EventOutboxEntity(
    @PrimaryKey val eventId: String,
    val eventType: String,
    val reelId: String,
    val sessionId: String,
    val clientTimestamp: String,
    val payload: String,
    val status: String = OUTBOX_PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
