package com.example.reelang.data.local.dao

import androidx.room.*
import com.example.reelang.data.local.entities.ReelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Query("SELECT * FROM reels ORDER BY cachedAt DESC LIMIT 20")
    fun getCachedReels(): Flow<List<ReelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReels(reels: List<ReelEntity>)

    @Query("DELETE FROM reels WHERE cachedAt < :threshold")
    suspend fun deleteOldReels(threshold: Long)
}
