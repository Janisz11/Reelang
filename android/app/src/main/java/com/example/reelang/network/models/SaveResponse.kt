package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class SaveResponse(
    @SerializedName("saved") val saved: Boolean,
    @SerializedName("saves_count") val savesCount: Int
)
