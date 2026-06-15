package com.example.reelang.unit

import org.junit.Assert.*
import org.junit.Test

class FileValidationTest {

    private val MAX_IMAGE_BYTES = 2 * 1024 * 1024L
    private val MAX_VIDEO_BYTES = 100 * 1024 * 1024L

    sealed class ValidationResult {
        object Ok : ValidationResult()
        object NeedsCompression : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    private fun validate(mimeType: String, fileSizeBytes: Long): ValidationResult {
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

    @Test
    fun `image exactly at 2MB limit is accepted without compression`() {
        assertEquals(ValidationResult.Ok, validate("image/jpeg", MAX_IMAGE_BYTES))
    }

    @Test
    fun `image one byte over 2MB requires compression`() {
        assertEquals(ValidationResult.NeedsCompression, validate("image/jpeg", MAX_IMAGE_BYTES + 1))
    }

    @Test
    fun `image png is treated as image type`() {
        assertEquals(ValidationResult.Ok, validate("image/png", 1024))
    }

    @Test
    fun `video exactly at 100MB limit is accepted`() {
        assertEquals(ValidationResult.Ok, validate("video/mp4", MAX_VIDEO_BYTES))
    }

    @Test
    fun `video one byte over 100MB returns error`() {
        val result = validate("video/mp4", MAX_VIDEO_BYTES + 1)
        assertTrue(result is ValidationResult.Error)
    }

    @Test
    fun `video error message mentions 100MB`() {
        val result = validate("video/mp4", MAX_VIDEO_BYTES + 1) as ValidationResult.Error
        assertTrue(result.message.contains("100MB"))
    }

    @Test
    fun `zero byte video is accepted`() {
        assertEquals(ValidationResult.Ok, validate("video/mp4", 0))
    }

    @Test
    fun `zero byte image is accepted`() {
        assertEquals(ValidationResult.Ok, validate("image/jpeg", 0))
    }

    @Test
    fun `video webm is treated as non-image type`() {
        assertEquals(ValidationResult.Ok, validate("video/webm", MAX_VIDEO_BYTES))
    }
}
