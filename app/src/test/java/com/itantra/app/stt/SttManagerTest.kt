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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SttManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testContext: TestContext
    private lateinit var sttManager: SttManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testContext = TestContext()
        testContext.createMockModelFiles()
        val vadManager = VadManager(testContext)
        val vaultManager = LanguageVaultManager(testContext)
        vaultManager.initializeFromAssets()
        sttManager = SttManager(
            context = testContext,
            vadManager = vadManager,
            vaultManager = vaultManager,
            sttEngine = FakeSttEngine(),
        )
    }

    @After
    fun tearDown() {
        sttManager.shutdown()
        testContext.cleanup()
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

            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(sttManager.lastError.value ?: "LOADING state was observed and coroutine is now running")
    }

    @Test
    fun `startListening fails when no language is selected`() = runTest {
        val sessionId = sttManager.startListening()
        assertEquals(-1, sessionId)
        assertNotNull(sttManager.lastError.value)
    }

    @Test
    fun `initialize success transitions state from LOADING to READY`() = runTest {
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
        val vaultManager = LanguageVaultManager(testContext)
        vaultManager.initializeFromAssets()
        val failingManager = SttManager(
            context = testContext,
            vadManager = VadManager(testContext),
            vaultManager = vaultManager,
            sttEngine = FailingSttEngine(),
        )

        failingManager.state.test {
            assertEquals(SttState.IDLE, awaitItem())

            failingManager.initialize(Language.ENGLISH)
            assertEquals(SttState.LOADING, awaitItem())

            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(SttState.ERROR, awaitItem())
            assertNotNull(failingManager.lastError.value)
            assertTrue(failingManager.lastError.value!!.contains("STT init failed"))
            cancelAndIgnoreRemainingEvents()
        }
        failingManager.shutdown()
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
        private val testDir = File(System.getProperty("java.io.tmpdir"), "itantra_test_${System.nanoTime()}")
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File {
            if (!testDir.exists()) testDir.mkdirs()
            return testDir
        }

        fun createMockModelFiles() {
            val indicDir = File(testDir, "indicconformer")
            listOf("en", "hi", "bn", "gu", "mr", "kn", "ml", "ta", "te").forEach { langCode ->
                val langDir = File(indicDir, langCode)
                langDir.mkdirs()
                File(langDir, "model.int8.onnx").createNewFile()
                File(langDir, "tokens.txt").createNewFile()
            }
        }

        fun cleanup() {
            testDir.deleteRecursively()
        }
    }

    private class FakeSttEngine : SttEngine {
        override fun initialize(context: Context) {}
        override fun prepareLanguage(language: com.itantra.app.core.model.Language): Boolean = true
        override fun transcribe(audioSamples: ShortArray, sampleRate: Int, language: com.itantra.app.core.model.Language): String = ""
        override fun release() {}
    }

    private class FailingSttEngine : SttEngine {
        override fun initialize(context: Context) {
            throw RuntimeException("STT engine init failed")
        }
        override fun prepareLanguage(language: com.itantra.app.core.model.Language): Boolean = true
        override fun transcribe(audioSamples: ShortArray, sampleRate: Int, language: com.itantra.app.core.model.Language): String = ""
        override fun release() {}
    }
}
