package com.example.reelang.ui.create

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.reelang.ui.SharedState
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary
import java.io.File
import java.io.FileOutputStream



// ─── Screen ───────────────────────────────────────────────────────────────────

private val languages = listOf("EN", "ES", "FR", "DE", "PL")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReelScreen(
    navController: NavController,
    viewModel: CreateReelViewModel = viewModel()
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("EN") }
    var tags by remember { mutableStateOf("") }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedUri = uri }

    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success) {
            SharedState.triggerProfileRefresh()
            navController.navigate("feed") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = ReelangCream,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nowy Reel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ReelangTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz",
                            tint = ReelangTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReelangSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Media picker
            Button(
                onClick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ReelangRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedUri == null) "Wybierz zdjęcie / wideo" else "Zmień media",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            // Media preview
            if (selectedUri != null) {
                val isImage = isImageUri(context, selectedUri!!)
                if (isImage) {
                    ImagePreview(uri = selectedUri!!)
                } else {
                    VideoPreview(uri = selectedUri!!)
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Tytuł") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReelangRed,
                    focusedLabelColor = ReelangRed,
                    unfocusedBorderColor = ReelangBorder
                )
            )

            // Language dropdown
            ExposedDropdownMenuBox(
                expanded = languageMenuExpanded,
                onExpandedChange = { languageMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Język") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ReelangRed,
                        focusedLabelColor = ReelangRed,
                        unfocusedBorderColor = ReelangBorder
                    )
                )
                ExposedDropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                selectedLanguage = lang
                                languageMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Tags
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tagi (np. sport,jedzenie,podróże)") },
                placeholder = { Text("#sport #jedzenie", color = ReelangTextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReelangRed,
                    focusedLabelColor = ReelangRed,
                    unfocusedBorderColor = ReelangBorder
                )
            )

            // Upload button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    if (title.isBlank()) return@Button
                    val mimeType = context.contentResolver.getType(uri) ?: "video/*"
                    val file = copyUriToTempFile(context, uri, mimeType) ?: return@Button
                    viewModel.uploadReel(context, file, title, selectedLanguage, tags, mimeType)
                },
                enabled = selectedUri != null &&
                        title.isNotBlank() &&
                        uploadState !is UploadState.Loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ReelangRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uploadState is UploadState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Prześlij",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            // Error
            if (uploadState is UploadState.Error) {
                Text(
                    text = (uploadState as UploadState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Image Preview ────────────────────────────────────────────────────────────

@Composable
private fun ImagePreview(uri: Uri) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

// ─── Video Preview ────────────────────────────────────────────────────────────

@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

fun isImageUri(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri)
    return type?.startsWith("image/") == true
}

private fun copyUriToTempFile(context: Context, uri: Uri, mimeType: String = "video/*"): File? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val ext = when {
            mimeType.startsWith("image/jpeg") || mimeType.startsWith("image/jpg") -> ".jpg"
            mimeType.startsWith("image/png") -> ".png"
            mimeType.startsWith("image/webp") -> ".webp"
            else -> ".mp4"
        }
        val temp = File.createTempFile("reel_upload_", ext, context.cacheDir)
        FileOutputStream(temp).use { out -> input.copyTo(out) }
        temp
    } catch (_: Exception) {
        null
    }
}
