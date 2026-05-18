package com.example.reelang.ui.words

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.WordResponse
import com.example.reelang.ui.common.LocalTTS
import com.example.reelang.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Model ────────────────────────────────────────────────────────────────────

enum class WordStatus { LEARNING, MASTERED }

data class Word(
    val id: String,
    val term: String,
    val definition: String,
    val status: WordStatus,
    val progress: Float,
    val language: String
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class WordsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Word>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Word>>> = _uiState.asStateFlow()

    init {
        loadWords()
        viewModelScope.launch {
            WordsEventBus.wordSaved.collect { loadWords() }
        }
    }

    fun loadWords() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val words = ApiClient.api.getWords().map { it.toWord() }
                _uiState.value = UiState.Success(words)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load words")
            }
        }
    }

    private fun WordResponse.toWord() = Word(
        id = id,
        term = term,
        definition = definition ?: "",
        status = when (status?.lowercase()) {
            "mastered" -> WordStatus.MASTERED
            else -> WordStatus.LEARNING
        },
        progress = progress ?: 0f,
        language = language
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val tabs = listOf("All", "Learning", "Mastered")

@Composable
fun WordsScreen(
    modifier: Modifier = Modifier,
    viewModel: WordsViewModel = viewModel(),
    onWordClick: (wordId: String) -> Unit = {},
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ReelangCream)
    ) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReelangSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "My Words",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ReelangTextPrimary
            )
            StreakBadge()
        }

        // ── Tab Row ─────────────────────────────────────────────────────────
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
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                val labelColor by animateColorAsState(
                    targetValue = if (selected) ReelangRed else ReelangTextSecondary,
                    animationSpec = tween(200),
                    label = "tab_color_$index"
                )
                Tab(
                    selected = selected,
                    onClick = { selectedTab = index },
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

        // ── Content ──────────────────────────────────────────────────────────
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ReelangRed, strokeWidth = 3.dp)
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = ReelangTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    TextButton(onClick = { viewModel.loadWords() }) {
                        Text("Retry", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            is UiState.Success -> {
                val displayedWords = when (selectedTab) {
                    1    -> state.data.filter { it.status == WordStatus.LEARNING }
                    2    -> state.data.filter { it.status == WordStatus.MASTERED }
                    else -> state.data
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedWords, key = { it.id }) { word ->
                        WordCard(
                            word = word,
                            onClick = { onWordClick(word.id) }
                        )
                    }
                }
            }
        }

        // ── Bottom Buttons ───────────────────────────────────────────────────
        HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReelangSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* TODO: export */ },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, ReelangRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ReelangRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Export List", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { navController.navigate("practice") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReelangRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Practice Now", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Streak Badge ─────────────────────────────────────────────────────────────

@Composable
private fun StreakBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF0F0)
    ) {
        Text(
            text = "🔥 12 days",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReelangRed
        )
    }
}

// ─── Word Card ────────────────────────────────────────────────────────────────

@Composable
private fun WordCard(word: Word, onClick: () -> Unit) {
    val tts = LocalTTS.current
    val progressColor = if (word.status == WordStatus.MASTERED) Color(0xFF4CAF50) else ReelangRed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.term,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReelangTextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = word.definition,
                    fontSize = 13.sp,
                    color = ReelangTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { word.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f)
                )
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { tts?.speak(word.term, word.language) },
                modifier = Modifier
                    .size(38.dp)
                    .background(ReelangCream, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Pronounce ${word.term}",
                    tint = ReelangTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
