package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class ProfileResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String,
    @SerializedName("bio") val bio: String?,
    @SerializedName("avatar_initials") val avatarInitials: String?,
    @SerializedName("followers_count") val followersCount: Int,
    @SerializedName("following_count") val followingCount: Int,
    @SerializedName("total_likes") val totalLikes: Int,
    @SerializedName("level") val level: Int,
    @SerializedName("streak_days") val streakDays: Int,
    @SerializedName("is_following") val isFollowing: Boolean = false
)
