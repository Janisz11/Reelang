package com.example.reelang

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.reelang.data.local.ReelangDatabase
import com.example.reelang.data.local.entities.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var db: ReelangDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReelangDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveWord() = runBlocking {
        val word = WordEntity(
            id = "w1",
            term = "hola",
            definition = "a greeting",
            translation = "hello",
            language = "es",
            status = "new",
            reelId = null,
            createdAt = "2024-01-01T00:00:00Z"
        )
        db.wordDao().insertWord(word)
        val words = db.wordDao().getAllWords().first()
        assertEquals(1, words.size)
        assertEquals("hola", words[0].term)
    }

    @Test
    fun deleteWord() = runBlocking {
        val word = WordEntity(
            id = "w2",
            term = "adios",
            definition = null,
            translation = "goodbye",
            language = "es",
            status = "new",
            reelId = null,
            createdAt = "2024-01-01T00:00:00Z"
        )
        db.wordDao().insertWord(word)
        db.wordDao().deleteWord(word)
        val count = db.wordDao().getWordCount()
        assertEquals(0, count)
    }

    @Test
    fun wordCountReflectsInserts() = runBlocking {
        repeat(3) { i ->
            db.wordDao().insertWord(
                WordEntity(
                    id = "wc$i",
                    term = "word$i",
                    definition = null,
                    translation = null,
                    language = "es",
                    status = "new",
                    reelId = null,
                    createdAt = "2024-01-01T00:00:00Z"
                )
            )
        }
        assertEquals(3, db.wordDao().getWordCount())
    }

    @Test
    fun insertAndRetrieveReel() = runBlocking {
        val reel = ReelEntity(
            id = "r1",
            youtubeId = null,
            title = "Spanish lesson",
            channelName = "ReeLang",
            thumbnailUrl = null,
            language = "es",
            level = "A1",
            tags = null,
            durationMs = 60000
        )
        db.reelDao().insertReels(listOf(reel))
        val reels = db.reelDao().getCachedReels().first()
        assertEquals(1, reels.size)
        assertEquals("Spanish lesson", reels[0].title)
    }

    @Test
    fun insertAndRetrieveUserProfile() = runBlocking {
        val profile = UserProfileEntity(
            userId = "u1",
            username = "testuser",
            avatarInitials = "TU",
            followersCount = 10,
            followingCount = 5,
            totalLikes = 100,
            level = 3,
            streakDays = 7
        )
        db.userProfileDao().insertProfile(profile)
        val retrieved = db.userProfileDao().getProfile("u1")
        assertNotNull(retrieved)
        assertEquals("testuser", retrieved?.username)
        assertEquals("TU", retrieved?.avatarInitials)
    }

    @Test
    fun insertAndRetrievePracticeSession() = runBlocking {
        val session = PracticeSessionEntity(
            userId = "u1",
            knownCount = 8,
            unknownCount = 2,
            totalCards = 10
        )
        db.practiceSessionDao().insertSession(session)
        val sessions = db.practiceSessionDao().getRecentSessions().first()
        assertEquals(1, sessions.size)
        assertEquals(8, sessions[0].knownCount)
        assertEquals(10, sessions[0].totalCards)
    }
}
