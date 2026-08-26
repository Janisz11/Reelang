package com.example.reelang.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.reelang.data.local.ReelangDatabase
import com.example.reelang.data.local.dao.EventOutboxDao
import com.example.reelang.data.local.entities.EventOutboxEntity
import com.example.reelang.data.local.entities.OUTBOX_FAILED
import com.example.reelang.data.local.entities.OUTBOX_PENDING
import com.example.reelang.data.local.entities.OUTBOX_SENT
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EventOutboxDaoTest {

    private lateinit var db: ReelangDatabase
    private lateinit var dao: EventOutboxDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReelangDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.eventOutboxDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun event(
        eventType: String = "reel_impression",
        reelId: String = "reel-1",
        status: String = OUTBOX_PENDING,
        createdAt: Long = System.currentTimeMillis(),
        retryCount: Int = 0,
        payload: String = "{}"
    ) = EventOutboxEntity(
        eventId = UUID.randomUUID().toString(),
        eventType = eventType,
        reelId = reelId,
        sessionId = UUID.randomUUID().toString(),
        clientTimestamp = "2026-08-26T10:00:00.000Z",
        payload = payload,
        status = status,
        createdAt = createdAt,
        retryCount = retryCount
    )

    @Test
    fun insertStoresTheEventAsPending() = runBlocking {
        val entity = event(payload = """{"watched_ms":1200}""")

        dao.insert(entity)

        val stored = dao.findById(entity.eventId)
        assertNotNull(stored)
        assertEquals(OUTBOX_PENDING, stored?.status)
        assertEquals("""{"watched_ms":1200}""", stored?.payload)
        assertEquals(0, stored?.retryCount)
    }

    @Test
    fun insertIsIdempotentOnEventId() = runBlocking {
        val entity = event()

        dao.insert(entity)
        dao.insert(entity.copy(eventType = "skip"))

        assertEquals(1, dao.pendingCount())
        assertEquals("reel_impression", dao.findById(entity.eventId)?.eventType)
    }

    @Test
    fun pendingEventsReturnOldestFirst() = runBlocking {
        val newest = event(reelId = "newest", createdAt = 3_000)
        val oldest = event(reelId = "oldest", createdAt = 1_000)
        val middle = event(reelId = "middle", createdAt = 2_000)
        listOf(newest, oldest, middle).forEach { dao.insert(it) }

        val pending = dao.pendingEvents(10)

        assertEquals(listOf("oldest", "middle", "newest"), pending.map { it.reelId })
    }

    @Test
    fun pendingEventsRespectTheBatchLimit() = runBlocking {
        repeat(60) { dao.insert(event(createdAt = it.toLong())) }

        assertEquals(50, dao.pendingEvents(50).size)
        assertEquals(60, dao.pendingCount())
    }

    @Test
    fun pendingEventsExcludeSentAndFailedRows() = runBlocking {
        dao.insert(event(reelId = "pending"))
        dao.insert(event(reelId = "sent", status = OUTBOX_SENT))
        dao.insert(event(reelId = "failed", status = OUTBOX_FAILED))

        val pending = dao.pendingEvents(10)

        assertEquals(listOf("pending"), pending.map { it.reelId })
    }

    @Test
    fun markAsSentMovesOnlyTheGivenIds() = runBlocking {
        val sent = event(reelId = "sent")
        val untouched = event(reelId = "untouched")
        dao.insert(sent)
        dao.insert(untouched)

        dao.markAsSent(listOf(sent.eventId))

        assertEquals(OUTBOX_SENT, dao.findById(sent.eventId)?.status)
        assertEquals(OUTBOX_PENDING, dao.findById(untouched.eventId)?.status)
        assertEquals(1, dao.pendingCount())
    }

    @Test
    fun incrementRetryCountBumpsEveryGivenId() = runBlocking {
        val first = event()
        val second = event(retryCount = 4)
        dao.insert(first)
        dao.insert(second)

        dao.incrementRetryCount(listOf(first.eventId, second.eventId))

        assertEquals(1, dao.findById(first.eventId)?.retryCount)
        assertEquals(5, dao.findById(second.eventId)?.retryCount)
    }

    @Test
    fun failExhaustedGivesUpOnlyPastTheRetryCeiling() = runBlocking {
        val exhausted = event(reelId = "exhausted", retryCount = 10)
        val stillTrying = event(reelId = "still-trying", retryCount = 9)
        dao.insert(exhausted)
        dao.insert(stillTrying)

        dao.failExhausted(maxRetries = 10)

        assertEquals(OUTBOX_FAILED, dao.findById(exhausted.eventId)?.status)
        assertEquals(OUTBOX_PENDING, dao.findById(stillTrying.eventId)?.status)
        assertEquals(listOf("still-trying"), dao.pendingEvents(10).map { it.reelId })
    }

    @Test
    fun deleteOldRemovesOnlyStaleSentRows() = runBlocking {
        val now = System.currentTimeMillis()
        val dayAgo = now - 25 * 60 * 60 * 1000L
        val staleSent = event(reelId = "stale-sent", status = OUTBOX_SENT, createdAt = dayAgo)
        val freshSent = event(reelId = "fresh-sent", status = OUTBOX_SENT, createdAt = now)
        val stalePending = event(reelId = "stale-pending", createdAt = dayAgo)
        listOf(staleSent, freshSent, stalePending).forEach { dao.insert(it) }

        dao.deleteOld(now - 24 * 60 * 60 * 1000L)

        assertNull(dao.findById(staleSent.eventId))
        assertNotNull(dao.findById(freshSent.eventId))
        assertNotNull(dao.findById(stalePending.eventId))
    }
}
