package com.example.reelang.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.reelang.auth.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _reels = MutableStateFlow<List<ReelResponse>>(emptyList())
    val reels: StateFlow<List<ReelResponse>> = _reels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _profiles = MutableStateFlow<List<ProfileResponse>>(emptyList())
    val profiles: StateFlow<List<ProfileResponse>> = _profiles.asStateFlow()

    private val _profilesLoading = MutableStateFlow(false)
    val profilesLoading: StateFlow<Boolean> = _profilesLoading.asStateFlow()

    fun search(language: String? = null, level: String? = null, tags: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getReels(
                    language = language?.takeIf { it.isNotEmpty() },
                    level = level?.takeIf { it.isNotEmpty() },
                    topic = null,
                    tags = tags?.takeIf { it.isNotEmpty() }
                )
            }.onSuccess { _reels.value = it }
            _isLoading.value = false
        }
    }

    fun searchProfiles(query: String) {
        if (query.isBlank()) {
            _profiles.value = emptyList()
            return
        }
        viewModelScope.launch {
            _profilesLoading.value = true
            runCatching {
                ApiClient.api.searchProfiles(query, UserSession.userId)
            }.onSuccess { _profiles.value = it }
            _profilesLoading.value = false
        }
    }
}

@Composable
fun SearchScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profilesLoading by viewModel.profilesLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedLanguage by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var profileQuery by remember { mutableStateOf("") }

    val languages = listOf("", "EN", "ES", "FR", "DE", "PL")
    val levels = listOf("", "A1", "A2", "B1", "B2", "C1", "C2")

    LaunchedEffect(Unit) { viewModel.search() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ReelangCream)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Odkrywaj",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = ReelangTextPrimary
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ReelangCream,
            contentColor = ReelangRed,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ReelangRed
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Reele",
                        fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Profile",
                        fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }

        if (selectedTab == 0) {
            // Language filter chips
            Text("Język", fontSize = 13.sp, color = ReelangTextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(languages) { lang ->
                    FilterChip(
                        label = if (lang.isEmpty()) "Wszystkie" else lang,
                        selected = selectedLanguage == lang,
                        onClick = {
                            selectedLanguage = lang
                            viewModel.search(selectedLanguage, selectedLevel, tagInput)
                        }
                    )
                }
            }

            // Level filter chips
            Text("Poziom", fontSize = 13.sp, color = ReelangTextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(levels) { level ->
                    FilterChip(
                        label = if (level.isEmpty()) "Wszystkie" else level,
                        selected = selectedLevel == level,
                        onClick = {
                            selectedLevel = level
                            viewModel.search(selectedLanguage, selectedLevel, tagInput)
                        }
                    )
                }
            }

            // Tag search
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text("Szukaj po tagu") },
                placeholder = { Text("np. sport", color = ReelangTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReelangRed,
                    focusedLabelColor = ReelangRed,
                    unfocusedBorderColor = ReelangBorder
                ),
                trailingIcon = {
                    TextButton(onClick = {
                        viewModel.search(selectedLanguage, selectedLevel, tagInput)
                    }) {
                        Text("Szukaj", color = ReelangRed)
                    }
                }
            )

            // Results
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = ReelangRed
                )
            } else {
                Text(
                    "${reels.size} reeli",
                    fontSize = 13.sp,
                    color = ReelangTextSecondary
                )
                reels.forEach { reel ->
                    ReelSearchItem(
                        reel = reel,
                        onClick = {
                            val orderedIds = listOf(reel.id) + reels.map { it.id }.filter { it != reel.id }
                            navController.navigate("feed_from_search/${orderedIds.joinToString(",")}")
                        }
                    )
                }
            }
        } else {
            // Profile search
            OutlinedTextField(
                value = profileQuery,
                onValueChange = {
                    profileQuery = it
                    viewModel.searchProfiles(it)
                },
                label = { Text("Szukaj użytkownika") },
                placeholder = { Text("np. alex", color = ReelangTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReelangRed,
                    focusedLabelColor = ReelangRed,
                    unfocusedBorderColor = ReelangBorder
                )
            )

            if (profilesLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = ReelangRed
                )
            } else {
                profiles.forEach { profile ->
                    ProfileSearchItem(
                        profile = profile,
                        onClick = { navController.navigate("profile/${profile.userId}") }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(
                color = if (selected) ReelangRed else ReelangSurface,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else ReelangTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ReelSearchItem(reel: ReelResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ReelangSurface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = reel.title ?: "Bez tytułu",
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextPrimary
            )
            if (!reel.tags.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reel.tags,
                    fontSize = 12.sp,
                    color = ReelangRed
                )
            }
            Text(
                text = reel.language,
                fontSize = 12.sp,
                color = ReelangTextSecondary
            )
        }
    }
}

@Composable
fun ProfileSearchItem(profile: ProfileResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ReelangSurface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ReelangRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.avatarInitials ?: profile.username.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.username,
                    fontWeight = FontWeight.SemiBold,
                    color = ReelangTextPrimary
                )
                Text(
                    text = "LVL ${profile.level} • ${profile.followersCount} followers",
                    fontSize = 12.sp,
                    color = ReelangTextSecondary
                )
            }
        }
    }
}
