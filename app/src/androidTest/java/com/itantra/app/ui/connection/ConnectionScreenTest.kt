package com.itantra.app.ui.connection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itantra.app.core.model.ConnectionState
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
                uiState = AppUiState(connectionState = ConnectionState.Disconnected),
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
        var connectedDevice = ""
        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(connectionState = ConnectionState.Discovering),
                onDiscoverClicked = {},
                onConnectClicked = { connectedDevice = it },
                onDisconnectClicked = {},
                onNavigateToCommunication = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Searching for nearby devices...").assertIsDisplayed()
        
        // Wait for mock discovery (2s delay in code)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Device A")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Device A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device C").assertIsDisplayed()

        // Test connection click
        composeTestRule.onAllNodes(hasText("Connect"))[0].performClick()
        assertEquals("Device A", connectedDevice)
    }

    @Test
    fun connectedState_showsConnectedContent() {
        var disconnectClicked = false
        var communicationNavigated = false
        val deviceName = "iTantra Glasses"

        composeTestRule.setContent {
            ConnectionScreen(
                uiState = AppUiState(connectionState = ConnectionState.Connected(deviceName)),
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
