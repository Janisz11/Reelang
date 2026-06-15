package com.example.reelang.ui.words.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.model.UserSession
import com.example.reelang.data.local.LocalDataSource
import com.example.reelang.data.local.entities.PracticeSessionEntity
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ReviewRequest
import com.example.reelang.ui.words.model.PracticeCard
import com.example.reelang.ui.words.WordsEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PracticeViewModel : ViewModel() {

    private var localDataSource: LocalDataSource? = null

    fun setLocalDataSource(ds: LocalDataSource) {
        localDataSource = ds
    }

    private val _cards = MutableStateFlow<List<PracticeCard>>(emptyList())
    val cards: StateFlow<List<PracticeCard>> = _cards.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionComplete = MutableStateFlow(false)
    val sessionComplete: StateFlow<Boolean> = _sessionComplete.asStateFlow()

    private val _knownCount = MutableStateFlow(0)
    val knownCount: StateFlow<Int> = _knownCount.asStateFlow()

    private val _unknownCount = MutableStateFlow(0)
    val unknownCount: StateFlow<Int> = _unknownCount.asStateFlow()

    init {
        loadCards()
    }

    fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getWords()
            }.onSuccess { words ->
                _cards.value = words.map { word ->
                    PracticeCard(
                        wordId = word.id,
                        term = word.term,
                        language = word.language,
                        definition = word.definition,
                        translation = null
                    )
                }.shuffled()
            }.onFailure {
                _cards.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun markKnown() {
        val card = _cards.value.getOrNull(_currentIndex.value) ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.reviewWord(card.wordId, ReviewRequest(quality = 4))
            }.onSuccess {
                WordsEventBus.notifyWordSaved()
            }
        }
        _knownCount.value++
        nextCard()
    }

    fun markUnknown() {
        val card = _cards.value.getOrNull(_currentIndex.value) ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.reviewWord(card.wordId, ReviewRequest(quality = 1))
            }.onSuccess {
                WordsEventBus.notifyWordSaved()
            }
        }
        _unknownCount.value++
        nextCard()
    }

    private fun nextCard() {
        val next = _currentIndex.value + 1
        if (next >= _cards.value.size) {
            _sessionComplete.value = true
            localDataSource?.let { ds ->
                viewModelScope.launch {
                    ds.savePracticeSession(
                        PracticeSessionEntity(
                            userId = UserSession.userId,
                            knownCount = _knownCount.value,
                            unknownCount = _unknownCount.value,
                            totalCards = _cards.value.size
                        )
                    )
                }
            }
        } else {
            _currentIndex.value = next
        }
    }

    fun restart() {
        _currentIndex.value = 0
        _knownCount.value = 0
        _unknownCount.value = 0
        _sessionComplete.value = false
        _cards.value = _cards.value.shuffled()
    }
}