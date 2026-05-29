package com.example.reelang.ui.feed

import androidx.compose.ui.graphics.Color

data class ReelItem(
    val id: String,
    val channelName: String = "",
    val avatarEmoji: String,
    val originalText: String,
    val translatedText: String,
    val clickableWord: String,
    val likes: Int,
    val saves: Int,
    val level: String = "",
    val streakDays: Int,
    val language: String,
    val bgColors: List<Color>,
    val sceneEmoji: String,
    val youtubeId: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val ownerId: String? = null,
    val ownerUsername: String? = null,
    val ownerAvatarInitials: String? = null
)

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000     -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else           -> n.toString()
}

fun levelColor(level: String) = when (level) {
    "A1", "A2" -> Color(0xFF43A047)
    "B1", "B2" -> Color(0xFF1E88E5)
    "C1", "C2" -> Color(0xFF8E24AA)
    else        -> Color(0xFF757575)
}

fun bgColorsFor(language: String): List<Color> = when (language.lowercase()) {
    "es" -> listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
    "fr" -> listOf(Color(0xFF2D1B1B), Color(0xFF4A1A00))
    "ja" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
    "de" -> listOf(Color(0xFF1A0A2E), Color(0xFF2D1B4E))
    "it" -> listOf(Color(0xFF1A1500), Color(0xFF3D2B00))
    else -> listOf(Color(0xFF1A1A1A), Color(0xFF2D2D2D))
}

fun sceneEmojiFor(language: String): String = when (language.lowercase()) {
    "es" -> "☀️"
    "fr" -> "🥐"
    "ja" -> "🚉"
    "de" -> "🎵"
    "it" -> "🏛️"
    else -> "🌍"
}
