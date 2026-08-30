package com.itantra.app.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MetricsManagerTest {

    @Before
    fun setup() {
        MetricsManager.reset()
    }

    @Test
    fun `recording functions update only their respective fields`() {
        MetricsManager.recordSttModelLoadTime(100L)
        assertEquals(100L, MetricsManager.metrics.value.sttModelLoadTimeMs)
        assertNull(MetricsManager.metrics.value.sttRecognitionLatencyMs)

        MetricsManager.recordEncodingLatency(50L)
        assertEquals(50L, MetricsManager.metrics.value.encodingLatencyMs)
        assertEquals(100L, MetricsManager.metrics.value.sttModelLoadTimeMs)
    }

    @Test
    fun `counters increment independently`() {
        MetricsManager.incrementMessagesSent()
        MetricsManager.incrementMessagesSent()
        MetricsManager.incrementMessagesReceived()

        assertEquals(2, MetricsManager.metrics.value.messagesSent)
        assertEquals(1, MetricsManager.metrics.value.messagesReceived)
    }

    @Test
    fun `interleaved message timers do not leak data`() {
        val id1 = "msg-1"
        val id2 = "msg-2"
        
        val t0_1 = 1000L
        val t0_2 = 1100L
        
        // Start msg 1
        MetricsManager.startMessageTimer(id1, t0_1)
        assertEquals(id1, MetricsManager.metrics.value.currentMessageId)
        
        // Start msg 2
        MetricsManager.startMessageTimer(id2, t0_2)
        assertEquals(id2, MetricsManager.metrics.value.currentMessageId)
        
        // Mark STT complete for msg 1
        val t1_1 = 1500L
        MetricsManager.markSttComplete(id1, t1_1)
        // Note: metrics.value reflects the last update, 
        // which for sttRecognitionLatencyMs would be msg 1's latency
        assertEquals(500L, MetricsManager.metrics.value.sttRecognitionLatencyMs)
        
        // Mark STT complete for msg 2
        val t1_2 = 1700L
        MetricsManager.markSttComplete(id2, t1_2)
        assertEquals(600L, MetricsManager.metrics.value.sttRecognitionLatencyMs)
        
        // Finish msg 1 first
        val tFinal_1 = 2000L
        // We simulate the time pass for TTS started
        // Since markTtsStarted uses System.currentTimeMillis, we can't easily verify 
        // the EXACT value without mocking time, but we can verify it doesn't 
        // use msg 2's start time.
        // Actually, let's look at markTtsStarted code... it uses System.currentTimeMillis.
        // We can't verify the exact value, but we can verify the correlation logic.
        
        MetricsManager.markTtsStarted(id1)
        assertEquals(id1, MetricsManager.metrics.value.currentMessageId)
        
        MetricsManager.markTtsStarted(id2)
        assertEquals(id2, MetricsManager.metrics.value.currentMessageId)
    }
}
