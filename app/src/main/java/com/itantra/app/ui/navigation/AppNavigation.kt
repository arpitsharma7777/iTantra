package com.itantra.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import com.itantra.app.ui.communication.CommunicationScreen
import com.itantra.app.ui.connection.ConnectionScreen
import com.itantra.app.ui.home.HomeScreen
import com.itantra.app.ui.state.AppViewModel

@Composable
fun AppNavigation(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                uiState = uiState,
                onLanguageSelect = { viewModel.setLanguage(it) },
                onConnectClick = { navController.navigate("connection") },
                onCommunicateClick = { navController.navigate("communication") },
                onNavigateToTab = { /* Handle tabs */ }
            )
        }
        composable("connection") {
            ConnectionScreen(
                connectionState = uiState.connectionState,
                onStartScan = { viewModel.startDiscovering() },
                onDeviceClick = { deviceName ->
                    viewModel.setConnectedDevice(deviceName)
                },
                onBackClick = { navController.popBackStack() },
                onStartCommunication = { navController.navigate("communication") }
            )
        }
        composable("communication") {
            CommunicationScreen(
                messages = uiState.messages,
                selectedLanguage = uiState.selectedLanguage,
                connectedDeviceName = uiState.connectedDeviceName,
                onBackClick = { navController.popBackStack() },
                onMicClick = { 
                    // Mock adding a message
                    viewModel.addMessage(
                        Message(
                            id = System.currentTimeMillis().toString(),
                            text = "Hello, how are you?",
                            translatedText = "नमस्ते, आप कैसे हैं?",
                            sender = Sender.SENDER,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onSendClick = {
                     viewModel.addMessage(
                        Message(
                            id = System.currentTimeMillis().toString(),
                            text = "I am fine, thank you.",
                            translatedText = "मैं ठीक हूँ, धन्यवाद।",
                            sender = Sender.RECEIVER,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onClearClick = { viewModel.clearMessages() }
            )
        }
    }
}