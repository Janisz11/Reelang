package com.example.reelang.ui.reels.viewmodel

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.reelang.events.EventTracker
import com.example.reelang.events.PROGRESS_INTERVAL_MS
import com.example.reelang.events.ReelPlaybackTracker
import com.example.reelang.network.models.CaptionSegment
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun LocalVideoPlayer(
    streamUrl: String,
    isActive: Boolean,
    captions: List<CaptionSegment> = emptyList(),
    onWordClick: (String) -> Unit = {},
    reelId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        val okHttpDataSource = OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        )
        val dataSourceFactory = DefaultDataSource.Factory(
            context, okHttpDataSource
        )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                prepare()
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                playWhenReady = false
            }
    }

    var currentTimeMs by remember { mutableStateOf(0L) }
    var isPaused by remember { mutableStateOf(false) }
    var showPauseIcon by remember { mutableStateOf(false) }

    val playbackTracker = remember(reelId) {
        reelId?.let {
            ReelPlaybackTracker(
                reelId = it,
                wasPrefetched = false,
                networkType = EventTracker::networkType,
                emit = EventTracker::track
            )
        }
    }

    DisposableEffect(playbackTracker) {
        val tracker = playbackTracker ?: return@DisposableEffect onDispose {}
        val listener = object : AnalyticsListener {
            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                tracker.onFirstFrameRendered()
            }

            override fun onPlaybackStateChanged(
                eventTime: AnalyticsListener.EventTime,
                state: Int
            ) {
                when (state) {
                    Player.STATE_BUFFERING -> tracker.onBufferingStarted()
                    Player.STATE_ENDED -> {
                        tracker.onBufferingEnded()
                        tracker.onPlaybackEnded()
                    }
                    else -> tracker.onBufferingEnded()
                }
            }

            override fun onPositionDiscontinuity(
                eventTime: AnalyticsListener.EventTime,
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                tracker.onProgress(newPosition.positionMs, exoPlayer.duration.coerceAtLeast(0), force = true)
            }
        }
        exoPlayer.addAnalyticsListener(listener)
        onDispose {
            exoPlayer.removeAnalyticsListener(listener)
            tracker.onLeft()
        }
    }

    LaunchedEffect(isActive) {
        if (isActive && !isPaused) {
            playbackTracker?.onActivated()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(playbackTracker, isActive) {
        val tracker = playbackTracker ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (exoPlayer.isPlaying) {
                tracker.onProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(showPauseIcon) {
        if (showPauseIcon) {
            delay(800)
            showPauseIcon = false
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(300)
            currentTimeMs = exoPlayer.currentPosition
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    val activeCaption = captions.firstOrNull {
        currentTimeMs >= it.startMs && currentTimeMs <= it.endMs
    }

    Box(
        modifier = modifier.clickable {
            isPaused = !isPaused
            exoPlayer.playWhenReady = !isPaused
            showPauseIcon = true
            playbackTracker?.onProgress(
                exoPlayer.currentPosition,
                exoPlayer.duration.coerceAtLeast(0),
                force = true
            )
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showPauseIcon,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (activeCaption != null && !activeCaption.originalText.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ClickableCaptionText(
                        text = activeCaption.originalText ?: "",
                        onWordClick = onWordClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!activeCaption.translatedText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeCaption.translatedText ?: "",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageOrVideoPlayer(
    streamUrl: String,
    isActive: Boolean,
    captions: List<CaptionSegment> = emptyList(),
    onWordClick: (String) -> Unit = {},
    reelId: String? = null,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (!loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(streamUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loadFailed = false },
                onError = { loadFailed = true }
            )
        }

        if (loadFailed) {
            LocalVideoPlayer(
                streamUrl = streamUrl,
                isActive = isActive,
                captions = captions,
                onWordClick = onWordClick,
                reelId = reelId,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
