package com.itantra.app.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.ui.state.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var viewModel: AppViewModel

    @Before
    fun setupAppNavHost() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            viewModel = AppViewModel()
            AppNavigation(navController = navController, viewModel = viewModel)
        }
    }

    @Test
    fun appNavHost_verifyStartDestination() {
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun appNavHost_clickConnectDevice_navigatesToConnection() {
        composeTestRule.onNodeWithText("Connect Device").performClick()
        assertEquals(Screen.Connection.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun appNavHost_clickSettings_navigatesToSettings() {
        composeTestRule.onNodeWithText("Settings").performClick()
        assertEquals(Screen.Settings.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun appNavHost_backFromConnection_returnsToHome() {
        composeTestRule.onNodeWithText("Connect Device").performClick()
        composeTestRule.onNodeWithText("Back to Home").performClick()
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun appNavHost_startCommunication_reachableOnlyWhenConnected() {
        // Initially disconnected
        composeTestRule.onNodeWithText("Start Communication").performClick()
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)

        // Go to connection screen and connect
        composeTestRule.onNodeWithText("Connect Device").performClick()
        composeTestRule.onNodeWithText("Discover Devices").performClick()
        
        // Wait for mock discovery (2s)
        composeTestRule.waitUntil(5000) {
            try {
                composeTestRule.onNodeWithText("Device A").assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        composeTestRule.onNodeWithText("Device A").performClick()
        
        // Navigate to communication from Connection screen
        composeTestRule.onNodeWithText("Start Communication").performClick()
        assertEquals(Screen.Communication.route, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun appNavHost_backFromCommunication_returnsToHome() {
        // Connect first to enable communication
        viewModel.updateConnectionState(ConnectionState.Connected("Test Device"))
        viewModel.updateConnectedDevice("Test Device")
        
        composeTestRule.onNodeWithText("Start Communication").performClick()
        assertEquals(Screen.Communication.route, navController.currentBackStackEntry?.destination?.route)
        
        // Use custom back button from Communication screen (it has a back icon)
        // Note: CommunicationScreen has an IconButton with contentDescription "Back"
        // But the task said "Back button (top bar, calls onBack)"
        // and we added an IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        // Wait, I should use content description for the back button
        
        // Find by content description
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Back")).performClick()
        
        assertEquals(Screen.Home.route, navController.currentBackStackEntry?.destination?.route)
    }
}
