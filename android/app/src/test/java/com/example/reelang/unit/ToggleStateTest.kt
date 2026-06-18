package com.example.reelang.unit

import com.example.reelang.ui.feed.model.ReelItem
import com.example.reelang.ui.feed.viewmodel.applyLikeFailure
import com.example.reelang.ui.feed.viewmodel.applyLikeSuccess
import com.example.reelang.ui.feed.viewmodel.applySaveFailure
import com.example.reelang.ui.feed.viewmodel.applySaveSuccess
import org.junit.Assert.*
import org.junit.Test

class ToggleStateTest {

    companion object {
        private const val INITIAL_LIKES = 5
        private const val TOGGLED_LIKES = 6
        private const val OTHER_REEL_LIKES = 10
        private const val INITIAL_SAVES = 3
        private const val OTHER_REEL_SAVES = 5
    }

    private fun testReel(
        id: String,
        isLiked: Boolean = false,
        likes: Int = 0,
        isSaved: Boolean = false,
        saves: Int = 0
    ) = ReelItem(
        id = id,
        avatarEmoji = "",
        originalText = "",
        translatedText = "",
        clickableWord = "",
        likes = likes,
        saves = saves,
        streakDays = 0,
        language = "",
        bgColors = emptyList(),
        sceneEmoji = "",
        isLiked = isLiked,
        isSaved = isSaved
    )

   

    @Test
    fun `like success sets isLiked true and updates count from server`() {
        val reels = listOf(testReel("1", isLiked = false, likes = INITIAL_LIKES))
        val result = applyLikeSuccess(reels, "1", liked = true, likesCount = TOGGLED_LIKES)
        assertTrue(result[0].isLiked)
        assertEquals(TOGGLED_LIKES, result[0].likes)
    }

    @Test
    fun `unlike success sets isLiked false and updates count from server`() {
        val reels = listOf(testReel("1", isLiked = true, likes = TOGGLED_LIKES))
        val result = applyLikeSuccess(reels, "1", liked = false, likesCount = INITIAL_LIKES)
        assertFalse(result[0].isLiked)
        assertEquals(INITIAL_LIKES, result[0].likes)
    }

    @Test
    fun `like success does not modify other reels`() {
        val reels = listOf(
            testReel("1", isLiked = false, likes = INITIAL_LIKES),
            testReel("2", isLiked = true, likes = OTHER_REEL_LIKES)
        )
        val result = applyLikeSuccess(reels, "1", liked = true, likesCount = TOGGLED_LIKES)
        assertTrue(result[1].isLiked)
        assertEquals(OTHER_REEL_LIKES, result[1].likes)
    }

    

    @Test
    fun `like failure restores original isLiked false`() {
        val reels = listOf(testReel("1", isLiked = true, likes = TOGGLED_LIKES))
        val result = applyLikeFailure(reels, "1", originalLiked = false, originalLikes = INITIAL_LIKES)
        assertFalse(result[0].isLiked)
        assertEquals(INITIAL_LIKES, result[0].likes)
    }

    @Test
    fun `like failure restores original isLiked true`() {
        val reels = listOf(testReel("1", isLiked = false, likes = INITIAL_LIKES))
        val result = applyLikeFailure(reels, "1", originalLiked = true, originalLikes = TOGGLED_LIKES)
        assertTrue(result[0].isLiked)
        assertEquals(TOGGLED_LIKES, result[0].likes)
    }

    @Test
    fun `like failure does not modify other reels`() {
        val reels = listOf(
            testReel("1", isLiked = true, likes = TOGGLED_LIKES),
            testReel("2", isLiked = true, likes = OTHER_REEL_LIKES)
        )
        val result = applyLikeFailure(reels, "1", originalLiked = false, originalLikes = INITIAL_LIKES)
        assertTrue(result[1].isLiked)
        assertEquals(OTHER_REEL_LIKES, result[1].likes)
    }

  

    @Test
    fun `save success marks reel as saved and updates count from server`() {
        val reels = listOf(testReel("1", isSaved = false, saves = INITIAL_SAVES))
        val result = applySaveSuccess(reels, "1", saved = true, savesCount = INITIAL_SAVES + 1)
        assertTrue(result[0].isSaved)
        assertEquals(INITIAL_SAVES + 1, result[0].saves)
    }

    @Test
    fun `unsave success clears isSaved and updates count from server`() {
        val reels = listOf(testReel("1", isSaved = true, saves = INITIAL_SAVES))
        val result = applySaveSuccess(reels, "1", saved = false, savesCount = INITIAL_SAVES - 1)
        assertFalse(result[0].isSaved)
        assertEquals(INITIAL_SAVES - 1, result[0].saves)
    }




    @Test
    fun `save failure restores original isSaved false`() {
        val reels = listOf(testReel("1", isSaved = true, saves = INITIAL_SAVES + 1))
        val result = applySaveFailure(reels, "1", originalSaved = false, originalSaves = INITIAL_SAVES)
        assertFalse(result[0].isSaved)
        assertEquals(INITIAL_SAVES, result[0].saves)
    }

    @Test
    fun `save failure restores original isSaved true`() {
        val reels = listOf(testReel("1", isSaved = false, saves = INITIAL_SAVES - 1))
        val result = applySaveFailure(reels, "1", originalSaved = true, originalSaves = INITIAL_SAVES)
        assertTrue(result[0].isSaved)
        assertEquals(INITIAL_SAVES, result[0].saves)
    }

    @Test
    fun `save failure does not modify other reels`() {
        val reels = listOf(
            testReel("1", isSaved = true, saves = INITIAL_SAVES + 1),
            testReel("2", isSaved = true, saves = OTHER_REEL_SAVES)
        )
        val result = applySaveFailure(reels, "1", originalSaved = false, originalSaves = INITIAL_SAVES)
        assertTrue(result[1].isSaved)
        assertEquals(OTHER_REEL_SAVES, result[1].saves)
    }
}
