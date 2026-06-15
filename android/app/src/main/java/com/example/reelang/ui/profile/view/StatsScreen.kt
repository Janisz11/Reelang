package com.example.reelang.ui.profile.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary
import com.example.reelang.ui.profile.model.DayStat
import com.example.reelang.ui.profile.model.LanguageStat
import com.example.reelang.ui.profile.viewmodel.ProfileViewModel

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val stats = viewModel.statsComputed

    Scaffold(
        containerColor = ReelangCream,
        topBar = { StatsTopBar(onBack = onBack) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Vocabulary Mastered ──────────────────────────────────────────
            item {
                VocabularyCard(value = stats.vocabularyMastered)
            }

            // ── Streak + Hours ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Whatshot,
                        iconTint = Color(0xFFFF6B35),
                        value = "${stats.streakDays}",
                        unit = "Day",
                        label = "Streak"
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.PlayCircle,
                        iconTint = Color(0xFF4682B4),
                        value = "${stats.hoursWatched}h",
                        unit = "",
                        label = "Hours Watched"
                    )
                }
            }

            // ── Weekly Activity ──────────────────────────────────────────────
            item {
                WeeklyActivityCard(data = stats.weeklyActivity)
            }

            // ── Target Languages ─────────────────────────────────────────────
            item {
                TargetLanguagesCard(languages = stats.targetLanguages)
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsTopBar(onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReelangSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ReelangTextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Learning Stats",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ReelangTextPrimary
            )
        }
        HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
    }
}

// ─── Vocabulary Mastered Card ─────────────────────────────────────────────────

@Composable
private fun VocabularyCard(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ReelangRed
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vocabulary Mastered",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "words learned across all languages",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ─── Quick Stat Card ──────────────────────────────────────────────────────────

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ReelangTextPrimary
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ReelangTextSecondary
                    )
                }
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = ReelangTextSecondary
            )
        }
    }
}

// ─── Weekly Activity ──────────────────────────────────────────────────────────

@Composable
private fun WeeklyActivityCard(data: List<DayStat>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Weekly Activity",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ReelangTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Minutes studied per day",
                fontSize = 12.sp,
                color = ReelangTextSecondary
            )
            Spacer(Modifier.height(20.dp))
            BarChart(data = data)
        }
    }
}

@Composable
private fun BarChart(data: List<DayStat>) {
    val barColor = ReelangRed
    val trackColor = ReelangRed.copy(alpha = 0.12f)

    Column {
        // Canvas bars
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val totalSlots = data.size
            val slotWidth = size.width / totalSlots
            val barWidth = slotWidth * 0.5f
            val maxBarHeight = size.height
            val cornerR = CornerRadius(6.dp.toPx())

            data.forEachIndexed { index, stat ->
                val cx = slotWidth * index + slotWidth / 2f
                val left = cx - barWidth / 2f

                // track (full height background bar)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, maxBarHeight),
                    cornerRadius = cornerR
                )

                // filled bar
                val filledHeight = maxBarHeight * stat.value
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, maxBarHeight - filledHeight),
                    size = Size(barWidth, filledHeight),
                    cornerRadius = cornerR
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Day labels
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEach { stat ->
                Text(
                    text = stat.day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ReelangTextSecondary
                )
            }
        }
    }
}

// ─── Target Languages ─────────────────────────────────────────────────────────

@Composable
private fun TargetLanguagesCard(languages: List<LanguageStat>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Target Languages",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ReelangTextPrimary
            )
            Spacer(Modifier.height(16.dp))
            languages.forEachIndexed { index, lang ->
                LanguageRow(lang = lang)
                if (index < languages.lastIndex) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: LanguageStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = lang.flag, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReelangTextPrimary
                )
                Text(
                    text = "${lang.percent}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReelangRed
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { lang.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = ReelangRed,
                trackColor = ReelangRed.copy(alpha = 0.12f)
            )
        }
    }
}
