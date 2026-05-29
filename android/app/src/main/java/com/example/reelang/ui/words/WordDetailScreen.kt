package com.example.reelang.ui.words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.ui.common.LocalTTS
import com.example.reelang.ui.common.UiState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun WordDetailScreen(
    wordId: String,
    onBack: () -> Unit,
    viewModel: WordDetailViewModel = viewModel(
        factory = WordDetailViewModelFactory(wordId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = ReelangCream,
        topBar = {
            WordDetailTopBar(
                partOfSpeech = (uiState as? UiState.Success)?.data?.partOfSpeech ?: "",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ReelangRed, strokeWidth = 3.dp)
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = ReelangTextSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    TextButton(onClick = { viewModel.loadDetail() }) {
                        Text("Retry", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            is UiState.Success<WordDetail> -> {
                val detail = state.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item { HeroSection(detail) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { FlashcardSection(detail) }
                    if (detail.contextQuotes.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item { ContextSection(detail.contextQuotes) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (detail.variations.isNotEmpty()) {
                        item { VariationsSection(detail.variations) }
                    }
                }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun WordDetailTopBar(partOfSpeech: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReelangSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ReelangTextPrimary
                    )
                }
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "Back to feed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = ReelangTextPrimary
                )
            }
            PartOfSpeechBadge(partOfSpeech)
        }
        HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
    }
}

@Composable
private fun PartOfSpeechBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ReelangRed
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.8.sp
        )
    }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroSection(detail: WordDetail) {
    val tts = LocalTTS.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ReelangSurface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = detail.term,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ReelangTextPrimary
                )
                IconButton(
                    onClick = { tts?.speak(detail.term, detail.language) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(ReelangCream, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Pronounce",
                        tint = ReelangRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail.phonetic,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = ReelangTextSecondary
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Translations",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.translations.forEach { t ->
                    TranslationChip(translation = t)
                }
            }
        }
    }
}

@Composable
private fun TranslationChip(translation: Translation) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ReelangCream,
        border = BorderStroke(1.dp, ReelangBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = translation.language,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextSecondary,
                letterSpacing = 0.4.sp
            )
            Text(
                text = "·",
                fontSize = 11.sp,
                color = ReelangTextSecondary
            )
            Text(
                text = translation.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ReelangTextPrimary
            )
        }
    }
}

// ─── Flashcard ────────────────────────────────────────────────────────────────

@Composable
internal fun FlashcardSection(detail: WordDetail) {
    var isFlipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "flip"
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Flashcard",
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ReelangTextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { isFlipped = !isFlipped },
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = ReelangRed,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = detail.term,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = detail.language.uppercase(),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "tap to flip",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f },
                    shape = RoundedCornerShape(16.dp),
                    color = ReelangSurface,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = detail.translation.ifEmpty { "No translation available" },
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ReelangTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "EN",
                            fontSize = 12.sp,
                            color = ReelangTextSecondary,
                            letterSpacing = 1.sp
                        )
                        if (detail.definition.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = detail.definition,
                                fontSize = 13.sp,
                                color = ReelangTextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isFlipped) "showing translation" else "showing original",
            fontSize = 11.sp,
            color = ReelangTextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// ─── In Context ───────────────────────────────────────────────────────────────

@Composable
private fun ContextSection(quotes: List<ContextQuote>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("In Context")
        Spacer(Modifier.height(10.dp))
        quotes.forEach { quote ->
            ContextCard(quote = quote)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ContextCard(quote: ContextQuote) {
    val annotated = buildAnnotatedString {
        val text = quote.text
        val lower = text.lowercase()
        val word = quote.highlightWord.lowercase()
        var cursor = 0
        while (cursor < text.length) {
            val idx = lower.indexOf(word, cursor)
            if (idx == -1) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, idx))
            withStyle(SpanStyle(color = ReelangRed, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + word.length))
            }
            cursor = idx + word.length
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "\u201C",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ReelangRed,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = annotated,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = ReelangTextPrimary
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = quote.source,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ReelangTextSecondary,
                letterSpacing = 1.2.sp
            )
        }
    }
}

// ─── Variations ───────────────────────────────────────────────────────────────

@Composable
private fun VariationsSection(variations: List<Variation>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("Variations")
        Spacer(Modifier.height(10.dp))
        // 2x2 grid — take up to 4 variations in two rows
        val rows = variations.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { variation ->
                    VariationCard(
                        variation = variation,
                        modifier = Modifier.weight(1f)
                    )
                }
                // fill empty slot if odd count
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun VariationCard(variation: Variation, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = variation.label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ReelangTextSecondary,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = variation.value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextPrimary
            )
        }
    }
}

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = ReelangTextPrimary
    )
}
