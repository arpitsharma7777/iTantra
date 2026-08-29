package com.itantra.app.ui.state

import app.cash.turbine.test
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
    fun `initial state matches defaults`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            // Note: The AppViewModel currently initializes with two mock messages in its constructor
            // So we check only the requested fields
            assertEquals(ConnectionState.Disconnected, state.connectionState)
            assertEquals(Language.ENGLISH, state.selectedLanguage)
            assertNull(state.connectedDeviceName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateConnectionState correctly updates state`() = runTest {
        viewModel.uiState.test {
            // Skip initial state
            awaitItem()

            val newState = ConnectionState.Discovering
            viewModel.updateConnectionState(newState)
            
            val state = awaitItem()
            assertEquals(newState, state.connectionState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSelectedLanguage correctly updates language`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            val newLanguage = Language.HINDI
            viewModel.updateSelectedLanguage(newLanguage)
            
            val state = awaitItem()
            assertEquals(newLanguage, state.selectedLanguage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateConnectedDevice correctly updates device name`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            val deviceName = "Test Device"
            viewModel.updateConnectedDevice(deviceName)
            
            assertEquals(deviceName, awaitItem().connectedDeviceName)

            viewModel.updateConnectedDevice(null)
            assertNull(awaitItem().connectedDeviceName)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple sequential updates don't clobber unrelated fields`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            // Update 1: Connection State
            viewModel.updateConnectionState(ConnectionState.Discovering)
            assertEquals(ConnectionState.Discovering, awaitItem().connectionState)

            // Update 2: Language (Connection state should remain Discovering)
            viewModel.updateSelectedLanguage(Language.HINDI)
            val stateAfterLanguage = awaitItem()
            assertEquals(Language.HINDI, stateAfterLanguage.selectedLanguage)
            assertEquals(ConnectionState.Discovering, stateAfterLanguage.connectionState)

            // Update 3: Connected Device (Language and Connection State should remain)
            val deviceName = "iTantra Glasses"
            viewModel.updateConnectedDevice(deviceName)
            viewModel.updateConnectionState(ConnectionState.Connected(deviceName))
            
            // Collect both updates
            awaitItem() // Device name update
            val finalState = awaitItem() // Connection state update
            
            assertEquals(deviceName, finalState.connectedDeviceName)
            assertEquals(Language.HINDI, finalState.selectedLanguage)
            assertEquals(ConnectionState.Connected(deviceName), finalState.connectionState)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
