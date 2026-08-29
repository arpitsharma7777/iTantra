package com.itantra.app.ui.state

import androidx.lifecycle.ViewModel
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.core.model.Sender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState(
        messages = listOf(
            Message("1", "Hello! How can I help you?", Sender.REMOTE, System.currentTimeMillis() - 10000),
            Message("2", "I need help with translation.", Sender.LOCAL, System.currentTimeMillis())
        )
    ))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun updateConnectionState(state: ConnectionState) {
        _uiState.update { it.copy(connectionState = state) }
    }

    fun updateSelectedLanguage(language: Language) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun updateConnectedDevice(name: String?) {
        _uiState.update { it.copy(connectedDeviceName = name) }
    }

    fun addMessage(message: Message) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }
}
