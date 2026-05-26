package com.example.reelang.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val avatarInitials: String?,
    val followersCount: Int,
    val followingCount: Int,
    val totalLikes: Int,
    val level: Int,
    val streakDays: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
