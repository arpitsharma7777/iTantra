package com.itantra.app.stt

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detection.
 * Uses RMS energy and zero-crossing rate to detect speech without any ML model.
 *
 * Frame size: 512 samples at 16,000 Hz = 32 ms per frame.
 * Returns a speech probability in [0.0, 1.0] mapped from RMS energy.
 */
class EnergyVadManager : VadEngine {

    companion object {
        private const val TAG = "EnergyVadManager"
        private const val FRAME_SIZE = 512
        private const val SAMPLE_RATE = 16000
    }

    private data class Config(
        val speechRmsThreshold: Float = 0.03f,
        val maxRms: Float = 0.15f,
        val hangoverFrames: Int = 5,
        val adaptiveFloorAlpha: Float = 0.05f,
    )

    private val config = Config()

    private var noiseFloorRms = 0.005f
    private var hangoverCounter = 0
    private var initialized = false

    override fun initialize() {
        noiseFloorRms = 0.005f
        hangoverCounter = 0
        initialized = true
        Log.d(TAG, "EnergyVadManager initialized (threshold=${config.speechRmsThreshold}, hangover=${config.hangoverFrames} frames)")
    }

    override fun processFrame(frame: ShortArray): Float {
        if (!initialized) return 0f
        if (frame.size != FRAME_SIZE) {
            Log.w(TAG, "Expected $FRAME_SIZE samples, got ${frame.size}")
            return 0f
        }

        val rms = computeRms(frame)
        val zcr = computeZcr(frame)

        val isVoiced = zcr in 0.02f..0.30f

        val snr = if (noiseFloorRms > 0f) rms / noiseFloorRms else rms

        val rawScore = when {
            rms < noiseFloorRms * 1.2f -> 0f
            rms > config.maxRms -> 1f
            isVoiced && snr > 1.5f -> (snr - 1.0f).coerceIn(0f, 1f)
            isVoiced -> (rms / config.speechRmsThreshold).coerceIn(0f, 0.8f)
            else -> (rms / config.speechRmsThreshold * 0.3f).coerceIn(0f, 0.4f)
        }

        if (rawScore < config.speechRmsThreshold) {
            hangoverCounter = config.hangoverFrames
            noiseFloorRms += config.adaptiveFloorAlpha * (rms - noiseFloorRms)
        } else {
            if (hangoverCounter > 0) {
                hangoverCounter--
            }
        }

        val probability = if (hangoverCounter > 0 && rawScore > 0f) {
            rawScore.coerceAtLeast(0.4f)
        } else {
            rawScore
        }

        return probability.coerceIn(0f, 1f)
    }

    override fun resetState() {
        noiseFloorRms = 0.005f
        hangoverCounter = 0
        Log.d(TAG, "VAD state reset")
    }

    override fun release() {
        initialized = false
        Log.d(TAG, "EnergyVadManager released")
    }

    private fun computeRms(frame: ShortArray): Float {
        var sumSquares = 0.0
        for (sample in frame) {
            val normalized = sample.toFloat() / 32768f
            sumSquares += normalized * normalized
        }
        return sqrt((sumSquares / frame.size).toFloat())
    }

    private fun computeZcr(frame: ShortArray): Float {
        var crossings = 0
        for (i in 1 until frame.size) {
            if ((frame[i] >= 0 && frame[i - 1] < 0) || (frame[i] < 0 && frame[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / (frame.size - 1)
    }
}
