package com.example.reelang.ui.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import com.example.reelang.network.models.CaptionSegment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.network.ApiClient
import com.example.reelang.ui.common.LocalDbSource
import com.example.reelang.ui.common.UiState
import com.example.reelang.ui.feed.FeedViewModel
import com.example.reelang.ui.feed.ReelActions
import com.example.reelang.ui.feed.ReelItem
import com.example.reelang.ui.feed.ReelTopBar
import com.example.reelang.ui.onboarding.ReelangRed

@Composable
fun ReelsScreen(
    viewModel: FeedViewModel = viewModel(),
    bottomPadding: Dp = 0.dp,
    onWordClick: (String) -> Unit = {},
    priorityReelIds: List<String> = emptyList()
) {
    val uiState by viewModel.uiState.collectAsState()
    val localDbSource = LocalDbSource.current
    LaunchedEffect(localDbSource) {
        localDbSource?.let { viewModel.setLocalDataSource(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = ReelangRed,
                    strokeWidth = 3.dp
                )
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.message,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { viewModel.loadReels() }) {
                        Text("Retry", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            is UiState.Success -> {
                val reels = state.data
                val sortedReels = if (priorityReelIds.isEmpty()) {
                    reels
                } else {
                    val prioritySet = priorityReelIds.toSet()
                    val priority = reels.filter { it.id in prioritySet }
                        .sortedBy { priorityReelIds.indexOf(it.id) }
                    val rest = reels.filter { it.id !in prioritySet }
                    priority + rest
                }
                if (sortedReels.isNotEmpty()) {
                    val pagerState = rememberPagerState { sortedReels.size }
                    val captionsMap by viewModel.captionsMap.collectAsState()
                    val currentStreak by viewModel.currentStreak.collectAsState()

                    val watchStartTime = remember { mutableStateOf(System.currentTimeMillis()) }
                    val reelsWatchedSet = remember { mutableStateOf(setOf<String>()) }

                    LaunchedEffect(pagerState.settledPage) {
                        val currentReel = sortedReels.getOrNull(pagerState.settledPage)
                        if (currentReel != null) {
                            viewModel.loadCaptions(currentReel.id)
                            reelsWatchedSet.value = reelsWatchedSet.value + currentReel.id
                        }
                        val prevReel = sortedReels.getOrNull(pagerState.settledPage - 1)
                        if (prevReel != null) {
                            viewModel.markConsumed(prevReel.id)
                        }
                    }

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(30_000)
                            val watchTimeMs = System.currentTimeMillis() - watchStartTime.value
                            viewModel.syncActivity(watchTimeMs, reelsWatchedSet.value.size)
                            watchStartTime.value = System.currentTimeMillis()
                            reelsWatchedSet.value = emptySet()
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            val watchTimeMs = System.currentTimeMillis() - watchStartTime.value
                            if (watchTimeMs > 1000) {
                                viewModel.syncActivityBlocking(watchTimeMs, reelsWatchedSet.value.size)
                            }
                        }
                    }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val reel = sortedReels[page]
                        ReelCard(
                            reel = reel.copy(streakDays = currentStreak),
                            captions = captionsMap[reel.id] ?: emptyList(),
                            isActive = pagerState.settledPage == page,
                            onLike = { viewModel.toggleLike(reel.id) },
                            onSave = { viewModel.toggleSave(reel.id) },
                            onShare = {},
                            onWordClick = { word -> viewModel.saveWord(word, reel.language, reel.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReelCard(
    reel: ReelItem,
    captions: List<CaptionSegment> = emptyList(),
    isActive: Boolean = false,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onWordClick: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(reel.bgColors))
    ) {
        val youtubeId = reel.youtubeId
        if (youtubeId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
            ) {
                YouTubeView(
                    youtubeId = youtubeId,
                    captions = captions,
                    isActive = isActive,
                    onWordClick = onWordClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            ImageOrVideoPlayer(
                streamUrl = "${ApiClient.BASE_URL}reels/${reel.id}/stream",
                isActive = isActive,
                captions = captions,
                onWordClick = onWordClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        ReelTopBar(reel = reel)

        ReelActions(
            reel = reel,
            onLike = onLike,
            onSave = onSave,
            onShare = onShare,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )
    }
}

@Composable
fun CaptionsOverlay(
    captions: List<CaptionSegment>,
    modifier: Modifier = Modifier
) {
    val caption = captions.firstOrNull() ?: return
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = caption.originalText ?: "",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
