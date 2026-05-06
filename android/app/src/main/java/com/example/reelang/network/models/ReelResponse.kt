package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class ReelResponse(
    @SerializedName("id")              val id: String             = "",
    @SerializedName("channel_name")    val channelName: String    = "",
    @SerializedName("avatar_emoji")    val avatarEmoji: String    = "🌍",
    @SerializedName("original_text")   val originalText: String   = "",
    @SerializedName("translated_text") val translatedText: String = "",
    @SerializedName("clickable_word")  val clickableWord: String  = "",
    @SerializedName("language")        val language: String       = "en",
    @SerializedName("level")           val level: String          = "A1",
    @SerializedName("likes")           val likes: Int             = 0,
    @SerializedName("saves")           val saves: Int             = 0,
    @SerializedName("streak_days")     val streakDays: Int        = 0,
    @SerializedName("youtube_id")      val youtubeId: String?     = null
)
