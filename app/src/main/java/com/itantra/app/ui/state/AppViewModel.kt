package com.itantra.app.ui.state

import androidx.lifecycle.ViewModel
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun updateConnectionState(state: ConnectionState) {
        _uiState.update { it.copy(connectionState = state) }
    }

    fun setLanguage(language: Language) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun startDiscovering() {
        _uiState.update { it.copy(connectionState = ConnectionState.Discovering) }
    }

    fun setConnectedDevice(deviceName: String) {
        _uiState.update { 
            it.copy(
                connectionState = ConnectionState.Connected(deviceName),
                connectedDeviceName = deviceName
            )
        }
    }

    fun disconnect() {
        _uiState.update { 
            it.copy(
                connectionState = ConnectionState.Disconnected,
                connectedDeviceName = null
            )
        }
    }

    fun addMessage(message: Message) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList()) }
    }
}