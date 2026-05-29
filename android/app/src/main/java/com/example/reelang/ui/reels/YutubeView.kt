package com.example.reelang.ui.reels

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onWordClick: (String) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize()
) {
    var currentTimeMs by remember { mutableStateOf(0L) }
    val webViewRef = remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isYouTubePaused by remember { mutableStateOf(false) }
    var showYouTubePauseIcon by remember { mutableStateOf(false) }

    LaunchedEffect(showYouTubePauseIcon) {
        if (showYouTubePauseIcon) {
            delay(800)
            showYouTubePauseIcon = false
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(500)
            webViewRef.value?.evaluateJavascript(
                """
                try {
                    if (window.player) {
                        window.player.unMute();
                        window.player.setVolume(100);
                        window.player.playVideo();
                    }
                } catch(e) {}
                """
            ) { }
            delay(800)
            webViewRef.value?.evaluateJavascript(
                """
                try {
                    if (window.player) {
                        window.player.unMute();
                        window.player.setVolume(100);
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

    Box(
        modifier = modifier.clickable {
            isYouTubePaused = !isYouTubePaused
            showYouTubePauseIcon = true
            if (isYouTubePaused) {
                webViewRef.value?.evaluateJavascript(
                    "try { window.player.pauseVideo(); } catch(e) {}"
                ) {}
            } else {
                webViewRef.value?.evaluateJavascript(
                    "try { window.player.playVideo(); window.player.unMute(); } catch(e) {}"
                ) {}
            }
        }
    ) {
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

        AnimatedVisibility(
            visible = showYouTubePauseIcon,
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
                        imageVector = if (isYouTubePaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClickableCaptionText(
    text: String,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val words = text.split(" ")
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        words.forEach { word ->
            val cleanWord = word.trim().trimEnd('.', ',', '!', '?', ';', ':')
            var clicked by remember { mutableStateOf(false) }
            val color by animateColorAsState(
                targetValue = if (clicked) Color(0xFFFFD700) else Color.White,
                animationSpec = tween(300),
                finishedListener = { clicked = false },
                label = "wordColor"
            )
            Text(
                text = "$word ",
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        if (cleanWord.isNotEmpty()) {
                            clicked = true
                            onWordClick(cleanWord)
                        }
                    }
                    .padding(horizontal = 1.dp)
            )
        }
    }
}
