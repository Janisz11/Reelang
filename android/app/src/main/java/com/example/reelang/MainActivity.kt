package com.example.reelang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.auth.AuthViewModel
import com.example.reelang.ui.navigation.AppNavigation
import com.example.reelang.ui.theme.ReelangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ReelangTheme {
                // Production: full auth + navigation flow
                val authViewModel: AuthViewModel = viewModel()
                AppNavigation(authViewModel = authViewModel)

                // Hardcoded test (swap in place of AppNavigation to bypass auth):
                // val testIds = listOf("pE9C2q5qU5A", "qIHeoV6HEx0")
                // val testReels = testIds.map { id ->
                //     ReelItem(
                //         id = id, youtubeId = id,
                //         channelName = "Test", avatarEmoji = "🌍",
                //         originalText = "Sample subtitle text", translatedText = "Przykładowy tekst",
                //         clickableWord = "Sample", likes = 0, saves = 0,
                //         level = "B1", streakDays = 1, language = "en",
                //         bgColors = listOf(Color(0xFF1A1A1A), Color(0xFF2D2D2D)),
                //         sceneEmoji = "🌍"
                //     )
                // }
                // ReelsScreen(viewModel = FeedViewModel().apply { /* inject testReels */ })
            }
        }
    }
}
