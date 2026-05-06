package com.example.reelang.ui.reels

import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onWordClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

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
                if (reels.isNotEmpty()) {
                    val pagerState = rememberPagerState { reels.size }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val reel = reels[page]
                        ReelCard(
                            reel = reel,
                            onLike = { viewModel.toggleLike(reel.id) },
                            onSave = { viewModel.toggleSave(reel.id) },
                            onShare = {}
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
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
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
                    modifier = Modifier.fillMaxSize()
                )
            }
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
