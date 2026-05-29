package com.example.reelang.ui.words.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.data.local.LocalDataSource
import com.example.reelang.data.local.entities.WordEntity
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.WordResponse
import com.example.reelang.ui.common.UiState
import com.example.reelang.ui.words.WordsEventBus
import com.example.reelang.ui.words.model.Word
import com.example.reelang.ui.words.model.WordStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WordsViewModel : ViewModel() {

    private var localDataSource: LocalDataSource? = null

    fun setLocalDataSource(ds: LocalDataSource) {
        localDataSource = ds
    }

    private val _uiState = MutableStateFlow<UiState<List<Word>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Word>>> = _uiState.asStateFlow()

    private var currentUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener { fa ->
        val newUid = fa.currentUser?.uid
        if (newUid != null && newUid != currentUid) {
            currentUid = newUid
            loadWords()
        } else if (newUid == null) {
            currentUid = null
            _uiState.value = UiState.Loading
        }
    }

    init {
        currentUid = FirebaseAuth.getInstance().currentUser?.uid
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        loadWords()
        viewModelScope.launch {
            WordsEventBus.wordSaved.collect { loadWords() }
        }
    }

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
    }

    fun loadWords() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val words = ApiClient.api.getWords().map { it.toWord() }
                _uiState.value = UiState.Success(words)
                localDataSource?.let { ds ->
                    val entities = words.map { word ->
                        WordEntity(
                            id = word.id,
                            term = word.term,
                            definition = word.definition,
                            translation = null,
                            language = word.language,
                            status = word.status.name.lowercase(),
                            reelId = null,
                            createdAt = System.currentTimeMillis().toString()
                        )
                    }
                    ds.saveWords(entities)
                    Log.d("WordsViewModel", "Cached ${entities.size} words to Room")
                }
            } catch (e: Exception) {
                Log.e("WordsViewModel", "loadWords failed - trying Room cache", e)
                localDataSource?.getAllWords()?.firstOrNull()?.let { cached ->
                    if (cached.isNotEmpty()) {
                        Log.d("WordsViewModel", "Loading ${cached.size} words from Room")
                        val words = cached.map { entity ->
                            Word(
                                id = entity.id,
                                term = entity.term,
                                definition = entity.definition ?: "",
                                status = if (entity.status == "mastered")
                                    WordStatus.MASTERED else WordStatus.LEARNING,
                                progress = 0f,
                                language = entity.language
                            )
                        }
                        _uiState.value = UiState.Success(words)
                    } else {
                        _uiState.value = UiState.Error("No internet connection")
                    }
                } ?: run {
                    _uiState.value = UiState.Error("No internet connection")
                }
            }
        }
    }

    fun deleteWord(wordId: String) {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.deleteWord(wordId)
            }.onSuccess {
                loadWords()
            }.onFailure {
                Log.e("WordsViewModel", "Failed to delete word", it)
            }
        }
    }

    private fun WordResponse.toWord() = Word(
        id = id,
        term = term,
        definition = definition ?: "",
        status = when (status?.lowercase()) {
            "mastered" -> WordStatus.MASTERED
            else -> WordStatus.LEARNING
        },
        progress = progress ?: 0f,
        language = language
    )
}