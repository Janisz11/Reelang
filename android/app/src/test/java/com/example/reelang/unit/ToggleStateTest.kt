package com.example.reelang.unit

import org.junit.Assert.*
import org.junit.Test

class ToggleStateTest {

    data class Reel(
        val id: String,
        val isLiked: Boolean,
        val likes: Int,
        val isSaved: Boolean = false,
        val saves: Int = 0
    )

    data class LikeResponse(val liked: Boolean, val likesCount: Int)

    private fun applyLikeSuccess(list: List<Reel>, id: String, response: LikeResponse) =
        list.map {
            if (it.id == id) it.copy(isLiked = response.liked, likes = response.likesCount) else it
        }

    private fun applyLikeFailure(list: List<Reel>) = list

    private fun applySaveSuccess(list: List<Reel>, id: String, saved: Boolean) =
        list.map { reel ->
            if (reel.id == id) reel.copy(
                isSaved = saved,
                saves = if (saved) reel.saves + 1 else maxOf(0, reel.saves - 1)
            ) else reel
        }

    private fun applySaveFailure(list: List<Reel>) = list

    // Like - success

    @Test
    fun `like success sets isLiked true and updates count from server`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 5))
        val result = applyLikeSuccess(reels, "1", LikeResponse(liked = true, likesCount = 6))
        assertTrue(result[0].isLiked)
        assertEquals(6, result[0].likes)
    }

    @Test
    fun `unlike success sets isLiked false and updates count from server`() {
        val reels = listOf(Reel("1", isLiked = true, likes = 6))
        val result = applyLikeSuccess(reels, "1", LikeResponse(liked = false, likesCount = 5))
        assertFalse(result[0].isLiked)
        assertEquals(5, result[0].likes)
    }

    @Test
    fun `like success does not modify other reels`() {
        val reels = listOf(
            Reel("1", isLiked = false, likes = 5),
            Reel("2", isLiked = true, likes = 10)
        )
        val result = applyLikeSuccess(reels, "1", LikeResponse(liked = true, likesCount = 6))
        assertTrue(result[1].isLiked)
        assertEquals(10, result[1].likes)
    }

    // Like - failure (rollback)

    @Test
    fun `like failure rolls back isLiked to original false`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 5))
        val result = applyLikeFailure(reels)
        assertFalse(result[0].isLiked)
        assertEquals(5, result[0].likes)
    }

    @Test
    fun `like failure rolls back isLiked to original true`() {
        val reels = listOf(Reel("1", isLiked = true, likes = 6))
        val result = applyLikeFailure(reels)
        assertTrue(result[0].isLiked)
        assertEquals(6, result[0].likes)
    }

    @Test
    fun `like failure leaves entire list unchanged`() {
        val reels = listOf(
            Reel("1", isLiked = false, likes = 5),
            Reel("2", isLiked = true, likes = 10)
        )
        val result = applyLikeFailure(reels)
        assertEquals(reels, result)
    }

    // Save - success

    @Test
    fun `save success marks reel as saved and increments saves`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 0, isSaved = false, saves = 3))
        val result = applySaveSuccess(reels, "1", saved = true)
        assertTrue(result[0].isSaved)
        assertEquals(4, result[0].saves)
    }

    @Test
    fun `unsave success clears isSaved and decrements saves`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 0, isSaved = true, saves = 3))
        val result = applySaveSuccess(reels, "1", saved = false)
        assertFalse(result[0].isSaved)
        assertEquals(2, result[0].saves)
    }

    @Test
    fun `unsave does not decrement saves below zero`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 0, isSaved = true, saves = 0))
        val result = applySaveSuccess(reels, "1", saved = false)
        assertEquals(0, result[0].saves)
    }

    // Save - failure (rollback)

    @Test
    fun `save failure rolls back isSaved to original false`() {
        val reels = listOf(Reel("1", isLiked = false, likes = 0, isSaved = false, saves = 3))
        val result = applySaveFailure(reels)
        assertFalse(result[0].isSaved)
        assertEquals(3, result[0].saves)
    }

    @Test
    fun `save failure leaves entire list unchanged`() {
        val reels = listOf(
            Reel("1", isLiked = false, likes = 0, isSaved = false, saves = 0),
            Reel("2", isLiked = false, likes = 0, isSaved = true, saves = 5)
        )
        val result = applySaveFailure(reels)
        assertEquals(reels, result)
    }
}
