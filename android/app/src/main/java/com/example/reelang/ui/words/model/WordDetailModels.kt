package com.example.reelang.ui.words.model

data class Translation(val language: String, val text: String)

data class ContextQuote(
    val text: String,
    val highlightWord: String,
    val source: String
)

data class Variation(val label: String, val value: String)

data class WordDetail(
    val id: String,
    val term: String,
    val phonetic: String,
    val partOfSpeech: String,
    val definition: String,
    val translation: String,
    val translations: List<Translation>,
    val contextQuotes: List<ContextQuote>,
    val variations: List<Variation>,
    val language: String = ""
)
