package com.itantra.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.ui.state.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun titleAndSubtitleAreDisplayed() {
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(),
                onConnectDevice = {},
                onStartCommunication = {},
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("iTantra").assertIsDisplayed()
        composeTestRule.onNodeWithText("Indian Multilingual Communication").assertIsDisplayed()
    }

    @Test
    fun connectionStatusShowsDisconnectedWhenDisconnected() {
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(connectionState = ConnectionState.DISCONNECTED),
                onConnectDevice = {},
                onStartCommunication = {},
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Status: Disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please connect a device first.").assertIsDisplayed()
    }

    @Test
    fun connectionStatusShowsConnectedWhenConnected() {
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(connectionState = ConnectionState.CONNECTED),
                onConnectDevice = {},
                onStartCommunication = {},
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Status: Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please connect a device first.").assertDoesNotExist()
    }

    @Test
    fun connectDeviceButtonInvokesCallback() {
        var callCount = 0
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(),
                onConnectDevice = { callCount++ },
                onStartCommunication = {},
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Connect Device").performClick()
        assertEquals(1, callCount)
    }

    @Test
    fun startCommunicationButtonIsDisabledWhenDisconnected() {
        var callCount = 0
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(connectionState = ConnectionState.DISCONNECTED),
                onConnectDevice = {},
                onStartCommunication = { callCount++ },
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Start Communication").assertIsNotEnabled()
        // performClick on a disabled node should not trigger the callback in most cases, 
        // but assertIsNotEnabled is the primary verification here.
        composeTestRule.onNodeWithText("Please connect a device first.").assertIsDisplayed()
        assertEquals(0, callCount)
    }

    @Test
    fun startCommunicationButtonInvokesCallbackWhenConnected() {
        var callCount = 0
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(connectionState = ConnectionState.CONNECTED),
                onConnectDevice = {},
                onStartCommunication = { callCount++ },
                onSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Start Communication").assertIsEnabled()
        composeTestRule.onNodeWithText("Start Communication").performClick()
        assertEquals(1, callCount)
    }

    @Test
    fun settingsButtonInvokesCallback() {
        var callCount = 0
        composeTestRule.setContent {
            HomeScreen(
                uiState = AppUiState(),
                onConnectDevice = {},
                onStartCommunication = {},
                onSettings = { callCount++ }
            )
        }

        composeTestRule.onNodeWithText("Settings").performClick()
        assertEquals(1, callCount)
    }
}
