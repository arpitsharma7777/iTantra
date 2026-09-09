package com.itantra.app.ui.state

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.LanguageVaultManager
import com.itantra.app.stt.SttManager
import com.itantra.app.stt.SttState
import com.itantra.app.stt.EnergyVadManager
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
    val vaultManager: LanguageVaultManager?

    init {
        val appContext = context?.applicationContext
        if (appContext == null) {
            transportManager = null
            sttManager = null
            ttsManager = null
            vaultManager = null
        } else {
            vaultManager = LanguageVaultManager(appContext)
            vaultManager.initializeFromAssets()

            transportManager = TransportManager(appContext)
            val vadManager = EnergyVadManager()
            sttManager = SttManager(appContext, vadManager, vaultManager)
            ttsManager = TtsManager(appContext, vaultManager)

            bindManagers()
            ttsManager.initialize()

            updateDownloadedLanguages()

            val selectedLang = _uiState.value.selectedLanguage
            val langCode = selectedLang.indicConformerCode
            val modelAvailable = langCode != null && (
                vaultManager.isModelDownloaded(langCode) ||
                vaultManager.isModelPreloaded(langCode)
            )
            if (modelAvailable) {
                sttManager.initialize(selectedLang)
            } else {
                Log.d(TAG, "Selected language $selectedLang not available, STT not initialized")
            }
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
                if (state == SttState.READY && _uiState.value.isRecording) {
                    _uiState.update { it.copy(isRecording = false) }
                }
            }
        }
        viewModelScope.launch {
            stt.result.collect { result ->
                if (result != null) {
                    val partial = result.partialText ?: ""
                    val final = result.finalText ?: ""
                    _uiState.update {
                        it.copy(
                            partialText = partial,
                            recognizedText = final.ifBlank { partial }
                        )
                    }
                    if (final.isNotBlank() && !_uiState.value.isRecording) {
                        autoSend(final)
                        prePrepareNextLanguage()
                    }
                }
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

    private fun updateDownloadedLanguages() {
        val vm = vaultManager ?: return
        val downloaded = vm.getDownloadedLanguages()
        _uiState.update {
            it.copy(
                downloadedLanguages = downloaded,
                downloadedCount = downloaded.size,
            )
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

    fun isLanguageDownloaded(language: Language): Boolean {
        val langCode = language.indicConformerCode ?: return false
        return vaultManager?.isModelDownloaded(langCode) ?: false
    }

    fun toggleRecording() {
        Log.d(TAG, "toggleRecording() called, isRecording=${_uiState.value.isRecording}")
        clearError()
        if (_uiState.value.isRecording) {
            stopSpeaking()
        } else {
            startSpeaking()
        }
    }

    fun startSpeaking() {
        Log.d(TAG, "startSpeaking() called")
        clearError()
        if (sttManager == null) {
            showError("Speech is not available")
            return
        }
        val language = _uiState.value.selectedLanguage
        if (!isLanguageDownloaded(language)) {
            showError("Please download ${language.displayName} model in Language Vault first")
            return
        }
        val sessionId = sttManager.startListening()
        if (sessionId == -1) {
            showError(sttManager.lastError.value ?: "Failed to start listening")
        } else {
            _uiState.update { it.copy(isRecording = true, partialText = "", recognizedText = "") }
        }
    }

    fun stopSpeaking() {
        Log.d(TAG, "stopSpeaking() called")
        sttManager?.stopListening()
    }

    private fun autoSend(text: String) {
        val transport = transportManager
        if (transport == null || text.isBlank()) return

        viewModelScope.launch {
            val message = com.itantra.app.communication.MessageFactory.createOutgoingMessage(
                text = text,
                language = _uiState.value.selectedLanguage,
                senderId = "local"
            )
            try {
                transport.send(message)
                _uiState.update { it.copy(messages = it.messages + message, partialText = "", recognizedText = "") }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-send failed: ${e.message}")
                showError("Failed to send: ${e.message}")
            }
        }
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

    fun refreshDownloadedLanguages() {
        updateDownloadedLanguages()
    }

    private fun prePrepareNextLanguage() {
        val stt = sttManager ?: return
        val current = _uiState.value.selectedLanguage
        val downloaded = _uiState.value.downloadedLanguages
        val next = downloaded.firstOrNull { it != current } ?: return
        stt.prePrepareLanguage(next)
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
