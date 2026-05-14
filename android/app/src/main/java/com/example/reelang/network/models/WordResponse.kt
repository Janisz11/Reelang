package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class WordResponse(
    @SerializedName("id")         val id: String,
    @SerializedName("term")       val term: String,
    @SerializedName("definition") val definition: String?,
    @SerializedName("status")     val status: String?,
    @SerializedName("progress")   val progress: Float?,
    @SerializedName("language")   val language: String,
    @SerializedName("saved_at")   val savedAt: String?
)
