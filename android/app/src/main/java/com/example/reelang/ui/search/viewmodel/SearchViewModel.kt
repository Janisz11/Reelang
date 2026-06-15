package com.example.reelang.ui.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.model.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ReelResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _reels = MutableStateFlow<List<ReelResponse>>(emptyList())
    val reels: StateFlow<List<ReelResponse>> = _reels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _profiles = MutableStateFlow<List<ProfileResponse>>(emptyList())
    val profiles: StateFlow<List<ProfileResponse>> = _profiles.asStateFlow()

    private val _profilesLoading = MutableStateFlow(false)
    val profilesLoading: StateFlow<Boolean> = _profilesLoading.asStateFlow()

    fun search(language: String? = null, level: String? = null, tags: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getReels(
                    language = language?.takeIf { it.isNotEmpty() },
                    level = level?.takeIf { it.isNotEmpty() },
                    topic = null,
                    tags = tags?.takeIf { it.isNotEmpty() }
                )
            }.onSuccess { _reels.value = it }
            _isLoading.value = false
        }
    }

    fun searchProfiles(query: String) {
        if (query.isBlank()) {
            _profiles.value = emptyList()
            return
        }
        viewModelScope.launch {
            _profilesLoading.value = true
            runCatching {
                ApiClient.api.searchProfiles(query, UserSession.userId)
            }.onSuccess { _profiles.value = it }
            _profilesLoading.value = false
        }
    }
}