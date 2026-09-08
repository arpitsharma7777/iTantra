package com.itantra.app.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtfCalculatorTest {

    @Test
    fun `RTF calculation is correct`() {
        val rtf = RtfCalculator.calculate(processingTimeMs = 2000, audioDurationMs = 10000)
        assertEquals(0.2f, rtf, 0.001f)
    }

    @Test
    fun `RTF equal to 1 is real time`() {
        val rtf = RtfCalculator.calculate(5000, 5000)
        assertEquals(1.0f, rtf, 0.001f)
    }

    @Test
    fun `RTF greater than 1 is slower than real time`() {
        val rtf = RtfCalculator.calculate(10000, 5000)
        assertEquals(2.0f, rtf, 0.001f)
    }

    @Test
    fun `zero audio duration returns max value`() {
        val rtf = RtfCalculator.calculate(5000, 0)
        assertEquals(Float.MAX_VALUE, rtf)
    }

    @Test
    fun `interpret provides human-readable output`() {
        val excellent = RtfCalculator.interpret(0.3f)
        assertTrue(excellent.contains("Excellent"))

        val good = RtfCalculator.interpret(0.7f)
        assertTrue(good.contains("Good"))

        val realtime = RtfCalculator.interpret(1.0f)
        assertTrue(realtime.contains("Real-time"))

        val slow = RtfCalculator.interpret(1.5f)
        assertTrue(slow.contains("Slow"))
    }

    @Test
    fun `statistics are computed correctly`() {
        val values = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val stats = RtfCalculator.statistics(values)

        assertEquals(0.3f, stats.mean, 0.001f)
        assertEquals(0.3f, stats.median, 0.001f)
        assertEquals(0.1f, stats.min, 0.001f)
        assertEquals(0.5f, stats.max, 0.001f)
        assertEquals(5, stats.count)
    }

    @Test
    fun `STT RTF delegates to calculate`() {
        val rtf = RtfCalculator.calculateSttRtf(2000, 10000)
        assertEquals(0.2f, rtf, 0.001f)
    }

    @Test
    fun `TTS RTF delegates to calculate`() {
        val rtf = RtfCalculator.calculateTtsRtf(1000, 3000)
        assertEquals(1f / 3f, rtf, 0.001f)
    }
}
