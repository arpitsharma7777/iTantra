package com.itantra.app.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyTrackerTest {

    @Test
    fun `session records checkpoints with correct elapsed times`() {
        val tracker = LatencyTracker()
        val sessionId = tracker.startSession("test_1")

        // Simulate some delay
        Thread.sleep(50)

        tracker.recordCheckpoint(sessionId, "checkpoint_1")
        Thread.sleep(50)
        tracker.recordCheckpoint(sessionId, "checkpoint_2")

        val elapsed1 = tracker.getElapsedMs(sessionId, "checkpoint_1")
        val elapsed2 = tracker.getElapsedMs(sessionId, "checkpoint_2")

        assertTrue(elapsed1!! >= 40) // Allow some timing tolerance
        assertTrue(elapsed2!! >= 80)
        assertTrue(elapsed2 > elapsed1!!)
    }

    @Test
    fun `duration between checkpoints is correct`() {
        val tracker = LatencyTracker()
        val sessionId = tracker.startSession("test_2")

        tracker.recordCheckpoint(sessionId, "A")
        Thread.sleep(30)
        tracker.recordCheckpoint(sessionId, "B")

        val duration = tracker.getDurationMs(sessionId, "A", "B")
        assertTrue(duration!! >= 20)
    }

    @Test
    fun `returns null for missing session`() {
        val tracker = LatencyTracker()
        val summary = tracker.getSessionSummary("nonexistent")
        assertEquals(null, summary)
    }

    @Test
    fun `returns null for missing checkpoint`() {
        val tracker = LatencyTracker()
        val sessionId = tracker.startSession("test_3")
        val elapsed = tracker.getElapsedMs(sessionId, "nonexistent")
        assertEquals(null, elapsed)
    }

    @Test
    fun `session summary includes all checkpoints`() {
        val tracker = LatencyTracker()
        val sessionId = tracker.startSession("test_4")

        tracker.recordCheckpoint(sessionId, "T0")
        tracker.recordCheckpoint(sessionId, "T1")
        tracker.recordCheckpoint(sessionId, "T2")

        val summary = tracker.getSessionSummary(sessionId)
        assertEquals(4, summary?.size) // session_start + 3 checkpoints
        assertTrue(summary?.containsKey("session_start") == true)
        assertTrue(summary?.containsKey("T0") == true)
        assertTrue(summary?.containsKey("T1") == true)
        assertTrue(summary?.containsKey("T2") == true)
    }

    @Test
    fun `endSession removes session`() {
        val tracker = LatencyTracker()
        val sessionId = tracker.startSession("test_5")
        tracker.recordCheckpoint(sessionId, "A")

        val summary = tracker.endSession(sessionId)
        assertEquals(2, summary?.size)

        val afterEnd = tracker.getSessionSummary(sessionId)
        assertEquals(null, afterEnd)
    }

    @Test
    fun `clear removes all sessions`() {
        val tracker = LatencyTracker()
        tracker.startSession("s1")
        tracker.startSession("s2")
        tracker.clear()

        assertEquals(null, tracker.getSessionSummary("s1"))
        assertEquals(null, tracker.getSessionSummary("s2"))
    }

    @Test
    fun `standard checkpoints are defined`() {
        assertEquals("T0_speech_start", LatencyTracker.Checkpoints.T0_SPEECH_START)
        assertEquals("T5_stt_result_available", LatencyTracker.Checkpoints.T5_STT_RESULT_AVAILABLE)
        assertEquals("T_first_remote_audio", LatencyTracker.Checkpoints.T_FIRST_REMOTE_AUDIO)
    }
}
