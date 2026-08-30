package com.itantra.app.ui.state

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.itantra.app.communication.CommunicationManager
import com.itantra.app.communication.SenderIdProvider
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.SttManager
import com.itantra.app.transport.TransportManager
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppViewModel(
    context: Context? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val transportManager: TransportManager?
    private val sttManager: SttManager?
    private val ttsManager: TtsManager?
    private val communicationManager: CommunicationManager?

    init {
        val appContext = context?.applicationContext
        if (appContext == null) {
            transportManager = null
            sttManager = null
            ttsManager = null
            communicationManager = null
        } else {
            transportManager = TransportManager(appContext)
            sttManager = SttManager(appContext)
            ttsManager = TtsManager(appContext)
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            communicationManager = CommunicationManager(
                transportManager = transportManager,
                sttManager = sttManager,
                ttsManager = ttsManager,
                senderIdProvider = SenderIdProvider(prefs)
            )

            bindManagers()
            ttsManager.initialize()
            sttManager.initialize(Language.ENGLISH)
        }
    }

    private fun bindManagers() {
        val transport = transportManager ?: return
        val stt = sttManager ?: return
        val tts = ttsManager ?: return
        val communication = communicationManager ?: return

        viewModelScope.launch {
            transport.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            transport.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }
        viewModelScope.launch {
            transport.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedDeviceName = name) }
            }
        }
        viewModelScope.launch {
            communication.selectedLanguage.collect { language ->
                _uiState.update { it.copy(selectedLanguage = language) }
            }
        }
        viewModelScope.launch {
            communication.messages.collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            communication.error.collect { message ->
                showError(message)
            }
        }
        viewModelScope.launch {
            transport.errorEvents.collect { message ->
                showError(message)
            }
        }
        viewModelScope.launch {
            stt.state.collect { state ->
                _uiState.update { it.copy(sttState = state) }
            }
        }
        viewModelScope.launch {
            stt.lastError.collect { message ->
                message?.let { showError(it) }
            }
        }
        viewModelScope.launch {
            tts.state.collect { state ->
                _uiState.update { it.copy(ttsState = state) }
            }
        }
        viewModelScope.launch {
            tts.lastError.collect { message ->
                message?.let { showError(it) }
            }
        }
    }

    fun startDiscovery() {
        Log.d(TAG, "startDiscovery() called")
        val transport = transportManager
        if (transport == null) {
            showError("Transport is not available")
            return
        }
        clearError()
        transport.startDiscovery()
    }

    fun connectToDevice(device: WifiDirectDevice) {
        Log.d(TAG, "connectToDevice() called for ${device.name}")
        val transport = transportManager
        if (transport == null) {
            showError("Transport is not available")
            return
        }
        clearError()
        viewModelScope.launch {
            val result = transport.connect(device)
            result.onFailure { showError(it.message ?: "Connection failed") }
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called")
        transportManager?.disconnect()
    }

    fun updateSelectedLanguage(language: Language) {
        Log.d(TAG, "updateSelectedLanguage() called for $language")
        communicationManager?.setLanguage(language)
            ?: _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun startSpeaking() {
        Log.d(TAG, "startSpeaking() called")
        clearError()
        communicationManager?.startSpeaking() ?: showError("Speech is not available")
    }

    fun stopSpeaking() {
        Log.d(TAG, "stopSpeaking() called")
        communicationManager?.stopSpeaking()
    }

    fun showError(message: String) {
        Log.e(TAG, "showError(): $message")
        _uiState.update { it.copy(errorMessage = message) }
        // Automatically clear error after a delay
        viewModelScope.launch {
            delay(5000)
            if (_uiState.value.errorMessage == message) {
                clearError()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateConnectionState(state: ConnectionState) {
        _uiState.update { it.copy(connectionState = state) }
    }

    fun updateConnectedDevice(name: String?) {
        _uiState.update { it.copy(connectedDeviceName = name) }
    }

    fun addMessage(message: Message) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    override fun onCleared() {
        communicationManager?.cleanup()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                return AppViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val TAG = "AppViewModel"
        private const val PREFS_NAME = "itantra_prefs"
    }
}
