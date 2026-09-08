package com.itantra.app.evaluation

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks timestamps at specific pipeline points for latency measurement.
 *
 * Uses System.nanoTime() for monotonic, high-resolution timing.
 * All durations are reported in milliseconds.
 *
 * Design:
 * - Each measurement session has a unique ID
 * - Timestamps are recorded at named checkpoints
 * - Duration between any two checkpoints can be computed
 * - Thread-safe for concurrent recording from different coroutines
 */
class LatencyTracker {

    companion object {
        private const val TAG = "LatencyTracker"
    }

    private data class MeasurementSession(
        val sessionId: String,
        val startTimeNs: Long,
        val checkpoints: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    )

    private val sessions = ConcurrentHashMap<String, MeasurementSession>()

    /**
     * Starts a new measurement session.
     */
    fun startSession(sessionId: String): String {
        val session = MeasurementSession(
            sessionId = sessionId,
            startTimeNs = System.nanoTime()
        )
        sessions[sessionId] = session
        Log.d(TAG, "Session started: $sessionId")
        return sessionId
    }

    /**
     * Records a checkpoint timestamp.
     * @param sessionId The session to record in
     * @param checkpointName Name of the checkpoint (e.g., "T0_speech_start", "T5_stt_result")
     */
    fun recordCheckpoint(sessionId: String, checkpointName: String) {
        sessions[sessionId]?.checkpoints?.put(checkpointName, System.nanoTime())
            ?: Log.w(TAG, "Checkpoint $checkpointName recorded for unknown session $sessionId")
    }

    /**
     * Records a checkpoint and returns the elapsed time since session start.
     */
    fun recordCheckpointAndGetElapsed(sessionId: String, checkpointName: String): Long {
        val now = System.nanoTime()
        sessions[sessionId]?.checkpoints?.put(checkpointName, now)
        val startNs = sessions[sessionId]?.startTimeNs ?: now
        return (now - startNs) / 1_000_000
    }

    /**
     * Computes the duration between two checkpoints in milliseconds.
     * Returns null if either checkpoint is missing.
     */
    fun getDurationMs(sessionId: String, from: String, to: String): Long? {
        val session = sessions[sessionId] ?: return null
        val fromNs = session.checkpoints[from] ?: return null
        val toNs = session.checkpoints[to] ?: return null
        return (toNs - fromNs) / 1_000_000
    }

    /**
     * Gets elapsed time from session start to a checkpoint in milliseconds.
     */
    fun getElapsedMs(sessionId: String, checkpoint: String): Long? {
        val session = sessions[sessionId] ?: return null
        val checkpointNs = session.checkpoints[checkpoint] ?: return null
        return (checkpointNs - session.startTimeNs) / 1_000_000
    }

    /**
     * Gets all checkpoint names and their elapsed times for a session.
     */
    fun getSessionSummary(sessionId: String): Map<String, Long>? {
        val session = sessions[sessionId] ?: return null
        val summary = mutableMapOf<String, Long>()
        summary["session_start"] = 0L
        session.checkpoints.forEach { (name, ns) ->
            summary[name] = (ns - session.startTimeNs) / 1_000_000
        }
        return summary.toSortedMap()
    }

    /**
     * Ends a session and returns the full checkpoint summary.
     */
    fun endSession(sessionId: String): Map<String, Long>? {
        val summary = getSessionSummary(sessionId)
        sessions.remove(sessionId)
        Log.d(TAG, "Session ended: $sessionId with ${summary?.size ?: 0} checkpoints")
        return summary
    }

    /**
     * Clears all sessions.
     */
    fun clear() {
        sessions.clear()
    }

    /**
     * Standard checkpoint names for the iTantra pipeline.
     */
    object Checkpoints {
        // STT pipeline
        const val T0_SPEECH_START = "T0_speech_start"
        const val T1_AUDIO_CAPTURE_BEGINS = "T1_audio_capture_begins"
        const val T2_VAD_DETECTS_SPEECH = "T2_vad_detects_speech"
        const val T3_SPEECH_SEGMENT_ENDS = "T3_speech_segment_ends"
        const val T4_STT_INFERENCE_STARTS = "T4_stt_inference_starts"
        const val T5_STT_RESULT_AVAILABLE = "T5_stt_result_available"

        // Transmission
        const val T_SEND = "T_send"
        const val T_RECEIVE = "T_receive"

        // TTS pipeline
        const val T6_TEXT_AVAILABLE = "T6_text_available_at_receiver"
        const val T7_TTS_INFERENCE_STARTS = "T7_tts_inference_starts"
        const val T8_FIRST_AUDIO_SAMPLE = "T8_first_playable_audio_sample"
        const val T9_PLAYBACK_STARTS = "T9_audio_playback_starts"
        const val T10_PLAYBACK_FINISHES = "T10_audio_playback_finishes"

        // End-to-end
        const val T_FIRST_REMOTE_AUDIO = "T_first_remote_audio"
    }
}
