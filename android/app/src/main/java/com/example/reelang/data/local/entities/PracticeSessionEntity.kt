package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val knownCount: Int,
    val unknownCount: Int,
    val totalCards: Int,
    val completedAt: Long = System.currentTimeMillis()
)
