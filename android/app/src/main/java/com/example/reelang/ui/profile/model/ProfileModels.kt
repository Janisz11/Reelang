package com.example.reelang.ui.profile.model

import androidx.compose.ui.graphics.Color

data class PostThumbnail(
    val id: String,
    val color: Color,
    val emoji: String,
    val thumbnailUrl: String? = null,
    val reelId: String? = null
)

data class DayStat(val day: String, val value: Float)

data class LanguageStat(
    val flag: String,
    val name: String,
    val progress: Float,
    val percent: Int
)

data class WeeklyStats(
    val vocabularyMastered: String,
    val streakDays: Int,
    val hoursWatched: Int,
    val weeklyActivity: List<DayStat>,
    val targetLanguages: List<LanguageStat>
)
