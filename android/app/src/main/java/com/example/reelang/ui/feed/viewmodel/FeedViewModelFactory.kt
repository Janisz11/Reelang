package com.example.reelang.ui.feed.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class FeedViewModelFactory(private val autoLoad: Boolean) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FeedViewModel(autoLoad) as T
}