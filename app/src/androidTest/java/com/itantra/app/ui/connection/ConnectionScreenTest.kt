package com.itantra.app.ui.connection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.ui.state.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disconnectedState_showsDisconnectedContent() {
        var discoverClicked = false
        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(connectionState = ConnectionState.DISCONNECTED),
                onDiscoverClicked = { discoverClicked = true },
                onConnectClicked = {},
                onDisconnectClicked = {},
                onNavigateToCommunication = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("No Device Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        assert(discoverClicked)
    }

    @Test
    fun discoveringState_showsDevicesAfterDelay() {
        var connectedDeviceName: String? = null
        val discoveredDevices = listOf(
            WifiDirectDevice(name = "Device A", address = "AA:AA:AA:AA:AA:AA"),
            WifiDirectDevice(name = "Device B", address = "BB:BB:BB:BB:BB:BB"),
            WifiDirectDevice(name = "Device C", address = "CC:CC:CC:CC:CC:CC")
        )

        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(
                    connectionState = ConnectionState.DISCOVERING,
                    discoveredDevices = discoveredDevices
                ),
                onDiscoverClicked = {},
                onConnectClicked = { connectedDeviceName = it.name },
                onDisconnectClicked = {},
                onNavigateToCommunication = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Searching for nearby devices...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device C").assertIsDisplayed()

        composeTestRule.onAllNodes(hasText("Connect"))[0].performClick()
        assertEquals("Device A", connectedDeviceName)
    }

    @Test
    fun connectedState_showsConnectedContent() {
        var disconnectClicked = false
        var communicationNavigated = false
        val deviceName = "iTantra Glasses"

        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(
                    connectionState = ConnectionState.CONNECTED,
                    connectedDeviceName = deviceName
                ),
                onDiscoverClicked = {},
                onConnectClicked = {},
                onDisconnectClicked = { disconnectClicked = true },
                onNavigateToCommunication = { communicationNavigated = true },
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Connected to: $deviceName").assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Start Communication").performClick()
        assert(communicationNavigated)

        composeTestRule.onNodeWithText("Disconnect").performClick()
        assert(disconnectClicked)
    }

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(),
                onDiscoverClicked = {},
                onConnectClicked = {},
                onDisconnectClicked = {},
                onNavigateToCommunication = {},
                onBack = { backClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Back to Home").performClick()
        assert(backClicked)
    }
}
