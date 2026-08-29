package com.itantra.app.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.ui.state.AppUiState
import kotlinx.coroutines.delay

@Composable
fun ConnectionScreen(
    uiState: AppUiState,
    onDiscoverClicked: () -> Unit,
    onConnectClicked: (String) -> Unit,
    onDisconnectClicked: () -> Unit,
    onNavigateToCommunication: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connection Manager", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        when (val state = uiState.connectionState) {
            is ConnectionState.Disconnected -> {
                DisconnectedContent(onDiscoverClicked)
            }
            is ConnectionState.Discovering -> {
                DiscoveringContent(onConnectClicked)
            }
            is ConnectionState.Connected -> {
                ConnectedContent(
                    deviceName = state.deviceName,
                    onDisconnectClicked = onDisconnectClicked,
                    onNavigateToCommunication = onNavigateToCommunication
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Home")
        }
    }
}

@Composable
private fun DisconnectedContent(onDiscoverClicked: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "No Device Connected", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDiscoverClicked) {
            Text("Discover Devices")
        }
    }
}

@Composable
private fun DiscoveringContent(onConnectClicked: (String) -> Unit) {
    var devices by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        delay(2000) // Mock discovery delay
        devices = listOf("Device A", "Device B", "Device C")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Searching for nearby devices...", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (devices.isEmpty()) {
            Button(onClick = {}, enabled = false) {
                Text("Searching...")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(device, onConnectClicked)
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(name: String, onConnectClicked: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Button(onClick = { onConnectClicked(name) }) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    deviceName: String,
    onDisconnectClicked: () -> Unit,
    onNavigateToCommunication: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Connected to: $deviceName",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToCommunication, modifier = Modifier.fillMaxWidth()) {
            Text("Start Communication")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDisconnectClicked, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect")
        }
    }
}
