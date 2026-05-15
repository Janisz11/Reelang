package com.example.reelang.ui.profile

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.reelang.auth.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ActivityStatsResponse
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.ui.feed.bgColorsFor
import com.example.reelang.ui.feed.sceneEmojiFor
import com.example.reelang.ui.SharedState
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── Data Models ─────────────────────────────────────────────────────────────

data class PostThumbnail(
    val id: String,
    val color: Color,
    val emoji: String,
    val thumbnailUrl: String? = null,
    val reelId: String? = null
)

data class DayStat(val day: String, val value: Float)

data class LanguageStat(
    val flag: String,
    val name: String,
    val progress: Float,
    val percent: Int
)

data class WeeklyStats(
    val vocabularyMastered: String,
    val streakDays: Int,
    val hoursWatched: Int,
    val weeklyActivity: List<DayStat>,
    val targetLanguages: List<LanguageStat>
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ProfileViewModel : ViewModel() {

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

    init {
        loadProfile()
        loadStats()
        loadUserReels()
        loadSavedReels()
        viewModelScope.launch {
            SharedState.profileRefreshTrigger.collect {
                if (it > 0) {
                    loadProfile()
                    loadSavedReels()
                }
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getMyProfile(UserSession.userId)
            }.onSuccess {
                _profile.value = it
            }.onFailure {
                Log.e("ProfileViewModel", "Failed to load profile", it)
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
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val profileTabs = listOf("Posts", "Saved")

@Composable
fun ProfileScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val userReels by viewModel.userReels.collectAsState()
    val savedReels by viewModel.savedReels.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val userReelThumbnails = userReels.map { reel ->
        PostThumbnail(
            id = reel.id,
            color = bgColorsFor(reel.language).first(),
            emoji = sceneEmojiFor(reel.language),
            thumbnailUrl = when {
                reel.thumbnailUrl == null -> null
                reel.thumbnailUrl.startsWith("http") -> reel.thumbnailUrl
                else -> "${ApiClient.BASE_URL}${reel.thumbnailUrl.trimStart('/')}"
            },
            reelId = reel.id
        )
    }
    val savedPostThumbnails = savedReels.map { reel ->
        PostThumbnail(
            id = reel.id,
            color = bgColorsFor(reel.language).first(),
            emoji = sceneEmojiFor(reel.language),
            thumbnailUrl = when {
                reel.thumbnailUrl == null -> null
                reel.thumbnailUrl.startsWith("http") -> reel.thumbnailUrl
                else -> "${ApiClient.BASE_URL}${reel.thumbnailUrl.trimStart('/')}"
            },
            reelId = reel.id
        )
    }
    val gridItems = if (selectedTab == 0) userReelThumbnails else savedPostThumbnails

    Scaffold(
        modifier = modifier,
        containerColor = ReelangCream,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create_reel") },
                containerColor = ReelangRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Create",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                ProfileHeader(
                    name = profile?.username ?: UserSession.displayName,
                    subtitle = "LVL ${profile?.level ?: 1}",
                    avatarInitials = profile?.avatarInitials ?: UserSession.initials(),
                    avatarColor = ReelangRed
                )
            }

            item {
                StatsRow(
                    followers = profile?.followersCount?.toString() ?: "0",
                    following = profile?.followingCount?.toString() ?: "0",
                    likes = profile?.totalLikes?.toString() ?: "0"
                )
                HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
            }

            item {
                LearningStatsCard(onClick = onNavigateToStats)
                Spacer(Modifier.height(2.dp))
            }

            item {
                ProfileTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            item {
                ThumbnailGrid(
                    items = gridItems,
                    onReelClick = { reelId ->
                        if (selectedTab == 0) {
                            val allIds = gridItems.mapNotNull { it.reelId }
                            val orderedIds = listOf(reelId) + allIds.filter { it != reelId }
                            navController.navigate("feed_from_search/${orderedIds.joinToString(",")}")
                        } else {
                            navController.navigate("saved_reel/$reelId")
                        }
                    }
                )
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    name: String,
    subtitle: String,
    avatarInitials: String,
    avatarColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarInitials,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = name,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ReelangTextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReelangTextSecondary,
            letterSpacing = 0.6.sp
        )
    }
}

// ─── Stats Row ────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(followers: String, following: String, likes: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(value = followers, label = "Followers")
        StatDivider()
        StatCell(value = following, label = "Following")
        StatDivider()
        StatCell(value = likes, label = "Likes")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ReelangTextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = ReelangTextSecondary
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(1.dp)
            .background(ReelangBorder)
    )
}

// ─── Learning Stats Card ──────────────────────────────────────────────────────

@Composable
private fun LearningStatsCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = ReelangSurface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFFFF0F0), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = ReelangRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detailed Learning Stats",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReelangTextPrimary
                )
                Text(
                    text = "Vocabulary, streaks, activity",
                    fontSize = 12.sp,
                    color = ReelangTextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open stats",
                tint = ReelangTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ─── Tab Row ─────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = ReelangSurface,
        contentColor = ReelangRed,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = ReelangRed
            )
        },
        divider = {
            HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
        }
    ) {
        profileTabs.forEachIndexed { index, title ->
            val selected = selectedTab == index
            val labelColor by animateColorAsState(
                targetValue = if (selected) ReelangRed else ReelangTextSecondary,
                animationSpec = tween(200),
                label = "profile_tab_$index"
            )
            Tab(
                selected = selected,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelColor
                    )
                }
            )
        }
    }
}

// ─── Thumbnail Grid ───────────────────────────────────────────────────────────

@Composable
private fun ThumbnailGrid(
    items: List<PostThumbnail>,
    onReelClick: (String) -> Unit = {}
) {
    val rows = items.chunked(3)
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                row.forEach { thumb ->
                    ThumbnailCell(
                        thumb = thumb,
                        onClick = { onReelClick(thumb.reelId ?: thumb.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ThumbnailCell(
    thumb: PostThumbnail,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(thumb.color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumb.thumbnailUrl != null) {
            AsyncImage(
                model = thumb.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
        } else {
            Text(
                text = thumb.emoji,
                fontSize = 30.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
