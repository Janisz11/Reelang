package com.example.reelang.ui.profile.view

import android.R
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.navigation.NavController
import com.example.reelang.auth.model.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.ui.common.LocalDbSource
import com.example.reelang.ui.feed.model.bgColorsFor
import com.example.reelang.ui.feed.model.sceneEmojiFor
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.request.ImageRequest
import com.example.reelang.ui.profile.viewmodel.ProfileViewModel
import com.example.reelang.ui.profile.viewmodel.ProfileViewModelFactory
import com.example.reelang.ui.profile.model.PostThumbnail

// ─── Screen ───────────────────────────────────────────────────────────────────

private val profileTabs = listOf("Posts", "Saved", "Private")

@Composable
fun ProfileScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: ProfileViewModel? = null,
    targetUserId: String? = null
) {
    val isOtherUser = targetUserId != null && targetUserId != UserSession.userId
    val effectiveViewModel: ProfileViewModel = viewModel ?: if (isOtherUser) {
        viewModel(factory = ProfileViewModelFactory(targetUserId!!))
    } else {
        viewModel()
    }
    val localDbSource = LocalDbSource.current
    LaunchedEffect(localDbSource) {
        localDbSource?.let { effectiveViewModel.setLocalDataSource(it) }
    }

    val profile by effectiveViewModel.profile.collectAsState()
    val userReels by effectiveViewModel.userReels.collectAsState()
    val savedReels by effectiveViewModel.savedReels.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val userReelThumbnails = userReels.map { reel ->
        PostThumbnail(
            id = reel.id,
            color = bgColorsFor(reel.language).first(),
            emoji = sceneEmojiFor(reel.language),
            thumbnailUrl = when {
                reel.thumbnailUrl == null -> null
                reel.thumbnailUrl.startsWith("http") -> reel.thumbnailUrl
                else -> "${ApiClient.BASE_URL}reels/${reel.id}/thumbnail"
            },
            reelId = reel.id
        )
    }
    val savedPostThumbnails = savedReels.map { reel ->
        PostThumbnail(
            id = reel.id,
            color = bgColorsFor(reel.language).first(),
            emoji = sceneEmojiFor(reel.language),
            thumbnailUrl = when {
                reel.thumbnailUrl == null -> null
                reel.thumbnailUrl.startsWith("http") -> reel.thumbnailUrl
                else -> "${ApiClient.BASE_URL}reels/${reel.id}/thumbnail"
            },
            reelId = reel.id
        )
    }
    Scaffold(
        modifier = modifier,
        containerColor = ReelangCream,
        floatingActionButton = {
            if (!isOtherUser) {
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                ProfileHeader(
                    name = profile?.username ?: UserSession.displayName,
                    subtitle = "LVL ${profile?.level ?: 1}",
                    avatarInitials = profile?.avatarInitials ?: UserSession.initials(),
                    avatarColor = ReelangRed,
                    onNavigateToSettings = if (isOtherUser) null else onNavigateToSettings
                )
            }

            item {
                StatsRow(
                    followers = profile?.followersCount?.toString() ?: "0",
                    following = profile?.followingCount?.toString() ?: "0",
                    likes = profile?.totalLikes?.toString() ?: "0"
                )
                HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
            }

            if (isOtherUser) {
                item {
                    val isFollowing = profile?.isFollowing ?: false
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ReelangSurface)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = {
                                targetUserId?.let { effectiveViewModel.followUser(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing) Color.Transparent else ReelangRed,
                                contentColor = if (isFollowing) ReelangRed else Color.White
                            ),
                            border = if (isFollowing)
                                BorderStroke(1.dp, ReelangRed)
                            else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
                }
            }

            if (!isOtherUser) item {
                LearningStatsCard(onClick = onNavigateToStats)
                Spacer(Modifier.height(2.dp))
            }

            item {
                ProfileTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            item {
                when (selectedTab) {
                    0 -> ThumbnailGrid(
                        items = userReelThumbnails,
                        onReelClick = { reelId ->
                            val ownerUserId = if (isOtherUser) targetUserId!! else UserSession.userId
                            navController.navigate("user_reels/$ownerUserId/$reelId")
                        },
                        onReelLongPress = if (!isOtherUser) { reelId ->
                            effectiveViewModel.deleteReel(reelId)
                        } else null
                    )
                    1 -> ThumbnailGrid(
                        items = savedPostThumbnails,
                        onReelClick = { reelId ->
                            navController.navigate("saved_reel/$reelId")
                        }
                    )
                    2 -> PrivateGalleryTab(
                        onImageClick = { imageName ->
                            navController.navigate("private_image/${Uri.encode(imageName)}")
                        }
                    )
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    name: String,
    subtitle: String,
    avatarInitials: String,
    avatarColor: Color,
    onNavigateToSettings: (() -> Unit)? = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarInitials,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = name,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ReelangTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextSecondary,
                letterSpacing = 0.6.sp
            )
        }
        if (onNavigateToSettings != null) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = ReelangTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─── Stats Row ────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(followers: String, following: String, likes: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelangSurface)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(value = followers, label = "Followers")
        StatDivider()
        StatCell(value = following, label = "Following")
        StatDivider()
        StatCell(value = likes, label = "Likes")
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
private fun ThumbnailGrid(
    items: List<PostThumbnail>,
    onReelClick: (String) -> Unit = {},
    onReelLongPress: ((String) -> Unit)? = null
) {
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
                        onClick = { onReelClick(thumb.reelId ?: thumb.id) },
                        onLongPress = onReelLongPress?.let { { it(thumb.reelId ?: thumb.id) } },
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

// ─── Private Gallery ─────────────────────────────────────────────────────────

@Composable
fun PrivateGalleryTab(modifier: Modifier = Modifier, onImageClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val images = remember {
        try {
            val imageFiles = context.assets.list("images") ?: emptyArray()
            val videoFiles = try {
                context.assets.list("videos") ?: emptyArray()
            } catch (e: Exception) {
                emptyArray()
            }
            (imageFiles + videoFiles)
                .filter { name ->
                    name.isNotBlank() &&
                    !name.startsWith(".") &&
                    !name.startsWith("android-") &&
                    !name.contains("font") &&
                    !name.contains("logo") &&
                    !name.contains("clock") &&
                    !name.contains("progress") &&
                    (name.lowercase().endsWith(".jpg") ||
                     name.lowercase().endsWith(".jpeg") ||
                     name.lowercase().endsWith(".mp4")) &&
                    (name.contains("unsplash") || name.lowercase().endsWith(".mp4")) &&
                    name.length > 5
                }
                .sorted()
        } catch (e: Exception) {
            Log.e("PrivateGallery", "Error listing assets", e)
            emptyList()
        }
    }

    if (images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No private photos",
                color = ReelangTextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 800.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(images) { imageName ->
            PrivateImageCell(
                imageName = imageName,
                onClick = { onImageClick(imageName) }
            )
        }
    }
}

@Composable
fun PrivateImageCell(imageName: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(ReelangSurface)
            .clickable { onClick() }
    ) {
        val assetPath = if (imageName.lowercase().endsWith(".mp4"))
            "file:///android_asset/videos/${Uri.encode(imageName)}"
        else
            "file:///android_asset/images/${Uri.encode(imageName)}"
        if (imageName.lowercase().endsWith(".mp4")) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(assetPath)
                    .crossfade(true)
                    .build(),
                contentDescription = imageName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailCell(
    thumb: PostThumbnail,
    onClick: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete post?", fontWeight = FontWeight.Bold) },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onLongPress?.invoke()
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

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(thumb.color)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (onLongPress != null) showDeleteDialog = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumb.thumbnailUrl != null) {
            AsyncImage(
                model = thumb.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.ic_menu_gallery)
            )
        } else {
            Text(
                text = thumb.emoji,
                fontSize = 30.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Private Image Fullscreen ─────────────────────────────────────────────────

@Composable
fun PrivateImageFullscreenScreen(imageName: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val assetPath = if (imageName.lowercase().endsWith(".mp4"))
            "file:///android_asset/videos/${Uri.encode(imageName)}"
        else
            "file:///android_asset/images/${Uri.encode(imageName)}"
        if (imageName.lowercase().endsWith(".mp4")) {
            val context = LocalContext.current
            val exoPlayer = remember {
                ExoPlayer.Builder(context).build().apply {
                    val uri = Uri.parse("file:///android_asset/videos/$imageName")
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(assetPath)
                    .crossfade(true)
                    .build(),
                contentDescription = imageName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}
