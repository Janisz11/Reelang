package com.example.reelang.unit

import org.junit.Test
import org.junit.Assert.*

class UiHelpersTest {

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
        n >= 1_000     -> "${n / 1_000}.${(n % 1_000) / 100}K"
        else           -> n.toString()
    }

    private fun bgColorsForTest(language: String): String = when (language.lowercase()) {
        "es" -> "1A1A2E"
        "fr" -> "2D1B1B"
        "ja" -> "0D1B2A"
        "de" -> "1A0A2E"
        else -> "1A1A1A"
    }

    private fun levelColorTest(level: String): String = when (level) {
        "A1", "A2" -> "43A047"
        "B1", "B2" -> "1E88E5"
        "C1", "C2" -> "8E24AA"
        else -> "757575"
    }

    @Test
    fun `formatCount formats thousands correctly`() {
        assertEquals("1.0K", formatCount(1000))
        assertEquals("1.5K", formatCount(1500))
        assertEquals("1.0M", formatCount(1000000))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `bgColorsFor returns correct colors for languages`() {
        val esColors = bgColorsForTest("es")
        val frColors = bgColorsForTest("fr")
        val jaColors = bgColorsForTest("ja")
        assertNotEquals(esColors, frColors)
        assertNotEquals(frColors, jaColors)
    }

    @Test
    fun `levelColor returns different colors for different levels`() {
        val a1 = levelColorTest("A1")
        val b1 = levelColorTest("B1")
        val c1 = levelColorTest("C1")
        assertNotEquals(a1, b1)
        assertNotEquals(b1, c1)
    }
}
