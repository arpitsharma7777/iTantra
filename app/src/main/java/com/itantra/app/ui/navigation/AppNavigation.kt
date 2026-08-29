package com.itantra.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.ui.home.HomeScreen
import com.itantra.app.ui.connection.ConnectionScreen
import com.itantra.app.ui.communication.CommunicationScreen
import com.itantra.app.ui.settings.SettingsScreen
import com.itantra.app.ui.state.AppViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                uiState = uiState,
                onConnectDevice = { navController.navigate(Screen.Connection.route) },
                onStartCommunication = { navController.navigate(Screen.Communication.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Connection.route) {
            ConnectionScreen(
                uiState = uiState,
                onDiscoverClicked = { viewModel.updateConnectionState(ConnectionState.Discovering) },
                onConnectClicked = { deviceName ->
                    viewModel.updateConnectionState(ConnectionState.Connected(deviceName))
                    viewModel.updateConnectedDevice(deviceName)
                },
                onDisconnectClicked = {
                    viewModel.updateConnectionState(ConnectionState.Disconnected)
                    viewModel.updateConnectedDevice(null)
                },
                onNavigateToCommunication = { navController.navigate(Screen.Communication.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Communication.route) {
            CommunicationScreen(
                uiState = uiState,
                onLanguageSelected = { viewModel.updateSelectedLanguage(it) },
                onMicPressed = { /* Log or No-op */ },
                onMicReleased = { /* Log or No-op */ },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                uiState = uiState,
                onUpdateLanguage = { viewModel.updateSelectedLanguage(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
