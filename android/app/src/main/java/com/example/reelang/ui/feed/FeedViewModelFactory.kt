package com.example.reelang.ui.feed

class FeedViewModelFactory(private val autoLoad: Boolean) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        FeedViewModel(autoLoad) as T
}
