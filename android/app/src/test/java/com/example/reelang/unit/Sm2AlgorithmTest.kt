package com.example.reelang.unit

import org.junit.Test
import org.junit.Assert.*

class Sm2AlgorithmTest {

    private fun sm2Update(
        repetitions: Int,
        easiness: Float,
        intervalDays: Int,
        quality: Int
    ): Triple<Int, Float, Int> {
        val newInterval = if (quality >= 3) {
            when (repetitions) {
                0 -> 1
                1 -> 6
                else -> (intervalDays * easiness).toInt()
            }
        } else 1
        val newRepetitions = if (quality >= 3) repetitions + 1 else 0
        val newEasiness = maxOf(
            1.3f,
            easiness + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        )
        return Triple(newRepetitions, newEasiness, newInterval)
    }

    @Test
    fun `first correct answer sets interval to 1`() {
        val (reps, _, interval) = sm2Update(0, 2.5f, 1, 4)
        assertEquals(1, reps)
        assertEquals(1, interval)
    }

    @Test
    fun `second correct answer sets interval to 6`() {
        val (reps, _, interval) = sm2Update(1, 2.5f, 1, 4)
        assertEquals(2, reps)
        assertEquals(6, interval)
    }

    @Test
    fun `third correct answer multiplies interval by easiness`() {
        val (reps, _, interval) = sm2Update(2, 2.5f, 6, 4)
        assertEquals(3, reps)
        assertEquals(15, interval)
    }

    @Test
    fun `wrong answer resets repetitions to 0`() {
        val (reps, _, interval) = sm2Update(3, 2.5f, 15, 1)
        assertEquals(0, reps)
        assertEquals(1, interval)
    }

    @Test
    fun `easiness never drops below 1_3`() {
        var easiness = 2.5f
        repeat(20) {
            val (_, newEasiness, _) = sm2Update(0, easiness, 1, 0)
            easiness = newEasiness
        }
        assertTrue(easiness >= 1.3f)
    }

    @Test
    fun `five correct answers marks word as mastered`() {
        var reps = 0
        var easiness = 2.5f
        var interval = 1
        repeat(5) {
            val result = sm2Update(reps, easiness, interval, 4)
            reps = result.first
            easiness = result.second
            interval = result.third
        }
        assertTrue(reps >= 5)
    }
}
