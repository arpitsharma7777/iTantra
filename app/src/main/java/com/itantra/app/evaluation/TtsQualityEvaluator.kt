package com.itantra.app.evaluation

import android.util.Log
import com.itantra.app.core.model.Language
import com.itantra.app.tts.TtsManager
import com.itantra.app.tts.TtsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Evaluates TTS quality through automated audio analysis.
 *
 * Measures structural audio properties that correlate with quality:
 * - Generation time and RTF
 * - Audio duration vs expected
 * - Clipping percentage
 * - Silence percentage
 * - Amplitude statistics (peak, RMS)
 * - Failed synthesis detection
 *
 * Human evaluation is handled separately via HumanTtsEvaluationScreen.
 */
class TtsQualityEvaluator(private val ttsManager: TtsManager) {

    companion object {
        private const val TAG = "TtsQualityEvaluator"
    }

    /**
     * Evaluates TTS for a given text and language.
     * Returns TtsMetrics with automated quality measurements.
     */
    suspend fun evaluate(
        text: String,
        language: Language,
        sampleRate: Int
    ): TtsMetrics = withContext(Dispatchers.IO) {
        val startTimeNs = System.nanoTime()

        try {
            // Wait for TTS to be ready
            ttsManager.state.first { it == TtsState.READY || it == TtsState.ERROR }
            if (ttsManager.state.value == TtsState.ERROR) {
                return@withContext TtsMetrics(
                    text = text,
                    language = language,
                    failedSynthesis = true
                )
            }

            // Trigger synthesis (speak returns after playback completes)
            ttsManager.speak(text, language)

            val elapsedNs = System.nanoTime() - startTimeNs
            val generationTimeMs = elapsedNs / 1_000_000

            // Since speak() blocks until completion, we estimate audio properties
            // based on text length and known model characteristics
            val estimatedDurationMs = estimateAudioDuration(text, language, sampleRate)
            val estimatedSizeBytes = (estimatedDurationMs * sampleRate * 2) / 1000 // 16-bit mono

            TtsMetrics(
                text = text,
                language = language,
                generationTimeMs = generationTimeMs,
                outputAudioDurationMs = estimatedDurationMs,
                outputAudioSizeBytes = estimatedSizeBytes,
                sampleRate = sampleRate,
                channels = 1,
                clippingPercentage = 0f, // Cannot measure without raw audio capture
                silencePercentage = 0f,
                peakAmplitude = 0f,
                rmsAmplitude = 0f,
                failedSynthesis = false,
                ttsInferenceLatencyMs = generationTimeMs,
                firstAudioSampleLatencyMs = generationTimeMs, // Non-streaming: first sample = full generation
                ttsRtf = if (estimatedDurationMs > 0) generationTimeMs.toFloat() / estimatedDurationMs else Float.MAX_VALUE
            )
        } catch (e: Exception) {
            Log.e(TAG, "TTS evaluation failed for text='$text', lang=$language", e)
            TtsMetrics(
                text = text,
                language = language,
                failedSynthesis = true
            )
        }
    }

    /**
     * Analyzes raw audio samples for quality metrics.
     * Call this when you have access to the raw ShortArray output from TTS engines.
     */
    fun analyzeAudioSamples(samples: ShortArray, sampleRate: Int): AudioAnalysis {
        if (samples.isEmpty()) {
            return AudioAnalysis(
                isValid = false,
                durationMs = 0,
                clippingPercentage = 0f,
                silencePercentage = 0f,
                peakAmplitude = 0f,
                rmsAmplitude = 0f,
                dcOffset = 0f
            )
        }

        val durationMs = (samples.size.toLong() * 1000) / sampleRate
        var peak = 0L
        var sumSq = 0.0
        var clippedCount = 0
        var silenceCount = 0
        val silenceThreshold = (Short.MAX_VALUE * 0.01).toInt() // 1% of max

        for (sample in samples) {
            val absSample = abs(sample.toInt())
            if (absSample > peak) peak = absSample.toLong()
            sumSq += sample.toDouble() * sample.toDouble()
            if (absSample >= Short.MAX_VALUE - 1) clippedCount++
            if (absSample < silenceThreshold) silenceCount++
        }

        val rms = sqrt(sumSq / samples.size).toFloat()
        val peakAmplitude = peak.toFloat() / Short.MAX_VALUE
        val clippingPercent = (clippedCount.toFloat() / samples.size) * 100f
        val silencePercent = (silenceCount.toFloat() / samples.size) * 100f

        return AudioAnalysis(
            isValid = true,
            durationMs = durationMs,
            clippingPercentage = clippingPercent,
            silencePercentage = silencePercent,
            peakAmplitude = peakAmplitude,
            rmsAmplitude = rms / Short.MAX_VALUE,
            dcOffset = samples.map { it.toFloat() }.average().toFloat() / Short.MAX_VALUE
        )
    }

    private fun estimateAudioDuration(text: String, language: Language, sampleRate: Int): Long {
        // Rough estimate: ~150 words per minute for TTS
        val wordCount = text.split(Regex("\\s+")).size
        val durationSeconds = (wordCount / 150.0) * 60.0
        return (durationSeconds * 1000).toLong()
    }

    data class AudioAnalysis(
        val isValid: Boolean,
        val durationMs: Long,
        val clippingPercentage: Float,
        val silencePercentage: Float,
        val peakAmplitude: Float,
        val rmsAmplitude: Float,
        val dcOffset: Float
    )
}
