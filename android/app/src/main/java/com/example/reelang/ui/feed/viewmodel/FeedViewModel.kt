package com.example.reelang.ui.feed.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.model.UserSession
import com.example.reelang.data.local.LocalDataSource
import com.example.reelang.data.local.entities.ReelEntity
import com.example.reelang.events.EventTracker
import com.example.reelang.events.EventTypes
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ActivityLogRequest
import com.example.reelang.network.models.CaptionSegment
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.network.models.SaveWordRequest
import com.example.reelang.ui.SharedState
import com.example.reelang.ui.common.UiState
import com.example.reelang.ui.feed.model.ReelItem
import com.example.reelang.ui.feed.model.bgColorsFor
import com.example.reelang.ui.feed.model.sceneEmojiFor
import com.example.reelang.ui.words.WordsEventBus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException

class FeedViewModel(private val autoLoad: Boolean = true) : ViewModel() {

    private var localDataSource: LocalDataSource? = null

    fun setLocalDataSource(ds: LocalDataSource) {
        localDataSource = ds
    }

    private val _uiState = MutableStateFlow<UiState<List<ReelItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ReelItem>>> = _uiState.asStateFlow()

    private val _captionsMap = MutableStateFlow<Map<String, List<CaptionSegment>>>(emptyMap())
    val captionsMap: StateFlow<Map<String, List<CaptionSegment>>> = _captionsMap.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private val _ownerProfiles = MutableStateFlow<Map<String, ProfileResponse>>(emptyMap())
    val ownerProfiles: StateFlow<Map<String, ProfileResponse>> = _ownerProfiles.asStateFlow()

    private var currentUid: String? = null

    private val authListener = FirebaseAuth.AuthStateListener { fa ->
        val newUid = fa.currentUser?.uid
        if (newUid != null && newUid != currentUid) {
            currentUid = newUid
            _captionsMap.value = emptyMap()
            _savedTerms.clear()
            if (autoLoad) {
                loadReels()
                loadStreak()
                triggerAgentRefill()
            }
        } else if (newUid == null) {
            currentUid = null
            _uiState.value = UiState.Loading
            _captionsMap.value = emptyMap()
            _savedTerms.clear()
        }
    }

    init {
        currentUid = FirebaseAuth.getInstance().currentUser?.uid
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        if (autoLoad) {
            loadReels()
            loadStreak()
            triggerAgentRefill()
        }
    }

    fun loadUserReels(userId: String, startReelId: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getUserReels(userId)
            }.onSuccess { response ->
                val reels = response.map { it.toReelItem() }
                val sorted = reels.sortedBy { if (it.id == startReelId) 0 else 1 }
                _uiState.value = UiState.Success(sorted)
            }.onFailure {
                _uiState.value = UiState.Error(it.message ?: "Failed to load reels")
            }
        }
    }

    fun loadSingleReel(reelId: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getReelById(reelId)
            }.onSuccess { reel ->
                _uiState.value = UiState.Success(listOf(reel.toReelItem()))
            }.onFailure {
                Log.e("FeedViewModel", "Failed to load single reel", it)
                _uiState.value = UiState.Error("Failed to load reel")
            }
        }
    }

    fun loadReels() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getFeed(userId = UserSession.userId, limit = 20)
                Log.d("FeedViewModel", "Loaded ${response.size} reel(s): ${response.map { it.id }}")
                Log.d("FeedViewModel", "youtubeIds: ${response.map { it.youtubeId }}")
                val reels = response.map { it.toReelItem() }
                _uiState.value = UiState.Success(reels)
                localDataSource?.let { ds ->
                    val entities = response.map { reel ->
                        ReelEntity(
                            id = reel.id,
                            youtubeId = reel.youtubeId,
                            title = reel.title ?: "",
                            channelName = reel.channelName,
                            thumbnailUrl = reel.thumbnailUrl,
                            language = reel.language,
                            level = reel.level,
                            tags = reel.tags,
                            durationMs = reel.durationMs,
                            likesCount = reel.likesCount,
                            isLiked = reel.isLiked
                        )
                    }
                    ds.cacheReels(entities)
                    Log.d("FeedViewModel", "Cached ${entities.size} reels to Room")
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "loadReels failed", e)
                val cached = localDataSource?.getCachedReels()?.firstOrNull()
                if (!cached.isNullOrEmpty()) {
                    Log.d("FeedViewModel", "Loading ${cached.size} reels from Room cache")
                    val reels = cached.map { it.toReelItem() }
                    _uiState.value = UiState.Success(reels)
                } else {
                    Log.d("FeedViewModel", "No cache available")
                    _uiState.value = UiState.Error(e.message ?: "Failed to load reels")
                }
            }
        }
    }

    fun loadCaptions(reelId: String) {
        val cached = _captionsMap.value[reelId]
        if (cached != null && cached.isNotEmpty()) return
        loadCaptionsWithRetry(reelId, retryCount = 0)
    }

    private fun loadCaptionsWithRetry(reelId: String, retryCount: Int) {
        viewModelScope.launch {
            runCatching { ApiClient.api.getCaptions(reelId) }
                .onSuccess { segments ->
                    if (segments.isNotEmpty()) {
                        _captionsMap.update { current -> current + (reelId to segments) }
                    } else if (retryCount < 5) {
                        delay(3000)
                        loadCaptionsWithRetry(reelId, retryCount + 1)
                    }
                }
        }
    }

    fun toggleLike(id: String) {
        val list = (_uiState.value as? UiState.Success)?.data ?: return
        val originalReel = list.find { it.id == id } ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.toggleLike(id, UserSession.userId)
            }.onSuccess { response ->
                _uiState.value = UiState.Success(applyLikeSuccess(list, id, response.liked, response.likesCount))
                EventTracker.track(
                    if (response.liked) EventTypes.LIKE else EventTypes.UNLIKE,
                    id
                )
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("FeedViewModel", "Failed to toggle like", it)
                _uiState.value = UiState.Success(applyLikeFailure(list, id, originalReel.isLiked, originalReel.likes))
            }
        }
    }

    private val _savedTerms = mutableSetOf<String>()

    fun saveWord(term: String, language: String, reelId: String) {
        val key = "${term.lowercase()}_${language}"
        if (_savedTerms.contains(key)) {
            Log.d("FeedViewModel", "Word already saved, skipping: $term")
            return
        }
        _savedTerms.add(key)
        viewModelScope.launch {
            runCatching {
                ApiClient.api.saveWord(
                    SaveWordRequest(
                        term = term,
                        definition = "",
                        language = language,
                        reelId = reelId
                    )
                )
            }.onSuccess {
                Log.d("FeedViewModel", "Saved word: $term")
                WordsEventBus.notifyWordSaved()
            }.onFailure {
                _savedTerms.remove(key)
                Log.e("FeedViewModel", "Failed to save word: $term", it)
            }
        }
    }

    fun triggerAgentRefill() {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.triggerAgentRefill(UserSession.userId)
            }.onSuccess {
                Log.d("FeedViewModel", "Agent refill triggered")
                delay(3000)
                loadReels()
            }.onFailure {
                Log.w("FeedViewModel", "Agent refill trigger failed (non-critical)")
            }
        }
    }

    fun markConsumed(reelId: String) {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.markConsumed(reelId, UserSession.userId)
            }.onFailure {
                Log.w("FeedViewModel", "Failed to mark consumed: $reelId")
            }
        }
    }

    fun toggleSave(id: String) {
        val list = (_uiState.value as? UiState.Success)?.data ?: return
        val originalReel = list.find { it.id == id } ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.toggleSave(id, UserSession.userId)
            }.onSuccess { response ->
                _uiState.value = UiState.Success(applySaveSuccess(list, id, response.saved, response.savesCount))
                EventTracker.track(
                    if (response.saved) EventTypes.SAVE else EventTypes.UNSAVE,
                    id
                )
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("FeedViewModel", "Failed to toggle save", it)
                _uiState.value = UiState.Success(applySaveFailure(list, id, originalReel.isSaved, originalReel.saves))
            }
        }
    }

    fun loadOwnerUsername(ownerId: String) {
        if (_ownerProfiles.value.containsKey(ownerId)) return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getProfile(ownerId, UserSession.userId)
            }.onSuccess { profile ->
                _ownerProfiles.value = _ownerProfiles.value + (ownerId to profile)
            }.onFailure {
                Log.w("FeedViewModel", "Failed to load owner profile for $ownerId")
            }
        }
    }

    fun loadStreak() {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.getMyStats(UserSession.userId)
            }.onSuccess {
                _currentStreak.value = it.streakDays
            }
        }
    }

    fun syncActivity(watchTimeMs: Long, reelsWatched: Int) {
        viewModelScope.launch {
            runCatching {
                ApiClient.api.logActivity(
                    userId = UserSession.userId,
                    body = ActivityLogRequest(
                        watchTimeMs = watchTimeMs,
                        reelsWatched = reelsWatched,
                        wordsSaved = 0
                    )
                )
            }.onFailure { e ->
                if (e !is UnknownHostException &&
                    e !is ConnectException) {
                    Log.e("FeedViewModel", "Failed to sync activity: ${e.message}", e)
                }
            }
        }
    }

    fun syncActivityBlocking(watchTimeMs: Long, reelsWatched: Int) {
        GlobalScope.launch {
            runCatching {
                ApiClient.api.logActivity(
                    userId = UserSession.userId,
                    body = ActivityLogRequest(
                        watchTimeMs = watchTimeMs,
                        reelsWatched = reelsWatched,
                        wordsSaved = 0
                    )
                )
            }
        }
    }

    override fun onCleared() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
    }

    private fun ReelEntity.toReelItem() = ReelItem(
        id = id,
        channelName = channelName ?: "",
        avatarEmoji = sceneEmojiFor(language),
        originalText = "",
        translatedText = "",
        clickableWord = "",
        likes = likesCount,
        saves = 0,
        level = level ?: "",
        streakDays = 0,
        language = language,
        bgColors = bgColorsFor(language),
        sceneEmoji = sceneEmojiFor(language),
        youtubeId = youtubeId,
        isLiked = isLiked
    )

    private fun ReelResponse.toReelItem() = ReelItem(
        id = id,
        channelName = channelName ?: "",
        avatarEmoji = avatarEmoji,
        originalText = originalText,
        translatedText = translatedText,
        clickableWord = clickableWord,
        likes = likesCount,
        saves = saves,
        level = level ?: "",
        streakDays = streakDays,
        language = language,
        bgColors = bgColorsFor(language),
        sceneEmoji = sceneEmojiFor(language),
        youtubeId = youtubeId,
        isLiked = isLiked,
        ownerId = ownerUserId,
        ownerUsername = ownerUsername,
        ownerAvatarInitials = ownerAvatarInitials
    )
}

internal fun applyLikeSuccess(list: List<ReelItem>, id: String, liked: Boolean, likesCount: Int): List<ReelItem> =
    list.map { if (it.id == id) it.copy(isLiked = liked, likes = likesCount) else it }

internal fun applySaveSuccess(list: List<ReelItem>, id: String, saved: Boolean, savesCount: Int): List<ReelItem> =
    list.map { if (it.id == id) it.copy(isSaved = saved, saves = savesCount) else it }

internal fun applyLikeFailure(reels: List<ReelItem>, id: String, originalLiked: Boolean, originalLikes: Int): List<ReelItem> =
    reels.map { if (it.id == id) it.copy(isLiked = originalLiked, likes = originalLikes) else it }

internal fun applySaveFailure(reels: List<ReelItem>, id: String, originalSaved: Boolean, originalSaves: Int): List<ReelItem> =
    reels.map { if (it.id == id) it.copy(isSaved = originalSaved, saves = originalSaves) else it }