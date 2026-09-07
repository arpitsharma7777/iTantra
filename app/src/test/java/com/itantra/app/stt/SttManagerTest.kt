package com.itantra.app.stt

import android.content.Context
import android.content.ContextWrapper
import app.cash.turbine.test
import com.itantra.app.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SttManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testContext: TestContext
    private lateinit var sttManager: SttManager
    private var configJsonContent: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testContext = TestContext()
        val vadManager = VadManager(testContext)
        sttManager = SttManager(
            context = testContext,
            vadManager = vadManager,
        ) { fileName ->
            val content = configJsonContent
            if ((fileName == "sravaani/config.json") && (content != null)) {
                ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            } else {
                throw IOException("Asset not found: $fileName")
            }
        }
    }

    @After
    fun tearDown() {
        sttManager.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE`() = runTest {
        assertEquals(SttState.IDLE, sttManager.state.value)
        assertNull(sttManager.result.value)
        assertNull(sttManager.lastError.value)
    }

    @Test
    fun `switchLanguage updates state to READY`() = runTest {
        sttManager.switchLanguage(Language.HINDI)
        assertEquals(SttState.READY, sttManager.state.value)
    }

    @Test
    fun `startListening when in LOADING state fails and sets lastError`() = runTest {
        sttManager.state.test {
            assertEquals(SttState.IDLE, awaitItem())

            sttManager.initialize(Language.ENGLISH)
            assertEquals(SttState.LOADING, awaitItem())

            val sessionId = sttManager.startListening()
            assertEquals(-1, sessionId)
            assertNotNull(sttManager.lastError.value)
            assertTrue(sttManager.lastError.value!!.contains("model loading"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startListening fails when no language is selected`() = runTest {
        val sessionId = sttManager.startListening()
        assertEquals(-1, sessionId)
        assertNotNull(sttManager.lastError.value)
    }

    @Test
    fun `initialize success transitions state from LOADING to READY`() = runTest {
        configJsonContent = """
            {
                "supported_languages": {
                    "en": "English",
                    "hi": "Hindi"
                }
            }
        """.trimIndent()

        sttManager.state.test {
            assertEquals(SttState.IDLE, awaitItem())

            sttManager.initialize(Language.ENGLISH)
            assertEquals(SttState.LOADING, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SttState.READY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initialize failure transitions state to ERROR`() = runTest {
        configJsonContent = null // Will throw IOException when reading asset

        sttManager.state.test {
            assertEquals(SttState.IDLE, awaitItem())

            sttManager.initialize(Language.ENGLISH)
            assertEquals(SttState.LOADING, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SttState.ERROR, awaitItem())
            assertNotNull(sttManager.lastError.value)
            assertTrue(sttManager.lastError.value!!.contains("Sravaani model load failed"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopListening resets state to READY`() = runTest {
        sttManager.switchLanguage(Language.ENGLISH)
        assertEquals(SttState.READY, sttManager.state.value)

        sttManager.stopListening()
        assertEquals(SttState.READY, sttManager.state.value)
    }

    @Test
    fun `cancelListening resets state to READY`() = runTest {
        sttManager.switchLanguage(Language.ENGLISH)
        assertEquals(SttState.READY, sttManager.state.value)

        sttManager.cancelListening()
        assertEquals(SttState.READY, sttManager.state.value)
    }

    @Test
    fun `shutdown resets state to IDLE`() = runTest {
        sttManager.switchLanguage(Language.ENGLISH)
        assertEquals(SttState.READY, sttManager.state.value)

        sttManager.shutdown()
        assertEquals(SttState.IDLE, sttManager.state.value)
    }

    @Test
    fun `SttResult properties match expected values`() {
        val result = SttResult(
            sessionId = 42,
            language = Language.TAMIL,
            partialText = "வணக்கம்",
            finalText = "வணக்கம், எனக்கு உதவி தேவை",
        )
        assertEquals(42, result.sessionId)
        assertEquals(Language.TAMIL, result.language)
        assertEquals("வணக்கம்", result.partialText)
        assertEquals("வணக்கம், எனக்கு உதவி தேவை", result.finalText)
    }

    @Test
    fun `SttState enum contains all required states`() {
        val states = SttState.entries.toSet()
        assertTrue(states.contains(SttState.IDLE))
        assertTrue(states.contains(SttState.LOADING))
        assertTrue(states.contains(SttState.READY))
        assertTrue(states.contains(SttState.LISTENING))
        assertTrue(states.contains(SttState.ERROR))
    }

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
}
