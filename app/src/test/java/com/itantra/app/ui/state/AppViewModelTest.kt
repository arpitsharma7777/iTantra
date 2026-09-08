package com.itantra.app.ui.state

import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state matches defaults`() {
        val state = viewModel.uiState.value
        assertEquals(ConnectionState.Disconnected, state.connectionState)
        assertEquals(Language.ENGLISH, state.selectedLanguage)
        assertNull(state.connectedDeviceName)
    }

    @Test
    fun `setLanguage correctly updates language`() {
        val newLanguage = Language.HINDI
        viewModel.setLanguage(newLanguage)

        val state = viewModel.uiState.value
        assertEquals(newLanguage, state.selectedLanguage)
    }

    @Test
    fun `clearMessages removes all messages`() {
        viewModel.clearMessages()

        val state = viewModel.uiState.value
        assertEquals(emptyList<Nothing>(), state.messages)
    }
}
