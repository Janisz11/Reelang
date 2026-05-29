package com.example.reelang.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.UserSession
import com.example.reelang.data.local.LocalDataSource
import com.example.reelang.data.local.entities.UserProfileEntity
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ActivityStatsResponse
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.ui.SharedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val initialTargetUserId: String? = null) : ViewModel() {

    private var localDataSource: LocalDataSource? = null

    fun setLocalDataSource(ds: LocalDataSource) {
        localDataSource = ds
    }

    private val _profile = MutableStateFlow<ProfileResponse?>(null)
    val profile: StateFlow<ProfileResponse?> = _profile.asStateFlow()

    private val _stats = MutableStateFlow<ActivityStatsResponse?>(null)
    val stats: StateFlow<ActivityStatsResponse?> = _stats.asStateFlow()

    private val _userReels = MutableStateFlow<List<ReelResponse>>(emptyList())
    val userReels: StateFlow<List<ReelResponse>> = _userReels.asStateFlow()

    private val _savedReels = MutableStateFlow<List<ReelResponse>>(emptyList())
    val savedReels: StateFlow<List<ReelResponse>> = _savedReels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentUid: String? = null

    private val statsFallback = WeeklyStats(
        vocabularyMastered = "0",
        streakDays = 0,
        hoursWatched = 0,
        weeklyActivity = listOf(
            DayStat("Mon", 0f), DayStat("Tue", 0f), DayStat("Wed", 0f),
            DayStat("Thu", 0f), DayStat("Fri", 0f), DayStat("Sat", 0f), DayStat("Sun", 0f)
        ),
        targetLanguages = emptyList()
    )

    val statsComputed: WeeklyStats
        get() {
            val s = _stats.value
            Log.d("ProfileViewModel", "statsComputed called, _stats.value=$s")
            if (s == null) return statsFallback
            return WeeklyStats(
                vocabularyMastered = if (s.vocabularyMastered >= 1000)
                    "${s.vocabularyMastered / 1000}.${(s.vocabularyMastered % 1000) / 100}k"
                else s.vocabularyMastered.toString(),
                streakDays = s.streakDays,
                hoursWatched = s.hoursWatched.toInt(),
                weeklyActivity = s.weeklyActivity.map { DayStat(it.day, it.value) },
                targetLanguages = s.targetLanguages.map {
                    LanguageStat(it.flag, it.name, it.progress, it.percent)
                }
            )
        }

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { fa ->
        val newUid = fa.currentUser?.uid
        if (newUid != null && newUid != currentUid) {
            currentUid = newUid
            clearAndReload()
        } else if (newUid == null) {
            currentUid = null
            clearAll()
        }
    }

    init {
        if (initialTargetUserId != null) {
            loadForUser(initialTargetUserId)
        } else {
            currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authListener)
            loadProfile()
            loadStats()
            loadUserReels()
            loadSavedReels()
            viewModelScope.launch {
                SharedState.profileRefreshTrigger.collect {
                    if (it > 0) {
                        loadProfile()
                        loadSavedReels()
                        loadUserReels()
                    }
                }
            }
        }
    }

    private fun clearAll() {
        _profile.value = null
        _stats.value = null
        _userReels.value = emptyList()
        _savedReels.value = emptyList()
    }

    private fun clearAndReload() {
        clearAll()
        loadProfile()
        loadStats()
        loadUserReels()
        loadSavedReels()
    }

    override fun onCleared() {
        if (initialTargetUserId == null) {
            com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getMyProfile(UserSession.userId)
            }.onSuccess { profile ->
                _profile.value = profile
                localDataSource?.saveProfile(
                    UserProfileEntity(
                        userId = profile.userId,
                        username = profile.username,
                        avatarInitials = profile.avatarInitials,
                        followersCount = profile.followersCount,
                        followingCount = profile.followingCount,
                        totalLikes = profile.totalLikes,
                        level = profile.level,
                        streakDays = profile.streakDays
                    )
                )
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to load profile - trying Room cache", it)
                localDataSource?.getProfile(UserSession.userId)?.let { cached ->
                    _profile.value = ProfileResponse(
                        userId = cached.userId,
                        username = cached.username,
                        bio = null,
                        avatarInitials = cached.avatarInitials,
                        followersCount = cached.followersCount,
                        followingCount = cached.followingCount,
                        totalLikes = cached.totalLikes,
                        level = cached.level,
                        streakDays = cached.streakDays,
                        isFollowing = false
                    )
                }
            }
            _isLoading.value = false
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getMyStats(UserSession.userId)
            }.onSuccess {
                _stats.value = it
                Log.d("ProfileViewModel", "Stats loaded: streak=${it.streakDays}, vocab=${it.vocabularyMastered}")
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to load stats: ${it.message}", it)
            }
        }
    }

    fun loadUserReels() {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getUserReels(UserSession.userId)
            }.onSuccess {
                _userReels.value = it
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to load user reels", it)
            }
        }
    }

    fun loadSavedReels() {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getSavedReels(UserSession.userId)
            }.onSuccess {
                _savedReels.value = it
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to load saved reels", it)
            }
        }
    }

    fun followUser(targetUserId: String) {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.followUser(targetUserId, UserSession.userId)
            }.onSuccess { response ->
                _profile.value = _profile.value?.copy(
                    isFollowing = response.following,
                    followersCount = if (response.following)
                        (_profile.value?.followersCount ?: 0) + 1
                    else
                        maxOf(0, (_profile.value?.followersCount ?: 0) - 1)
                )
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to follow user", it)
            }
        }
    }

    fun deleteReel(reelId: String) {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.deleteReel(reelId, UserSession.userId)
            }.onSuccess {
                _userReels.value = _userReels.value.filter { it.id != reelId }
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to delete reel", it)
            }
        }
    }

    fun loadForUser(targetUserId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getProfile(targetUserId, UserSession.userId)
            }.onSuccess { _profile.value = it }
             .onFailure { Log.e("ProfileViewModel", "Failed to load profile for $targetUserId", it) }
            _isLoading.value = false
        }
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getUserReels(targetUserId)
            }.onSuccess { _userReels.value = it }
             .onFailure { Log.e("ProfileViewModel", "Failed to load reels for $targetUserId", it) }
        }
        _savedReels.value = emptyList()
        _stats.value = null
    }
}

class ProfileViewModelFactory(private val targetUserId: String) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ProfileViewModel(targetUserId) as T
}
