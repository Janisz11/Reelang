package com.example.reelang.ui.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.UserSession
import com.example.reelang.network.ApiClient
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class CreateReelViewModel : ViewModel() {

    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024L
        private const val MAX_VIDEO_SIZE_BYTES = 100 * 1024 * 1024L
        private const val IMAGE_MAX_WIDTH = 1920
        private const val IMAGE_MAX_HEIGHT = 1080
        private const val IMAGE_QUALITY = 80
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun uploadReel(
        context: Context,
        file: File,
        title: String,
        language: String,
        tags: String = "",
        mimeType: String = "video/*"
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                val fileToUpload = if (mimeType.startsWith("image/")) {
                    if (file.length() > MAX_IMAGE_SIZE_BYTES) {
                        Compressor.compress(context, file) {
                            resolution(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT)
                            quality(IMAGE_QUALITY)
                            size(MAX_IMAGE_SIZE_BYTES)
                        }
                    } else {
                        file
                    }
                } else {
                    if (file.length() > MAX_VIDEO_SIZE_BYTES) {
                        _uploadState.value = UploadState.Error(
                            "Video is too large (max 100MB). Please choose a shorter clip."
                        )
                        return@launch
                    }
                    file
                }
                val mediaPart = MultipartBody.Part.createFormData(
                    "file",
                    fileToUpload.name,
                    fileToUpload.asRequestBody(mimeType.toMediaTypeOrNull())
                )
                val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = language.toRequestBody("text/plain".toMediaTypeOrNull())
                val tagsBody = tags.toRequestBody("text/plain".toMediaTypeOrNull())
                val ownerBody = UserSession.userId.toRequestBody("text/plain".toMediaTypeOrNull())
                ApiClient.api.uploadReel(mediaPart, titleBody, langBody, tagsBody, ownerBody)
                _uploadState.value = UploadState.Success
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Upload failed")
            }
        }
    }
}
