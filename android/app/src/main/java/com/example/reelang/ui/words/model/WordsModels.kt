package com.example.reelang.ui.words.model

enum class WordStatus { LEARNING, MASTERED }

data class Word(
    val id: String,
    val term: String,
    val definition: String,
    val status: WordStatus,
    val progress: Float,
    val language: String
)
