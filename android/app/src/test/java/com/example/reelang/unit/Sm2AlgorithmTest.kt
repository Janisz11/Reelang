package com.example.reelang.unit

import com.example.reelang.util.Sm2Algorithm
import org.junit.Test
import org.junit.Assert.*

class Sm2AlgorithmTest {

    companion object {
        private const val INITIAL_EASINESS = 2.5f
        private const val MIN_EASINESS = 1.3f
        private const val SECOND_REVIEW_INTERVAL = 6
        private const val EXPECTED_THIRD_INTERVAL = 15
        private const val MASTERY_REPS = 5
        private const val EASINESS_DECAY_ITERATIONS = 20
    }

    @Test
    fun `first correct answer sets interval to 1`() {
        val (reps, _, interval) = Sm2Algorithm.update(0, INITIAL_EASINESS, 1, known = true)
        assertEquals(1, reps)
        assertEquals(1, interval)
    }

    @Test
    fun `second correct answer sets interval to 6`() {
        val (reps, _, interval) = Sm2Algorithm.update(1, INITIAL_EASINESS, 1, known = true)
        assertEquals(2, reps)
        assertEquals(SECOND_REVIEW_INTERVAL, interval)
    }

    @Test
    fun `third correct answer multiplies interval by easiness`() {
        val (reps, _, interval) = Sm2Algorithm.update(2, INITIAL_EASINESS, SECOND_REVIEW_INTERVAL, known = true)
        assertEquals(3, reps)
        assertEquals(EXPECTED_THIRD_INTERVAL, interval)
    }

    @Test
    fun `wrong answer resets repetitions to 0`() {
        val (reps, _, interval) = Sm2Algorithm.update(3, INITIAL_EASINESS, EXPECTED_THIRD_INTERVAL, known = false)
        assertEquals(0, reps)
        assertEquals(1, interval)
    }

    @Test
    fun `easiness never drops below 1_3`() {
        var easiness = INITIAL_EASINESS
        repeat(EASINESS_DECAY_ITERATIONS) {
            val (_, newEasiness, _) = Sm2Algorithm.update(0, easiness, 1, known = false)
            easiness = newEasiness
        }
        assertTrue(easiness >= MIN_EASINESS)
    }

    @Test
    fun `five correct answers marks word as mastered`() {
        var reps = 0
        var easiness = INITIAL_EASINESS
        var interval = 1
        repeat(MASTERY_REPS) {
            val result = Sm2Algorithm.update(reps, easiness, interval, known = true)
            reps = result.first
            easiness = result.second
            interval = result.third
        }
        assertTrue(reps >= MASTERY_REPS)
    }
}
