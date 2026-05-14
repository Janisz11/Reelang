package com.example.reelang.ui.reels

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.example.reelang.network.models.CaptionSegment
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeView(
    youtubeId: String,
    captions: List<CaptionSegment> = emptyList(),
    isActive: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    var currentTimeMs by remember { mutableStateOf(0L) }
    val webViewRef = remember { mutableStateOf<android.webkit.WebView?>(null) }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(500)
            webViewRef.value?.evaluateJavascript(
                """
                try {
                    if (window.player) {
                        window.player.mute();
                        window.player.playVideo();
                        setTimeout(function() { window.player.unMute(); }, 300);
                    }
                } catch(e) {}
                """
            ) { }
        } else {
            webViewRef.value?.evaluateJavascript(
                """
                try {
                    if (window.player) {
                        window.player.pauseVideo();
                        window.player.mute();
                    }
                } catch(e) {}
                """
            ) { }
        }
    }

    LaunchedEffect(webViewRef.value) {
        while (true) {
            delay(300)
            webViewRef.value?.evaluateJavascript(
                "(function() { try { return window.currentTimeMs || 0; } catch(e) { return 0; } })()"
            ) { value ->
                val ms = value?.toDoubleOrNull()?.toLong() ?: 0L
                if (ms > 0) {
                    currentTimeMs = ms
                    Log.d("YouTubeWebView", "currentTimeMs: $ms")
                }
            }
        }
    }

    val activeCaption = captions.firstOrNull {
        currentTimeMs >= it.startMs && currentTimeMs <= it.endMs
    }

    Log.d("YouTubeWebView", "currentTimeMs: $currentTimeMs")
    Log.d("YouTubeWebView", "captions count: ${captions.size}")
    Log.d("YouTubeWebView", "activeCaption: ${activeCaption?.originalText}")
    captions.forEach { cap ->
        Log.d("YouTubeWebView", "caption: ${cap.startMs}-${cap.endMs}: ${cap.originalText}")
    }

    Box(modifier = modifier) {
        val state = rememberWebViewState(
            url = "https://reelang-player.vercel.app/?id=$youtubeId"
        )

        WebView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onCreated = { webView ->
                webView.settings.javaScriptEnabled = true
                webView.settings.mediaPlaybackRequiresUserGesture = false
                webView.settings.domStorageEnabled = true
                webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                webViewRef.value = webView
            }
        )

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
                    Text(
                        text = activeCaption.originalText ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LocalVideoPlayer(
    streamUrl: String,
    isActive: Boolean,
    captions: List<CaptionSegment> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            playWhenReady = false
        }
    }

    var currentTimeMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
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

    Box(modifier = modifier) {
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
                    Text(
                        text = activeCaption.originalText ?: "",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
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
