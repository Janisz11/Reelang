package com.example.reelang.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ActivityLogRequest
import com.example.reelang.network.models.CaptionSegment
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.network.models.SaveWordRequest
import com.example.reelang.ui.words.WordsEventBus
import com.example.reelang.ui.common.UiState
import com.example.reelang.ui.SharedState
import com.example.reelang.ui.onboarding.ReelangRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Data ─────────────────────────────────────────────────────────────────────

data class ReelItem(
    val id: String,
    val channelName: String = "",
    val avatarEmoji: String,
    val originalText: String,
    val translatedText: String,
    val clickableWord: String,
    val likes: Int,
    val saves: Int,
    val level: String = "",
    val streakDays: Int,
    val language: String,
    val bgColors: List<Color>,
    val sceneEmoji: String,
    val youtubeId: String? = null,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false
)

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000     -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else           -> n.toString()
}

private fun levelColor(level: String) = when (level) {
    "A1", "A2" -> Color(0xFF43A047)
    "B1", "B2" -> Color(0xFF1E88E5)
    "C1", "C2" -> Color(0xFF8E24AA)
    else        -> Color(0xFF757575)
}

internal fun bgColorsFor(language: String): List<Color> = when (language.lowercase()) {
    "es" -> listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
    "fr" -> listOf(Color(0xFF2D1B1B), Color(0xFF4A1A00))
    "ja" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
    "de" -> listOf(Color(0xFF1A0A2E), Color(0xFF2D1B4E))
    "it" -> listOf(Color(0xFF1A1500), Color(0xFF3D2B00))
    else -> listOf(Color(0xFF1A1A1A), Color(0xFF2D2D2D))
}

internal fun sceneEmojiFor(language: String): String = when (language.lowercase()) {
    "es" -> "☀️"
    "fr" -> "🥐"
    "ja" -> "🚉"
    "de" -> "🎵"
    "it" -> "🏛️"
    else -> "🌍"
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class FeedViewModel(private val autoLoad: Boolean = true) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<ReelItem>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ReelItem>>> = _uiState.asStateFlow()

    private val _captionsMap = MutableStateFlow<Map<String, List<CaptionSegment>>>(emptyMap())
    val captionsMap: StateFlow<Map<String, List<CaptionSegment>>> = _captionsMap.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    private var currentUid: String? = null

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { fa ->
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
        currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authListener)
        if (autoLoad) {
            loadReels()
            loadStreak()
            triggerAgentRefill()
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
                val response = ApiClient.api.getFeed(userId = UserSession.userId)
                Log.d("FeedViewModel", "Loaded ${response.size} reel(s): ${response.map { it.id }}")
                Log.d("FeedViewModel", "youtubeIds: ${response.map { it.youtubeId }}")
                val reels = response.map { it.toReelItem() }
                _uiState.value = UiState.Success(reels)
            } catch (e: Exception) {
                Log.e("FeedViewModel", "loadReels failed", e)
                _uiState.value = UiState.Error(e.message ?: "Failed to load reels")
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
        viewModelScope.launch {
            runCatching {
                ApiClient.api.toggleLike(id, UserSession.userId)
            }.onSuccess { response ->
                _uiState.value = UiState.Success(list.map { reel ->
                    if (reel.id == id) reel.copy(
                        isLiked = response.liked,
                        likes = response.likesCount
                    ) else reel
                })
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("FeedViewModel", "Failed to toggle like", it)
                _uiState.value = UiState.Success(list.map { reel ->
                    if (reel.id == id) reel.copy(
                        isLiked = !reel.isLiked,
                        likes = if (reel.isLiked) reel.likes - 1 else reel.likes + 1
                    ) else reel
                })
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
        viewModelScope.launch {
            runCatching {
                ApiClient.api.toggleSave(id, UserSession.userId)
            }.onSuccess { response ->
                _uiState.value = UiState.Success(list.map { reel ->
                    if (reel.id == id) reel.copy(
                        isSaved = response.saved,
                        saves = if (response.saved) reel.saves + 1 else reel.saves - 1
                    ) else reel
                })
                SharedState.triggerProfileRefresh()
            }.onFailure {
                Log.e("FeedViewModel", "Failed to toggle save", it)
                _uiState.value = UiState.Success(list.map { reel ->
                    if (reel.id == id) reel.copy(
                        isSaved = !reel.isSaved,
                        saves = if (reel.isSaved) reel.saves - 1 else reel.saves + 1
                    ) else reel
                })
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
            }.onFailure {
                Log.e("FeedViewModel", "Failed to sync activity", it)
            }
        }
    }

    fun syncActivityBlocking(watchTimeMs: Long, reelsWatched: Int) {
        kotlinx.coroutines.GlobalScope.launch {
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
        com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(authListener)
    }

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
        isLiked = isLiked
    )
}

class FeedViewModelFactory(private val autoLoad: Boolean) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        FeedViewModel(autoLoad) as T
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun ReelTopBar(reel: ReelItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = reel.avatarEmoji, fontSize = 18.sp)
            }
            Text(
                text = reel.channelName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor(reel.level))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = reel.level,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE65100).copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "🔥", fontSize = 12.sp)
            Column {
                Text(
                    text = "${reel.streakDays} DAY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 12.sp
                )
                Text(
                    text = "STREAK",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 10.sp
                )
            }
        }
    }
}

// ─── Action Buttons ───────────────────────────────────────────────────────────

@Composable
fun ReelActions(
    reel: ReelItem,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likeColor by animateColorAsState(
        targetValue = if (reel.isLiked) ReelangRed else Color.White,
        animationSpec = tween(200), label = "likeColor"
    )
    val saveColor by animateColorAsState(
        targetValue = if (reel.isSaved) Color(0xFFFFD700) else Color.White,
        animationSpec = tween(200), label = "saveColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        FeedActionButton(
            icon = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            count = formatCount(reel.likes),
            tint = likeColor,
            onClick = onLike
        )
        FeedActionButton(
            icon = if (reel.isSaved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
            count = formatCount(reel.saves),
            tint = saveColor,
            onClick = onSave
        )
        FeedActionButton(
            icon = Icons.Outlined.Share,
            count = null,
            tint = Color.White,
            onClick = onShare
        )
    }
}

@Composable
fun FeedActionButton(
    icon: ImageVector,
    count: String?,
    tint: Color,
    onClick: () -> Unit
) {
    var bounced by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bounced) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { bounced = false },
        label = "btnScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                bounced = true
                onClick()
            }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        if (count != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = count, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

// ─── Subtitle / Caption overlay ───────────────────────────────────────────────

@Composable
fun ClickableSubtitle(
    text: String,
    clickableWord: String,
    onWordClick: (String) -> Unit
) {
    val annotatedText = remember(text, clickableWord) {
        val idx = text.lowercase().indexOf(clickableWord.lowercase())
        buildAnnotatedString {
            val base = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            val highlight = SpanStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFD700),
                textDecoration = TextDecoration.Underline
            )
            if (idx < 0) {
                withStyle(base) { append(text) }
            } else {
                withStyle(base) { append(text.substring(0, idx)) }
                pushStringAnnotation("word", text.substring(idx, idx + clickableWord.length))
                withStyle(highlight) { append(text.substring(idx, idx + clickableWord.length)) }
                pop()
                withStyle(base) { append(text.substring(idx + clickableWord.length)) }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedText,
        lineHeight = 28.sp,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(onWordClick) {
            detectTapGestures { offset ->
                layoutResult?.let { lr ->
                    val pos = lr.getOffsetForPosition(offset)
                    annotatedText
                        .getStringAnnotations("word", pos, pos)
                        .firstOrNull()
                        ?.let { onWordClick(it.item) }
                }
            }
        }
    )
}

