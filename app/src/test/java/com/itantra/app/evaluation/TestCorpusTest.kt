package com.itantra.app.evaluation

import com.itantra.app.core.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCorpusTest {

    @Test
    fun `corpus has test cases for all 10 languages`() {
        val languages = TestCorpus.supportedLanguages()
        assertEquals(10, languages.size)
        assertTrue(languages.contains(Language.ENGLISH))
        assertTrue(languages.contains(Language.HINDI))
        assertTrue(languages.contains(Language.GUJARATI))
        assertTrue(languages.contains(Language.MARATHI))
        assertTrue(languages.contains(Language.KANNADA))
        assertTrue(languages.contains(Language.MALAYALAM))
        assertTrue(languages.contains(Language.TAMIL))
        assertTrue(languages.contains(Language.TELUGU))
        assertTrue(languages.contains(Language.ODIA))
        assertTrue(languages.contains(Language.BENGALI))
    }

    @Test
    fun `each language has at least 5 test cases`() {
        for (lang in Language.entries) {
            val cases = TestCorpus.getForLanguage(lang)
            assertTrue("${lang.displayName} should have at least 5 test cases, has ${cases.size}", cases.size >= 5)
        }
    }

    @Test
    fun `test case IDs are unique`() {
        val ids = TestCorpus.testCases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `getById returns correct test case`() {
        val tc = TestCorpus.getById("EN_001")
        assertNotNull(tc)
        assertEquals(Language.ENGLISH, tc!!.language)
        assertEquals("I need help", tc.referenceText)
    }

    @Test
    fun `getById returns null for unknown ID`() {
        val tc = TestCorpus.getById("NONEXISTENT")
        assertEquals(null, tc)
    }

    @Test
    fun `category filtering works`() {
        val emergencyCases = TestCorpus.getForCategory(TestCorpus.TestCategory.EMERGENCY)
        assertTrue(emergencyCases.isNotEmpty())
        emergencyCases.forEach { tc ->
            assertEquals(TestCorpus.TestCategory.EMERGENCY, tc.category)
        }
    }

    @Test
    fun `all reference texts are non-empty`() {
        TestCorpus.testCases.forEach { tc ->
            assertTrue("Test case ${tc.id} has empty reference", tc.referenceText.isNotBlank())
        }
    }

    @Test
    fun `all audio duration estimates are positive`() {
        TestCorpus.testCases.forEach { tc ->
            assertTrue("Test case ${tc.id} has non-positive duration", tc.estimatedAudioDurationMs > 0)
        }
    }

    @Test
    fun `Hindi test cases use Devanagari script`() {
        val hindiCases = TestCorpus.getForLanguage(Language.HINDI)
        hindiCases.forEach { tc ->
            assertTrue("Test case ${tc.id} should contain Devanagari", tc.referenceText.any { it in '\u0900'..'\u097F' })
        }
    }

    @Test
    fun `English test cases use Latin script`() {
        val englishCases = TestCorpus.getForLanguage(Language.ENGLISH)
        englishCases.forEach { tc ->
            assertTrue("Test case ${tc.id} should contain Latin", tc.referenceText.any { it in 'A'..'Z' || it in 'a'..'z' })
        }
    }

    @Test
    fun `total test corpus size is reasonable`() {
        assertTrue("Should have at least 50 test cases", TestCorpus.testCases.size >= 50)
    }
}
