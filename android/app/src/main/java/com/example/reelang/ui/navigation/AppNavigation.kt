package com.example.reelang.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.example.reelang.ui.feed.viewmodel.FeedViewModel
import com.example.reelang.ui.feed.viewmodel.FeedViewModelFactory
import androidx.compose.ui.Alignment
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.reelang.auth.view.AuthScreen
import com.example.reelang.auth.viewmodel.AuthViewModel
import com.example.reelang.auth.model.UserSession
import com.example.reelang.ui.create.view.CreateReelScreen
import com.example.reelang.ui.onboarding.OnboardingScreen
import com.example.reelang.ui.search.view.SearchScreen
import com.example.reelang.ui.reels.view.ReelsScreen
import com.example.reelang.ui.profile.view.PrivateImageFullscreenScreen
import com.example.reelang.ui.profile.view.ProfileScreen
import com.example.reelang.ui.profile.viewmodel.ProfileViewModel
import com.example.reelang.ui.profile.view.SettingsScreen
import com.example.reelang.ui.profile.view.StatsScreen
import com.example.reelang.ui.words.view.PracticeScreen
import com.example.reelang.ui.words.view.WordDetailScreen
import com.example.reelang.ui.words.view.WordsScreen
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Nav Items ────────────────────────────────────────────────────────────────

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    NavItem("feed",    "Feed",    Icons.Filled.Home,     Icons.Outlined.Home),
    NavItem("search",  "Search",  Icons.Filled.Search,   Icons.Outlined.Search),
    NavItem("words",   "Words",   Icons.Filled.MenuBook,  Icons.Outlined.MenuBook),
    NavItem("profile", "Profile", Icons.Filled.Person,   Icons.Outlined.Person)
)

// Routes that show the bottom navigation bar
private val mainRoutes = setOf("feed", "search", "words", "profile")

private fun NavController.toWordDetail(wordId: String) = navigate("word_detail/$wordId")

// ─── Root Composable ──────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    newWordsCount: Int = 3
) {
    val navController  = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    // Skip auth + onboarding if the user is already signed in.
    val startDestination = if (FirebaseAuth.getInstance().currentUser != null) "feed" else "auth"

    Scaffold(
        bottomBar = {
            if (currentRoute in mainRoutes) {
                ReelangBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                    newWordsCount = newWordsCount
                )
            }
        }
    ) { innerPadding ->
        val profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {

            // ── Auth (unauthenticated entry point) ───────────────────────────
            composable("auth") {
                AuthScreen(
                    authViewModel = authViewModel,
                    onNavigateToOnboarding = {
                        navController.navigate("onboarding") {
                            popUpTo("auth") { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            // ── Onboarding (language / level selection after sign-in) ────────
            composable("onboarding") {
                OnboardingScreen(
                    onNavigateToFeed = { _, _ ->
                        navController.navigate("feed") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // ── Main app ──────────────────────────────────────────────────────
            composable("feed") {
                ReelsScreen(
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    onWordClick = { wordId -> navController.toWordDetail(wordId) },
                    onProfileClick = { userId ->
                        if (userId != UserSession.userId) {
                            navController.navigate("profile/$userId")
                        }
                    }
                )
            }

            composable("search") {
                SearchScreen(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            composable("words") {
                WordsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onWordClick = { wordId -> navController.toWordDetail(wordId) },
                    navController = navController
                )
            }

            composable("practice") {
                PracticeScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("word_detail/{wordId}") { backStack ->
                val wordId = backStack.arguments?.getString("wordId") ?: ""
                WordDetailScreen(
                    wordId = wordId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToStats = { navController.navigate("stats") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    viewModel = profileViewModel
                )
            }

            composable("stats") {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = profileViewModel
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable("profile/{userId}") { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: ""
                ProfileScreen(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToStats = { navController.navigate("stats") },
                    targetUserId = userId
                )
            }

            composable("feed_from_search/{reelIds}") { backStack ->
                val reelIds = backStack.arguments?.getString("reelIds") ?: ""
                ReelsScreen(
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    onWordClick = { wordId -> navController.toWordDetail(wordId) },
                    priorityReelIds = reelIds.split(",").filter { it.isNotEmpty() },
                    onProfileClick = { userId ->
                        if (userId != UserSession.userId) {
                            navController.navigate("profile/$userId")
                        }
                    }
                )
            }

            composable("saved_reel/{reelId}") { backStack ->
                val reelId = backStack.arguments?.getString("reelId") ?: ""
                val savedFeedViewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = FeedViewModelFactory(autoLoad = false)
                )
                LaunchedEffect(reelId) {
                    savedFeedViewModel.loadSingleReel(reelId)
                }
                ReelsScreen(
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    onWordClick = { wordId -> navController.toWordDetail(wordId) },
                    viewModel = savedFeedViewModel,
                    onProfileClick = { userId ->
                        if (userId != UserSession.userId) {
                            navController.navigate("profile/$userId")
                        }
                    }
                )
            }

            composable("user_reels/{userId}/{startReelId}") { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: ""
                val startReelId = backStack.arguments?.getString("startReelId") ?: ""
                val userFeedViewModel: FeedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = FeedViewModelFactory(autoLoad = false)
                )
                LaunchedEffect(userId, startReelId) {
                    userFeedViewModel.loadUserReels(userId, startReelId)
                }
                ReelsScreen(
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    onWordClick = { wordId -> navController.toWordDetail(wordId) },
                    viewModel = userFeedViewModel,
                    onProfileClick = { profileUserId ->
                        if (profileUserId != UserSession.userId) {
                            navController.navigate("profile/$profileUserId")
                        }
                    }
                )
            }

            composable("private_image/{imageName}") { backStack ->
                val imageName = android.net.Uri.decode(
                    backStack.arguments?.getString("imageName") ?: ""
                )
                PrivateImageFullscreenScreen(
                    imageName = imageName,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("create_reel") {
                CreateReelScreen(navController = navController)
            }
        }
    }
}

// ─── Bottom Bar ───────────────────────────────────────────────────────────────

@Composable
fun ReelangBottomBar(
    currentRoute: String?,
    navController: NavController,
    newWordsCount: Int
) {
    NavigationBar(
        containerColor = ReelangSurface,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            val iconColor by animateColorAsState(
                targetValue = if (selected) ReelangRed else ReelangTextSecondary,
                animationSpec = tween(200),
                label = "nav_${item.route}"
            )

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo("feed") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    val icon = if (selected) item.selectedIcon else item.unselectedIcon
                    if (item.route == "words" && newWordsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = ReelangRed,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (newWordsCount > 99) "99+" else newWordsCount.toString(),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = item.label, tint = iconColor)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = item.label, tint = iconColor)
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = iconColor
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color(0xFFFFF0F0)
                )
            )
        }
    }
}

// ─── Placeholder ──────────────────────────────────────────────────────────────

@Composable
fun PlaceholderScreen(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReelangCream),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$label — coming soon",
            fontSize = 16.sp,
            color = ReelangTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}
