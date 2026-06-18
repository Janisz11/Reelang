package com.example.reelang.unit

import com.example.reelang.util.FileValidator
import org.junit.Assert.*
import org.junit.Test

class FileValidationTest {

    companion object {
        private val MAX_IMAGE_BYTES = FileValidator.MAX_IMAGE_BYTES
        private val MAX_VIDEO_BYTES = FileValidator.MAX_VIDEO_BYTES
        private const val SMALL_FILE_BYTES = 1024L
    }

    @Test
    fun `image exactly at 2MB limit is accepted without compression`() {
        assertEquals(FileValidator.ValidationResult.Ok, FileValidator.validate("image/jpeg", MAX_IMAGE_BYTES))
    }

    @Test
    fun `image one byte over 2MB requires compression`() {
        assertEquals(FileValidator.ValidationResult.NeedsCompression, FileValidator.validate("image/jpeg", MAX_IMAGE_BYTES + 1))
    }

    @Test
    fun `image png is treated as image type`() {
        assertEquals(FileValidator.ValidationResult.Ok, FileValidator.validate("image/png", SMALL_FILE_BYTES))
    }

    @Test
    fun `video exactly at 100MB limit is accepted`() {
        assertEquals(FileValidator.ValidationResult.Ok, FileValidator.validate("video/mp4", MAX_VIDEO_BYTES))
    }

    @Test
    fun `video one byte over 100MB returns error`() {
        val result = FileValidator.validate("video/mp4", MAX_VIDEO_BYTES + 1)
        assertTrue(result is FileValidator.ValidationResult.Error)
    }

    @Test
    fun `video error message mentions 100MB`() {
        val result = FileValidator.validate("video/mp4", MAX_VIDEO_BYTES + 1) as FileValidator.ValidationResult.Error
        assertTrue(result.message.contains("100MB"))
    }

    @Test
    fun `zero byte video returns error`() {
        val result = FileValidator.validate("video/mp4", 0)
        assertTrue(result is FileValidator.ValidationResult.Error)
    }

    @Test
    fun `zero byte image returns error`() {
        val result = FileValidator.validate("image/jpeg", 0)
        assertTrue(result is FileValidator.ValidationResult.Error)
    }

    @Test
    fun `negative size returns error`() {
        val result = FileValidator.validate("image/jpeg", -1)
        assertTrue(result is FileValidator.ValidationResult.Error)
    }

    @Test
    fun `video webm is treated as non-image type`() {
        assertEquals(FileValidator.ValidationResult.Ok, FileValidator.validate("video/webm", MAX_VIDEO_BYTES))
    }
}
