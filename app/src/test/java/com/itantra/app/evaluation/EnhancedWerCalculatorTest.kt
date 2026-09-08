package com.itantra.app.evaluation

import com.itantra.app.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedWerCalculatorTest {

    @Test
    fun `identical English phrases have zero WER`() {
        val result = EnhancedWerCalculator.calculate("I need help", "I need help")
        assertEquals(0f, result.wer, 0.001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
    }

    @Test
    fun `single deletion in English`() {
        val result = EnhancedWerCalculator.calculate("I need emergency help", "I need help")
        assertEquals(0.25f, result.wer, 0.001f)
        assertEquals(0, result.substitutions)
        assertEquals(1, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(4, result.referenceWordCount)
    }

    @Test
    fun `single insertion in English`() {
        val result = EnhancedWerCalculator.calculate("I need help", "I really need help")
        assertEquals(1f / 3f, result.wer, 0.001f)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(1, result.insertions)
    }

    @Test
    fun `single substitution in English`() {
        val result = EnhancedWerCalculator.calculate("I need help", "I want help")
        assertEquals(1f / 3f, result.wer, 0.001f)
        assertEquals(1, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
    }

    @Test
    fun `identical Hindi phrases have zero WER`() {
        val result = EnhancedWerCalculator.calculate("मुझे मदद चाहिए", "मुझे मदद चाहिए")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `substitution in Hindi`() {
        val result = EnhancedWerCalculator.calculate("मुझे मदद चाहिए", "मुझे पानी चाहिए")
        assertEquals(1f / 3f, result.wer, 0.001f)
        assertEquals(1, result.substitutions)
    }

    @Test
    fun `case insensitive for Latin`() {
        val result = EnhancedWerCalculator.calculate("I Need Help", "i need help")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `Devanagari case normalization not applied`() {
        // Devanagari has no case distinction; normalization should not break it
        val result = EnhancedWerCalculator.calculate("मुझे मदद चाहिए", "मुझे मदद चाहिए")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `punctuation is stripped`() {
        val result = EnhancedWerCalculator.calculate("Hello, world!", "Hello world")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `whitespace normalized`() {
        val result = EnhancedWerCalculator.calculate("I   need   help", "I need help")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `empty strings return zero WER`() {
        val result = EnhancedWerCalculator.calculate("", "")
        assertEquals(0f, result.wer, 0.001f)
    }

    @Test
    fun `empty recognized returns full WER`() {
        val result = EnhancedWerCalculator.calculate("Reference phrase", "")
        assertEquals(1.0f, result.wer, 0.001f)
        assertEquals(2, result.deletions)
    }

    @Test
    fun `empty reference returns full WER`() {
        val result = EnhancedWerCalculator.calculate("", "some words")
        assertEquals(1.0f, result.wer, 0.001f)
    }

    @Test
    fun `corpus WER calculation`() {
        val results = listOf(
            EnhancedWerCalculator.WerResult(0f, 0, 0, 0, 3, "", ""),
            EnhancedWerCalculator.WerResult(0.5f, 1, 0, 0, 2, "", ""),
            EnhancedWerCalculator.WerResult(0.25f, 0, 1, 0, 4, "", "")
        )
        val corpusWer = EnhancedWerCalculator.calculateCorpusWer(results)
        // Total edits = 0 + 1 + 1 = 2, total ref words = 3 + 2 + 4 = 9
        assertEquals(2f / 9f, corpusWer, 0.001f)
    }

    @Test
    fun `normalization is NFC`() {
        val input = "\u092E\u0941\u091D\u0947" // Devanagari without NFC
        val normalized = EnhancedWerCalculator.normalize(input)
        assertTrue(normalized.isNotEmpty())
    }

    @Test
    fun `configurable numeral handling`() {
        val keepConfig = EnhancedWerCalculator.NormalizationConfig(
            handleNumerals = EnhancedWerCalculator.NumeralHandling.KEEP
        )
        val stripConfig = EnhancedWerCalculator.NormalizationConfig(
            handleNumerals = EnhancedWerCalculator.NumeralHandling.STRIP
        )

        val keepResult = EnhancedWerCalculator.normalize("Call 911 now", keepConfig)
        val stripResult = EnhancedWerCalculator.normalize("Call 911 now", stripConfig)

        assertTrue(keepResult.contains("911"))
        assertFalse(stripResult.contains("911"))
    }
}
