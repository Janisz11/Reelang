package com.example.reelang.ui.create.model

sealed interface UploadState {
    object Idle : UploadState
    object Loading : UploadState
    object Success : UploadState
    data class Error(val message: String) : UploadState
}