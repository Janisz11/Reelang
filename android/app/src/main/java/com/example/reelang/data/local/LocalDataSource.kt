package com.example.reelang.data.local

import com.example.reelang.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class LocalDataSource(private val db: ReelangDatabase) {

    fun getAllWords(): Flow<List<WordEntity>> = db.wordDao().getAllWords()
    suspend fun saveWords(words: List<WordEntity>) = db.wordDao().insertWords(words)
    suspend fun saveWord(word: WordEntity) = db.wordDao().insertWord(word)
    suspend fun getWordCount(): Int = db.wordDao().getWordCount()

    fun getCachedReels(): Flow<List<ReelEntity>> = db.reelDao().getCachedReels()
    suspend fun cacheReels(reels: List<ReelEntity>) {
        db.reelDao().insertReels(reels)
        val threshold = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        db.reelDao().deleteOldReels(threshold)
    }

    suspend fun getCaptions(reelId: String) = db.captionDao().getCaptionsForReel(reelId)
    suspend fun saveCaptions(captions: List<CaptionEntity>) = db.captionDao().insertCaptions(captions)

    suspend fun getProfile(userId: String) = db.userProfileDao().getProfile(userId)
    suspend fun saveProfile(profile: UserProfileEntity) = db.userProfileDao().insertProfile(profile)

    fun getRecentSessions(): Flow<List<PracticeSessionEntity>> = db.practiceSessionDao().getRecentSessions()
    suspend fun savePracticeSession(session: PracticeSessionEntity) = db.practiceSessionDao().insertSession(session)
}
