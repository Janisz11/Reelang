package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captions")
data class CaptionEntity(
    @PrimaryKey val id: String,
    val reelId: String,
    val startMs: Int,
    val endMs: Int,
    val originalText: String,
    val translatedText: String?
)
