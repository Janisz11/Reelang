package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reels")
data class ReelEntity(
    @PrimaryKey val id: String,
    val youtubeId: String?,
    val title: String,
    val channelName: String?,
    val thumbnailUrl: String?,
    val language: String,
    val level: String?,
    val tags: String?,
    val durationMs: Int?,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
