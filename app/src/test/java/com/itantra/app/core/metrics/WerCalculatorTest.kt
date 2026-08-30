package com.itantra.app.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class WerCalculatorTest {

    @Test
    fun `identical English phrases have zero WER`() {
        val wer = WerCalculator.calculate("I need help", "I need help")
        assertEquals(0.0f, wer, 0.001f)
    }

    @Test
    fun `single deletion in English phrase`() {
        // "emergency" is deleted
        val wer = WerCalculator.calculate("I need emergency help", "I need help")
        assertEquals(0.25f, wer, 0.001f)
    }

    @Test
    fun `identical Hindi phrases have zero WER`() {
        val wer = WerCalculator.calculate("मुझे मदद चाहिए", "मुझे मदद चाहिए")
        assertEquals(0.0f, wer, 0.001f)
    }

    @Test
    fun `single substitution in Hindi phrase`() {
        // "मदद" -> "पानी"
        val wer = WerCalculator.calculate("मुझे मदद चाहिए", "मुझे पानी चाहिए")
        assertEquals(1f/3f, wer, 0.001f)
    }

    @Test
    fun `empty strings return zero WER`() {
        val wer = WerCalculator.calculate("", "")
        assertEquals(0.0f, wer, 0.001f)
    }

    @Test
    fun `empty recognized returns 100 percent WER`() {
        val wer = WerCalculator.calculate("Reference phrase", "")
        assertEquals(1.0f, wer, 0.001f)
    }
}
