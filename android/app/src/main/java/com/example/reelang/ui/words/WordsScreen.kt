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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.ui.common.LocalDbSource
import com.example.reelang.ui.common.LocalTTS
import com.example.reelang.ui.common.UiState
import androidx.navigation.NavController
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Screen ───────────────────────────────────────────────────────────────────

private val tabs = listOf("All", "Learning", "Mastered")

@Composable
fun WordsScreen(
    modifier: Modifier = Modifier,
    viewModel: WordsViewModel = viewModel(),
    onWordClick: (wordId: String) -> Unit = {},
    navController: NavController
) {
    val localDbSource = LocalDbSource.current
    LaunchedEffect(localDbSource) {
        localDbSource?.let { viewModel.setLocalDataSource(it) }
    }

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
                        SwipeableWordCard(
                            word = word,
                            onClick = { onWordClick(word.id) },
                            onDelete = { viewModel.deleteWord(word.id) }
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

// ─── Swipeable Word Card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableWordCard(
    word: Word,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete word?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${word.term}\" will be removed from your vocabulary.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Delete", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = ReelangTextSecondary)
                }
            },
            containerColor = ReelangSurface
        )
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFFFFEBEB), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = ReelangRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Delete",
                        color = ReelangRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        WordCard(word = word, onClick = onClick)
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
