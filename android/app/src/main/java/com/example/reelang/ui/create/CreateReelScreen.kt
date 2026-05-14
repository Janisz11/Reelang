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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.example.reelang.network.ApiClient
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

// ─── State ────────────────────────────────────────────────────────────────────

sealed interface UploadState {
    object Idle : UploadState
    object Loading : UploadState
    object Success : UploadState
    data class Error(val message: String) : UploadState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class CreateReelViewModel : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    fun uploadReel(videoFile: File, title: String, language: String) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val videoPart = MultipartBody.Part.createFormData(
                    "file",
                    videoFile.name,
                    videoFile.asRequestBody("video/*".toMediaTypeOrNull())
                )
                val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = language.toRequestBody("text/plain".toMediaTypeOrNull())
                ApiClient.api.uploadReel(videoPart, titleBody, langBody)
                _uploadState.value = UploadState.Success
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }


}

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
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedUri = uri }

    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success) {
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
            // Video picker
            Button(
                onClick = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ReelangRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selectedUri == null) "Wybierz wideo" else "Zmień wideo",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            // Video preview
            if (selectedUri != null) {
                VideoPreview(uri = selectedUri!!)
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

            // Upload button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    if (title.isBlank()) return@Button
                    val videoFile = copyUriToTempFile(context, uri) ?: return@Button
                    viewModel.uploadReel(videoFile, title, selectedLanguage)
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

private fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val temp = File.createTempFile("reel_upload_", ".mp4", context.cacheDir)
        FileOutputStream(temp).use { out -> input.copyTo(out) }
        temp
    } catch (_: Exception) {
        null
    }
}
