package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class SaveWordRequest(
    @SerializedName("term")       val term: String,
    @SerializedName("definition") val definition: String,
    @SerializedName("language")   val language: String,
    @SerializedName("reel_id")    val reelId: String? = null
)
