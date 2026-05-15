package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class SaveResponse(
    @SerializedName("saved") val saved: Boolean
)
