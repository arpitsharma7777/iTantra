package com.itantra.app.ui.communication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.ui.state.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunicationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(),
                onLanguageSelected = {},
                onMicPressed = {},
                onMicReleased = {},
                onBack = { backClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun connectedDeviceName_isDisplayed() {
        val deviceName = "iTantra Glasses"
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(connectedDeviceName = deviceName),
                onLanguageSelected = {},
                onMicPressed = {},
                onMicReleased = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(deviceName).assertIsDisplayed()
    }

    @Test
    fun languageSelector_invokesCallback() {
        var selectedLang: Language? = null
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(selectedLanguage = Language.ENGLISH),
                onLanguageSelected = { selectedLang = it },
                onMicPressed = {},
                onMicReleased = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Hindi").performClick()
        assertEquals(Language.HINDI, selectedLang)
    }

    @Test
    fun messageList_rendersMessages() {
        val messages = listOf(
            Message(id = "1", text = "Hello from remote", sender = Sender.REMOTE, timestamp = 100L, language = Language.ENGLISH),
            Message(id = "2", text = "Hi from local", sender = Sender.LOCAL, timestamp = 200L, language = Language.ENGLISH)
        )
        
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(messages = messages),
                onLanguageSelected = {},
                onMicPressed = {},
                onMicReleased = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Hello from remote").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hi from local").assertIsDisplayed()
    }

    @Test
    fun messageList_rendersEmptyState() {
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(messages = emptyList()),
                onLanguageSelected = {},
                onMicPressed = {},
                onMicReleased = {},
                onBack = {}
            )
        }

        // Just verify it doesn't crash and remains buildable
        composeTestRule.onNodeWithText("Communication").assertIsDisplayed()
    }

    @Test
    fun micButton_triggersPressAndReleaseCallbacks() {
        var pressed = false
        var released = false
        
        composeTestRule.setContent {
            CommunicationScreen(
                uiState = AppUiState(),
                onLanguageSelected = {},
                onMicPressed = { pressed = true },
                onMicReleased = { released = true },
                onBack = {}
            )
        }

        val micButton = composeTestRule.onNodeWithText("Hold to Speak")
        
        // Simulate Press
        micButton.performTouchInput {
            down(center)
        }
        assert(pressed)
        assert(!released)
        
        // Simulate Release
        micButton.performTouchInput {
            up()
        }
        assert(released)
    }
}
