package com.example.reelang.ui

import kotlinx.coroutines.flow.MutableStateFlow

object SharedState {
    val profileRefreshTrigger = MutableStateFlow(0)

    fun triggerProfileRefresh() {
        profileRefreshTrigger.value++
    }
}
