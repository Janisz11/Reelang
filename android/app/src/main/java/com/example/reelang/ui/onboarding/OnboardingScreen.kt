package com.example.reelang.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Theme Colors ────────────────────────────────────────────────────────────
val ReelangRed = Color(0xFFB22222)
val ReelangRedLight = Color(0xFFE53935)
val ReelangCream = Color(0xFFFDF6F0)
val ReelangSurface = Color(0xFFFFFFFF)
val ReelangBorder = Color(0xFFE8DDD5)
val ReelangTextPrimary = Color(0xFF1A1A1A)
val ReelangTextSecondary = Color(0xFF888888)

// ─── Data Models ─────────────────────────────────────────────────────────────
data class Language(
    val code: String,
    val name: String,
    val flag: String
)

data class Level(
    val id: String,
    val label: String,
    val emoji: String
)

val availableLanguages = listOf(
    Language("es", "Spanish", "🇪🇸"),
    Language("fr", "French", "🇫🇷"),
    Language("de", "German", "🇩🇪"),
    Language("ja", "Japanese", "🇯🇵"),
    Language("it", "Italian", "🇮🇹"),
    Language("pt", "Portuguese", "🇵🇹")
)

val availableLevels = listOf(
    Level("beginner", "Beginner", "🌱"),
    Level("intermediate", "Intermediate", "🚀"),
    Level("advanced", "Advanced", "🏆")
)

// ─── ViewModel ───────────────────────────────────────────────────────────────
class OnboardingViewModel : ViewModel() {
    var selectedLanguage by mutableStateOf<Language?>(null)
        private set
    var selectedLevel by mutableStateOf<Level?>(null)
        private set

    fun selectLanguage(language: Language) {
        selectedLanguage = if (selectedLanguage == language) null else language
    }

    fun selectLevel(level: Level) {
        selectedLevel = if (selectedLevel == level) null else level
    }

    val canProceed: Boolean
        get() = selectedLanguage != null && selectedLevel != null
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onNavigateToFeed: (language: String, level: String) -> Unit = { _, _ -> }
) {
    val selectedLanguage = viewModel.selectedLanguage
    val selectedLevel = viewModel.selectedLevel
    val canProceed = viewModel.canProceed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReelangCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Globe emoji header
            Text(
                text = "🌎",
                fontSize = 52.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Learn by watching\nreels",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = ReelangTextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick your target language to start your journey.",
                fontSize = 14.sp,
                color = ReelangTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Language Grid
            LanguageGrid(
                languages = availableLanguages,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { viewModel.selectLanguage(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Level section
            Text(
                text = "Choose your level",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LevelSelector(
                levels = availableLevels,
                selectedLevel = selectedLevel,
                onLevelSelected = { viewModel.selectLevel(it) }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // CTA Button
            LetsGoButton(
                enabled = canProceed,
                onClick = {
                    onNavigateToFeed(
                        selectedLanguage!!.code,
                        selectedLevel!!.id
                    )
                }
            )
        }
    }
}

// ─── Language Grid ────────────────────────────────────────────────────────────
@Composable
fun LanguageGrid(
    languages: List<Language>,
    selectedLanguage: Language?,
    onLanguageSelected: (Language) -> Unit
) {
    val rows = languages.chunked(2)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { language ->
                    LanguageChip(
                        language = language,
                        isSelected = selectedLanguage == language,
                        onClick = { onLanguageSelected(language) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LanguageChip(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ReelangRed else ReelangBorder,
        animationSpec = tween(200),
        label = "border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFF0F0) else ReelangSurface,
        animationSpec = tween(200),
        label = "bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) ReelangRed else ReelangTextPrimary,
        animationSpec = tween(200),
        label = "text"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = language.flag, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = language.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

// ─── Level Selector ───────────────────────────────────────────────────────────
@Composable
fun LevelSelector(
    levels: List<Level>,
    selectedLevel: Level?,
    onLevelSelected: (Level) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        levels.forEach { level ->
            LevelChip(
                level = level,
                isSelected = selectedLevel == level,
                onClick = { onLevelSelected(level) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LevelChip(
    level: Level,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ReelangRed else ReelangSurface,
        animationSpec = tween(200),
        label = "levelBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else ReelangTextPrimary,
        animationSpec = tween(200),
        label = "levelText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ReelangRed else ReelangBorder,
        animationSpec = tween(200),
        label = "levelBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${level.emoji} ${level.label}",
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ─── CTA Button ───────────────────────────────────────────────────────────────
@Composable
fun LetsGoButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = tween(300),
        label = "btnAlpha"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ReelangRed,
            disabledContainerColor = ReelangRed.copy(alpha = 0.4f),
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (enabled) 4.dp else 0.dp
        )
    ) {
        Text(
            text = "Let's go! →",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OnboardingScreenPreview() {
    OnboardingScreen()
}
