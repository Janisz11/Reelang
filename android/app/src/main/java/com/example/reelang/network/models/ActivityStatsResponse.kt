package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class ActivityStatsResponse(
    @SerializedName("vocabulary_mastered") val vocabularyMastered: Int,
    @SerializedName("streak_days") val streakDays: Int,
    @SerializedName("hours_watched") val hoursWatched: Float,
    @SerializedName("weekly_activity") val weeklyActivity: List<DayStatResponse>,
    @SerializedName("target_languages") val targetLanguages: List<LanguageStatResponse>
)

data class DayStatResponse(
    @SerializedName("day") val day: String,
    @SerializedName("value") val value: Float
)

data class LanguageStatResponse(
    @SerializedName("flag") val flag: String,
    @SerializedName("name") val name: String,
    @SerializedName("progress") val progress: Float,
    @SerializedName("percent") val percent: Int
)

data class FollowResponse(
    @SerializedName("following") val following: Boolean
)

data class ActivityLogRequest(
    @SerializedName("watch_time_ms") val watchTimeMs: Long = 0,
    @SerializedName("reels_watched") val reelsWatched: Int = 0,
    @SerializedName("words_saved") val wordsSaved: Int = 0
)

data class ProfileUpdateRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatar_initials") val avatarInitials: String? = null
)
