package com.example.reelang.data.local.dao

import androidx.room.*
import com.example.reelang.data.local.entities.PracticeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY completedAt DESC LIMIT 10")
    fun getRecentSessions(): Flow<List<PracticeSessionEntity>>

    @Insert
    suspend fun insertSession(session: PracticeSessionEntity)

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE userId = :userId")
    suspend fun getSessionCount(userId: String): Int
}
