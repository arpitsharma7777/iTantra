package com.itantra.app.evaluation

/**
 * Calculates Real-Time Factor (RTF).
 *
 * RTF = processing_time / audio_duration
 *
 * RTF < 1.0: faster than real-time (good)
 * RTF = 1.0: real-time
 * RTF > 1.0: slower than real-time (bad for real-time applications)
 */
object RtfCalculator {

    /**
     * Calculates RTF from processing time and audio duration.
     * @param processingTimeMs Time taken to process in milliseconds
     * @param audioDurationMs Duration of the audio in milliseconds
     * @return RTF value. Returns Float.MAX_VALUE if audio duration is zero.
     */
    fun calculate(processingTimeMs: Long, audioDurationMs: Long): Float {
        if (audioDurationMs <= 0) return Float.MAX_VALUE
        return processingTimeMs.toFloat() / audioDurationMs
    }

    /**
     * Calculates STT RTF.
     */
    fun calculateSttRtf(inferenceTimeMs: Long, audioDurationMs: Long): Float {
        return calculate(inferenceTimeMs, audioDurationMs)
    }

    /**
     * Calculates TTS RTF.
     */
    fun calculateTtsRtf(generationTimeMs: Long, outputDurationMs: Long): Float {
        return calculate(generationTimeMs, outputDurationMs)
    }

    /**
     * Interprets the RTF value.
     */
    fun interpret(rtf: Float): String {
        return when {
            rtf < 0.5f -> "Excellent (${String.format("%.2f", rtf)}x faster than real-time)"
            rtf < 1.0f -> "Good (${String.format("%.2f", rtf)}x faster than real-time)"
            rtf == 1.0f -> "Real-time"
            rtf < 2.0f -> "Slow (${String.format("%.2f", rtf)}x slower than real-time)"
            else -> "Very slow (${String.format("%.2f", rtf)}x slower than real-time)"
        }
    }

    /**
     * Calculates statistics for a collection of RTF values.
     */
    fun statistics(values: List<Float>): RtfStatistics {
        if (values.isEmpty()) return RtfStatistics()
        val sorted = values.sorted()
        val n = sorted.size
        return RtfStatistics(
            mean = sorted.average().toFloat(),
            median = sorted[n / 2],
            min = sorted.first(),
            max = sorted.last(),
            p95 = sorted[(n * 0.95).toInt().coerceAtMost(n - 1)],
            count = n
        )
    }
}

data class RtfStatistics(
    val mean: Float = 0f,
    val median: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f,
    val p95: Float = 0f,
    val count: Int = 0
)
