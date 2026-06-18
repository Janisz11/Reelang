package com.example.reelang.util

object FileValidator {

    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024L
    const val MAX_VIDEO_BYTES = 100 * 1024 * 1024L

    sealed class ValidationResult {
        object Ok : ValidationResult()
        object NeedsCompression : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    fun validate(mimeType: String, fileSizeBytes: Long): ValidationResult {
        if (fileSizeBytes <= 0) return ValidationResult.Error("File is empty. Please choose a valid file.")
        return if (mimeType.startsWith("image/")) {
            if (fileSizeBytes > MAX_IMAGE_BYTES) ValidationResult.NeedsCompression
            else ValidationResult.Ok
        } else {
            if (fileSizeBytes > MAX_VIDEO_BYTES) ValidationResult.Error(
                "Video is too large (max 100MB). Please choose a shorter clip."
            )
            else ValidationResult.Ok
        }
    }
}
