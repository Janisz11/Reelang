package com.example.reelang.ui.words.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.WordLookupResponse
import com.example.reelang.network.models.WordResponse
import com.example.reelang.ui.common.UiState
import com.example.reelang.ui.words.model.Translation
import com.example.reelang.ui.words.model.WordDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WordDetailViewModel(private val wordId: String) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<WordDetail>>(UiState.Loading)
    val uiState: StateFlow<UiState<WordDetail>> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val wordResponse = ApiClient.api.getWordById(wordId)
                val lookup = try {
                    ApiClient.api.lookupWord(
                        term = wordResponse.term,
                        language = wordResponse.language,
                        targetLang = "en"
                    )
                } catch (e: Exception) { null }
                _uiState.value = UiState.Success(wordResponse.toWordDetail(lookup))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load word")
            }
        }
    }

    private fun WordResponse.toWordDetail(lookup: WordLookupResponse?) = WordDetail(
        id = id,
        term = term,
        phonetic = "",
        partOfSpeech = "",
        definition = lookup?.definition ?: definition ?: "",
        translation = lookup?.translation ?: "",
        translations = if (lookup?.translation != null)
            listOf(Translation("EN", lookup.translation))
        else emptyList(),
        contextQuotes = emptyList(),
        variations = emptyList(),
        language = language
    )
}

class WordDetailViewModelFactory(private val wordId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WordDetailViewModel(wordId) as T
}
