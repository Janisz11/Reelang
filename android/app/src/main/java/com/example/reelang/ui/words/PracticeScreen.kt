package com.example.reelang.ui.words

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ReviewRequest
import com.example.reelang.ui.common.LocalTTS
import com.example.reelang.ui.onboarding.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeCard(
    val wordId: String,
    val term: String,
    val language: String,
    val definition: String?,
    val translation: String?
)

class PracticeViewModel : ViewModel() {

    private val _cards = MutableStateFlow<List<PracticeCard>>(emptyList())
    val cards: StateFlow<List<PracticeCard>> = _cards.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionComplete = MutableStateFlow(false)
    val sessionComplete: StateFlow<Boolean> = _sessionComplete.asStateFlow()

    private val _knownCount = MutableStateFlow(0)
    val knownCount: StateFlow<Int> = _knownCount.asStateFlow()

    private val _unknownCount = MutableStateFlow(0)
    val unknownCount: StateFlow<Int> = _unknownCount.asStateFlow()

    init {
        loadCards()
    }

    fun loadCards() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                ApiClient.api.getWords()
            }.onSuccess { words ->
                val practiceCards = words.map { word ->
                    PracticeCard(
                        wordId = word.id,
                        term = word.term,
                        language = word.language,
                        definition = word.definition,
                        translation = null
                    )
                }.shuffled()
                _cards.value = practiceCards
            }.onFailure {
                _cards.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun markKnown() {
        val card = _cards.value.getOrNull(_currentIndex.value) ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.reviewWord(card.wordId, ReviewRequest(quality = 4))
            }.onSuccess {
                WordsEventBus.notifyWordSaved()
            }
        }
        _knownCount.value++
        nextCard()
    }

    fun markUnknown() {
        val card = _cards.value.getOrNull(_currentIndex.value) ?: return
        viewModelScope.launch {
            runCatching {
                ApiClient.api.reviewWord(card.wordId, ReviewRequest(quality = 1))
            }.onSuccess {
                WordsEventBus.notifyWordSaved()
            }
        }
        _unknownCount.value++
        nextCard()
    }

    private fun nextCard() {
        val next = _currentIndex.value + 1
        if (next >= _cards.value.size) {
            _sessionComplete.value = true
        } else {
            _currentIndex.value = next
        }
    }

    fun restart() {
        _currentIndex.value = 0
        _knownCount.value = 0
        _unknownCount.value = 0
        _sessionComplete.value = false
        _cards.value = _cards.value.shuffled()
    }
}

@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    viewModel: PracticeViewModel = viewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val sessionComplete by viewModel.sessionComplete.collectAsState()
    val knownCount by viewModel.knownCount.collectAsState()
    val unknownCount by viewModel.unknownCount.collectAsState()

    Scaffold(
        containerColor = ReelangCream,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReelangSurface)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ReelangTextPrimary
                        )
                    }
                    Text(
                        "Practice",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ReelangTextPrimary
                    )
                    if (!sessionComplete && cards.isNotEmpty()) {
                        Text(
                            "${currentIndex + 1}/${cards.size}",
                            fontSize = 14.sp,
                            color = ReelangTextSecondary
                        )
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
                HorizontalDivider(color = ReelangBorder)
                if (!sessionComplete && cards.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1f) / cards.size },
                        modifier = Modifier.fillMaxWidth(),
                        color = ReelangRed,
                        trackColor = ReelangRed.copy(alpha = 0.15f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = ReelangRed)
                }
                cards.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "No words to practice yet!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ReelangTextPrimary
                        )
                        Text(
                            "Save words from the feed to start practicing.",
                            fontSize = 14.sp,
                            color = ReelangTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = ReelangRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Go to Feed")
                        }
                    }
                }
                sessionComplete -> {
                    PracticeResultScreen(
                        knownCount = knownCount,
                        unknownCount = unknownCount,
                        total = cards.size,
                        onRestart = { viewModel.restart() },
                        onBack = onBack
                    )
                }
                else -> {
                    val card = cards[currentIndex]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        PracticeFlashcard(card = card)
                        Spacer(Modifier.height(40.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { viewModel.markUnknown() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEEEEE),
                                    contentColor = ReelangTextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Don't know", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { viewModel.markKnown() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ReelangRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("I know it", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PracticeFlashcard(card: PracticeCard) {
    val tts = LocalTTS.current
    LaunchedEffect(card.wordId) {
        delay(300)
        tts?.speak(card.term, card.language)
    }
    var isFlipped by remember(card.wordId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
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
                shape = RoundedCornerShape(20.dp),
                color = ReelangRed,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = card.language.uppercase(),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = card.term,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "tap to reveal",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
                shape = RoundedCornerShape(20.dp),
                color = ReelangSurface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val displayTranslation = when {
                        !card.translation.isNullOrEmpty() -> card.translation
                        !card.definition.isNullOrEmpty() && card.definition.startsWith("Translation: ") ->
                            card.definition.removePrefix("Translation: ")
                        else -> null
                    }
                    val displayDefinition = when {
                        !card.definition.isNullOrEmpty() && !card.definition.startsWith("Translation: ") ->
                            card.definition
                        else -> null
                    }

                    if (displayTranslation != null) {
                        Text(
                            text = "EN",
                            fontSize = 12.sp,
                            color = ReelangTextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = displayTranslation,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ReelangTextPrimary,
                            textAlign = TextAlign.Center
                        )
                        if (displayDefinition != null) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = ReelangBorder)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = displayDefinition,
                                fontSize = 13.sp,
                                color = ReelangTextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    } else if (displayDefinition != null) {
                        Text(
                            text = displayDefinition,
                            fontSize = 16.sp,
                            color = ReelangTextPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    } else {
                        Text(
                            text = "No translation available",
                            fontSize = 16.sp,
                            color = ReelangTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PracticeResultScreen(
    knownCount: Int,
    unknownCount: Int,
    total: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Session Complete!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = ReelangTextPrimary)
        Spacer(Modifier.height(32.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ReelangSurface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$knownCount", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4CAF50))
                        Text("Known", fontSize = 13.sp, color = ReelangTextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$unknownCount", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = ReelangRed)
                        Text("To review", fontSize = 13.sp, color = ReelangTextSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { knownCount.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF4CAF50),
                    trackColor = ReelangRed.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(8.dp))
                Text("${(knownCount.toFloat() / total * 100).toInt()}% mastered", fontSize = 14.sp, color = ReelangTextSecondary)
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ReelangRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Practice Again", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            border = androidx.compose.foundation.BorderStroke(1.dp, ReelangRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ReelangRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Back to Words", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

