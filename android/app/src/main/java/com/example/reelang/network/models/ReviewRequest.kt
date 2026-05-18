package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class ReviewRequest(
    @SerializedName("quality") val quality: Int
)
