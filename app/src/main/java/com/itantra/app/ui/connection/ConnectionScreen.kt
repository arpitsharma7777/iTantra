package com.itantra.app.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.ui.state.AppUiState

@Composable
fun ConnectionScreen(
    uiState: AppUiState,
    onDiscoverClicked: () -> Unit,
    onConnectClicked: (WifiDirectDevice) -> Unit,
    onDisconnectClicked: () -> Unit,
    onNavigateToCommunication: () -> Unit,
    onClearError: () -> Unit = {},
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connection Manager", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        ErrorMessage(uiState.errorMessage, onClearError)

        when (uiState.connectionState) {
            ConnectionState.DISCONNECTED -> {
                DisconnectedContent(
                    hasDevices = uiState.discoveredDevices.isNotEmpty(),
                    devices = uiState.discoveredDevices,
                    onDiscoverClicked = onDiscoverClicked,
                    onConnectClicked = onConnectClicked
                )
            }

            ConnectionState.DISCOVERING -> {
                DiscoveringContent(
                    devices = uiState.discoveredDevices,
                    onDiscoverClicked = onDiscoverClicked,
                    onConnectClicked = onConnectClicked
                )
            }

            ConnectionState.CONNECTING -> {
                ConnectingContent(onDisconnectClicked)
            }

            ConnectionState.CONNECTED -> {
                ConnectedContent(
                    deviceName = uiState.connectedDeviceName ?: "Connected device",
                    onDisconnectClicked = onDisconnectClicked,
                    onNavigateToCommunication = onNavigateToCommunication
                )
            }

            ConnectionState.ERROR -> {
                ErrorContent(
                    onDiscoverClicked = onDiscoverClicked,
                    onDisconnectClicked = onDisconnectClicked
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
private fun ErrorMessage(message: String?, onClearError: () -> Unit) {
    if (message == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClearError) {
                Text("Dismiss")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun DisconnectedContent(
    hasDevices: Boolean,
    devices: List<WifiDirectDevice>,
    onDiscoverClicked: () -> Unit,
    onConnectClicked: (WifiDirectDevice) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "No Device Connected", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDiscoverClicked) {
            Text(if (hasDevices) "Refresh Devices" else "Discover Devices")
        }
        if (hasDevices) {
            Spacer(modifier = Modifier.height(16.dp))
            DeviceList(devices, enabled = true, onConnectClicked = onConnectClicked)
        }
    }
}

@Composable
private fun DiscoveringContent(
    devices: List<WifiDirectDevice>,
    onDiscoverClicked: () -> Unit,
    onConnectClicked: (WifiDirectDevice) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = "Searching for nearby devices...", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onDiscoverClicked) {
            Text("Search Again")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (devices.isEmpty()) {
            Text(
                text = "No devices found yet. Keep both devices nearby with Wi-Fi and Location enabled.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            DeviceList(devices, enabled = true, onConnectClicked = onConnectClicked)
        }
    }
}

@Composable
private fun ConnectingContent(onDisconnectClicked: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Connecting...", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onDisconnectClicked) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ErrorContent(
    onDiscoverClicked: () -> Unit,
    onDisconnectClicked: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Connection failed",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDiscoverClicked) {
            Text("Try Again")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onDisconnectClicked) {
            Text("Reset Connection")
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<WifiDirectDevice>,
    enabled: Boolean,
    onConnectClicked: (WifiDirectDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(devices, key = { it.address }) { device ->
            DeviceItem(device, enabled, onConnectClicked)
        }
    }
}

@Composable
private fun DeviceItem(
    device: WifiDirectDevice,
    enabled: Boolean,
    onConnectClicked: (WifiDirectDevice) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onConnectClicked(device) },
                enabled = enabled
            ) {
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
