package com.example.reelang.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Data Models ─────────────────────────────────────────────────────────────

data class PostThumbnail(val id: String, val color: Color, val emoji: String)

data class DayStat(val day: String, val value: Float)

data class LanguageStat(
    val flag: String,
    val name: String,
    val progress: Float,
    val percent: Int
)

data class ProfileData(
    val name: String,
    val subtitle: String,
    val avatarInitials: String,
    val avatarColor: Color,
    val followers: String,
    val following: String,
    val likes: String,
    val posts: List<PostThumbnail>,
    val savedPosts: List<PostThumbnail>
)

data class WeeklyStats(
    val vocabularyMastered: String,
    val streakDays: Int,
    val hoursWatched: Int,
    val weeklyActivity: List<DayStat>,
    val targetLanguages: List<LanguageStat>
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ProfileViewModel : ViewModel() {

    val profile = ProfileData(
        name = "Alex Rivera",
        subtitle = "POLYGLOT EXPLORER • LVL 24",
        avatarInitials = "AR",
        avatarColor = ReelangRed,
        followers = "1.4k",
        following = "312",
        likes = "8.7k",
        posts = listOf(
            PostThumbnail("p1", Color(0xFF6A5ACD), "🎬"),
            PostThumbnail("p2", Color(0xFF2E8B57), "🌍"),
            PostThumbnail("p3", Color(0xFFDC143C), "🗼"),
            PostThumbnail("p4", Color(0xFF4682B4), "🎵"),
            PostThumbnail("p5", Color(0xFF8B4513), "☕"),
            PostThumbnail("p6", Color(0xFF708090), "🏔️"),
        ),
        savedPosts = listOf(
            PostThumbnail("s1", Color(0xFFFF6347), "🍜"),
            PostThumbnail("s2", Color(0xFF20B2AA), "🌊"),
            PostThumbnail("s3", Color(0xFF9370DB), "🌸"),
            PostThumbnail("s4", Color(0xFF3CB371), "🌿"),
        )
    )

    val stats = WeeklyStats(
        vocabularyMastered = "1.2k",
        streakDays = 14,
        hoursWatched = 156,
        weeklyActivity = listOf(
            DayStat("Mon", 0.40f),
            DayStat("Tue", 0.70f),
            DayStat("Wed", 0.50f),
            DayStat("Thu", 0.90f),
            DayStat("Fri", 0.60f),
            DayStat("Sat", 1.00f),
            DayStat("Sun", 0.30f),
        ),
        targetLanguages = listOf(
            LanguageStat("🇫🇷", "French",   0.78f, 78),
            LanguageStat("🇯🇵", "Japanese", 0.45f, 45),
            LanguageStat("🇪🇸", "Spanish",  0.62f, 62),
            LanguageStat("🇩🇪", "German",   0.23f, 23),
        )
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

private val profileTabs = listOf("Posts", "Saved")

@Composable
fun ProfileScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val profile = viewModel.profile
    var selectedTab by remember { mutableIntStateOf(0) }
    val gridItems = if (selectedTab == 0) profile.posts else profile.savedPosts

    Scaffold(
        modifier = modifier,
        containerColor = ReelangCream,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create_reel") },
                containerColor = ReelangRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Create",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                ProfileHeader(profile = profile)
            }

            // ── Stats Row ───────────────────────────────────────────────────
            item {
                StatsRow(profile = profile)
                HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
            }

            // ── Learning Stats Card ──────────────────────────────────────────
            item {
                LearningStatsCard(onClick = onNavigateToStats)
                Spacer(Modifier.height(2.dp))
            }

            // ── Tab Row ─────────────────────────────────────────────────────
            item {
                ProfileTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            // ── Thumbnail Grid ───────────────────────────────────────────────
            item {
                ThumbnailGrid(items = gridItems)
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(profile: ProfileData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(profile.avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.avatarInitials,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = profile.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ReelangTextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = profile.subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ReelangTextSecondary,
            letterSpacing = 0.6.sp
        )
    }
}

// ─── Stats Row ────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(profile: ProfileData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(value = profile.followers, label = "Followers")
        StatDivider()
        StatCell(value = profile.following, label = "Following")
        StatDivider()
        StatCell(value = profile.likes, label = "Likes")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ReelangTextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = ReelangTextSecondary
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(1.dp)
            .background(ReelangBorder)
    )
}

// ─── Learning Stats Card ──────────────────────────────────────────────────────

@Composable
private fun LearningStatsCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = ReelangSurface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFFFF0F0), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = ReelangRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detailed Learning Stats",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReelangTextPrimary
                )
                Text(
                    text = "Vocabulary, streaks, activity",
                    fontSize = 12.sp,
                    color = ReelangTextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open stats",
                tint = ReelangTextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ─── Tab Row ─────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
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
        profileTabs.forEachIndexed { index, title ->
            val selected = selectedTab == index
            val labelColor by animateColorAsState(
                targetValue = if (selected) ReelangRed else ReelangTextSecondary,
                animationSpec = tween(200),
                label = "profile_tab_$index"
            )
            Tab(
                selected = selected,
                onClick = { onTabSelected(index) },
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
}

// ─── Thumbnail Grid ───────────────────────────────────────────────────────────

@Composable
private fun ThumbnailGrid(items: List<PostThumbnail>) {
    val rows = items.chunked(3)
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                row.forEach { thumb ->
                    ThumbnailCell(
                        thumb = thumb,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ThumbnailCell(thumb: PostThumbnail, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(thumb.color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = thumb.emoji,
            fontSize = 30.sp,
            textAlign = TextAlign.Center
        )
    }
}
