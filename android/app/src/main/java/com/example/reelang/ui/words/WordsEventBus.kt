package com.example.reelang.ui.words

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WordsEventBus {
    private val _wordSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wordSaved = _wordSaved.asSharedFlow()

    fun notifyWordSaved() {
        _wordSaved.tryEmit(Unit)
    }
}
