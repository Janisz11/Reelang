package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class CaptionSegment(
    @SerializedName("start_ms")      val startMs: Long,
    @SerializedName("end_ms")        val endMs: Long,
    @SerializedName("original_text") val originalText: String? = null,
    @SerializedName("translated_text") val translatedText: String? = null
)