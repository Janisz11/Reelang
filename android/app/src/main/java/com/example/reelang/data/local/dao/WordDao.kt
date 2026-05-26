package com.example.reelang.data.local.dao

import androidx.room.*
import com.example.reelang.data.local.entities.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY createdAt DESC")
    fun getAllWords(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE status = :status")
    fun getWordsByStatus(status: String): Flow<List<WordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Delete
    suspend fun deleteWord(word: WordEntity)

    @Query("DELETE FROM words")
    suspend fun deleteAllWords()

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getWordCount(): Int
}
