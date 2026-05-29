package com.example.reelang.ui.words

data class PracticeCard(
    val wordId: String,
    val term: String,
    val language: String,
    val definition: String?,
    val translation: String?
)
