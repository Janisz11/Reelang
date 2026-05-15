package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class LikeResponse(
    @SerializedName("liked") val liked: Boolean,
    @SerializedName("likes_count") val likesCount: Int
)
