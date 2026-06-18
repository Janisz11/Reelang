package com.example.reelang.util

object Sm2Algorithm {

    private const val QUALITY_KNOWN = 4
    private const val QUALITY_UNKNOWN = 1

    fun update(
        repetitions: Int,
        easiness: Float,
        intervalDays: Int,
        known: Boolean
    ): Triple<Int, Float, Int> {
        val quality = if (known) QUALITY_KNOWN else QUALITY_UNKNOWN
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
}
