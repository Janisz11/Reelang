package com.example.reelang.ui.reels

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeView(
    youtubeId: String,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val url = "https://reelang-player.vercel.app/?id=$youtubeId"
    val state = rememberWebViewState(url = url)

    WebView(
        state = state,
        modifier = modifier,
        onCreated = { webView ->
            webView.settings.javaScriptEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.settings.domStorageEnabled = true
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    )
}
