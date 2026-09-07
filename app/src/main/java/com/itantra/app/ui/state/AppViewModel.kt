package com.itantra.app.ui.state

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.SttManager
import com.itantra.app.stt.SttState
import com.itantra.app.stt.VadManager
import com.itantra.app.transport.TransportManager
import com.itantra.app.transport.WifiDirectDevice
import com.itantra.app.tts.TtsManager
import com.itantra.app.tts.TtsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    context: Context? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val transportManager: TransportManager?
    private val sttManager: SttManager?
    private val ttsManager: TtsManager?

    init {
        val appContext = context?.applicationContext
        if (appContext == null) {
            transportManager = null
            sttManager = null
            ttsManager = null
        } else {
            transportManager = TransportManager(appContext)
            val vadManager = VadManager(appContext)
            sttManager = SttManager(appContext, vadManager)
            ttsManager = TtsManager(appContext)

            bindManagers()
            ttsManager.initialize()
            sttManager.initialize(_uiState.value.selectedLanguage)
        }
    }

    private fun bindManagers() {
        val transport = transportManager ?: return
        val stt = sttManager ?: return
        val tts = ttsManager ?: return

        viewModelScope.launch {
            transport.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        connectedDeviceName = (state as? ConnectionState.Connected)?.deviceName
                    )
                }
            }
        }
        viewModelScope.launch {
            transport.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
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
        viewModelScope.launch {
            transport.incomingMessages.collect { message ->
                _uiState.update { it.copy(messages = it.messages + message) }
                try {
                    tts?.speak(message.translatedText ?: message.text, _uiState.value.selectedLanguage)
                } catch (e: Exception) {
                    Log.e(TAG, "TTS playback failed: ${e.message}")
                }
            }
        }
    }

    fun startDiscovering() {
        Log.d(TAG, "startDiscovering() called")
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
            _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
            val result = transport.connect(device)
            result.onFailure { showError(it.message ?: "Connection failed") }
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called")
        transportManager?.disconnect()
    }

    fun setLanguage(language: Language) {
        Log.d(TAG, "setLanguage() called for $language")
        _uiState.update { it.copy(selectedLanguage = language) }
        sttManager?.switchLanguage(language)
    }

    fun startSpeaking() {
        Log.d(TAG, "startSpeaking() called")
        clearError()
        if (sttManager == null) {
            showError("Speech is not available")
            return
        }
        val sessionId = sttManager.startListening()
        if (sessionId == -1) {
            showError(sttManager.lastError.value ?: "Failed to start listening")
        }
    }

    fun stopSpeaking() {
        Log.d(TAG, "stopSpeaking() called")
        sttManager?.stopListening()
    }

    fun sendCurrentMessage() {
        val transport = transportManager
        val stt = sttManager
        if (transport == null || stt == null) {
            showError("Transport or STT not available")
            return
        }

        val result = stt.result.value
        val text = result?.finalText ?: result?.partialText
        if (text.isNullOrBlank()) {
            showError("No speech recognized to send")
            return
        }

        viewModelScope.launch {
            val message = com.itantra.app.communication.MessageFactory.createOutgoingMessage(
                text = text,
                language = _uiState.value.selectedLanguage,
                senderId = "local"
            )
            try {
                transport.send(message)
                _uiState.update { it.copy(messages = it.messages + message) }
            } catch (e: Exception) {
                showError("Failed to send message: ${e.message}")
            }
        }
    }

    fun addMessage(message: Message) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun showError(message: String) {
        Log.e(TAG, "showError(): $message")
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        sttManager?.shutdown()
        ttsManager?.shutdown()
        transportManager?.cleanup()
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
    }
}
