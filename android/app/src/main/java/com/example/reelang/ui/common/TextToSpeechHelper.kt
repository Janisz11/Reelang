package com.example.reelang.ui.common

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

class TextToSpeechHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (!isReady) {
                Log.e("TTS", "Initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String, language: String) {
        if (!isReady) return
        val locale = languageToLocale(language)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("TTS", "Language $language not supported, falling back to English")
            tts?.setLanguage(Locale.ENGLISH)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word_${text.hashCode()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun languageToLocale(language: String): Locale = when (language.lowercase()) {
        "es" -> Locale("es", "ES")
        "fr" -> Locale("fr", "FR")
        "de" -> Locale("de", "DE")
        "ja" -> Locale("ja", "JP")
        "it" -> Locale("it", "IT")
        "pt" -> Locale("pt", "PT")
        "pl" -> Locale("pl", "PL")
        "en" -> Locale.ENGLISH
        else -> Locale.ENGLISH
    }
}

val LocalTTS = compositionLocalOf<TextToSpeechHelper?> { null }
