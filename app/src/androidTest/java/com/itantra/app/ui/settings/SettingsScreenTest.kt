package com.itantra.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itantra.app.core.model.Language
import com.itantra.app.ui.state.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backButton_invokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            SettingsScreen(
                uiState = AppUiState(),
                onUpdateLanguage = {},
                onBack = { backClicked = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun languageSelector_invokesCallback() {
        var selectedLang: Language? = null
        composeTestRule.setContent {
            SettingsScreen(
                uiState = AppUiState(selectedLanguage = Language.ENGLISH),
                onUpdateLanguage = { selectedLang = it },
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Hindi").performClick()
        assertEquals(Language.HINDI, selectedLang)
    }

    @Test
    fun appInfoSection_isDisplayed() {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = AppUiState(),
                onUpdateLanguage = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("Application Information").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name: iTantra").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version: 1.0.0").assertIsDisplayed()
    }
}
