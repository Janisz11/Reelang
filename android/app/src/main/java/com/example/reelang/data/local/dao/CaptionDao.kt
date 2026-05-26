package com.example.reelang.data.local.dao

import androidx.room.*
import com.example.reelang.data.local.entities.CaptionEntity

@Dao
interface CaptionDao {
    @Query("SELECT * FROM captions WHERE reelId = :reelId ORDER BY startMs")
    suspend fun getCaptionsForReel(reelId: String): List<CaptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaptions(captions: List<CaptionEntity>)
}
