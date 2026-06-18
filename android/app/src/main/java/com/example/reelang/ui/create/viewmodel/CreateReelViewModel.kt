package com.example.reelang.ui.create.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reelang.auth.model.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.ui.create.model.UploadState
import com.example.reelang.util.FileValidator
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CreateReelViewModel : ViewModel() {

    companion object {
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
                val fileToUpload: File = when (val result = FileValidator.validate(mimeType, file.length())) {
                    is FileValidator.ValidationResult.Error -> {
                        _uploadState.value = UploadState.Error(result.message)
                        return@launch
                    }
                    FileValidator.ValidationResult.NeedsCompression -> Compressor.compress(context, file) {
                        resolution(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT)
                        quality(IMAGE_QUALITY)
                        size(FileValidator.MAX_IMAGE_BYTES)
                    }
                    FileValidator.ValidationResult.Ok -> file
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