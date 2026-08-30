package com.itantra.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.ui.state.AppUiState

@Composable
fun HomeScreen(
    uiState: AppUiState,
    onConnectDevice: () -> Unit,
    onStartCommunication: () -> Unit,
    onSettings: () -> Unit,
    onClearError: () -> Unit = {}
) {
    val isDisconnected = uiState.connectionState == ConnectionState.DISCONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "iTantra", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Indian Multilingual Communication",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        val statusText = when (uiState.connectionState) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.DISCONNECTED -> "Disconnected"
            ConnectionState.DISCOVERING -> "Discovering"
            else -> uiState.connectionState.name
        }
        Text(
            text = "Status: $statusText",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDisconnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onClearError) {
                Text("Dismiss")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onConnectDevice) {
            Text("Connect Device")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isDisconnected) {
            Text(
                text = "Please connect a device first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        Button(
            onClick = onStartCommunication,
            enabled = !isDisconnected
        ) {
            Text("Start Communication")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onSettings) {
            Text("Settings")
        }
    }
}
