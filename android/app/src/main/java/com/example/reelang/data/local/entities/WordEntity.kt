package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: String,
    val term: String,
    val definition: String?,
    val translation: String?,
    val language: String,
    val status: String,
    val repetitions: Int = 0,
    val easiness: Float = 2.5f,
    val intervalDays: Int = 1,
    val reelId: String?,
    val createdAt: String
)
