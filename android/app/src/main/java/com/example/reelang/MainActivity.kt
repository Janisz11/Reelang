package com.example.reelang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.auth.viewmodel.AuthViewModel
import com.example.reelang.data.local.LocalDataSource
import com.example.reelang.data.local.ReelangDatabase
import com.example.reelang.ui.common.LocalDbSource
import com.example.reelang.ui.common.LocalTTS
import com.example.reelang.ui.common.TextToSpeechHelper
import com.example.reelang.ui.navigation.AppNavigation
import com.example.reelang.ui.theme.ReelangTheme

class MainActivity : ComponentActivity() {
    private lateinit var ttsHelper: TextToSpeechHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ttsHelper = TextToSpeechHelper(this)
        val database = ReelangDatabase.getInstance(this)
        val localDataSource = LocalDataSource(database)
        setContent {
            ReelangTheme {
                CompositionLocalProvider(
                    LocalTTS provides ttsHelper,
                    LocalDbSource provides localDataSource
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    AppNavigation(authViewModel = authViewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
    }
}
