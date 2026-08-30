package com.itantra.app.communication

import com.itantra.app.core.model.ConnectionState
import com.itantra.app.core.model.Language
import com.itantra.app.core.model.Message
import com.itantra.app.stt.SttManager
import com.itantra.app.stt.SttResult
import com.itantra.app.stt.SttState
import com.itantra.app.transport.TransportManager
import com.itantra.app.tts.TtsManager
import com.itantra.app.tts.TtsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates communication between STT, TTS, and Transport layers.
 * Acts as the primary integration layer for the app's messaging features.
 */
class CommunicationManager(
    private val transportManager: TransportManager,
    private val sttManager: SttManager,
    private val ttsManager: TtsManager,
    private val senderIdProvider: SenderIdProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _selectedLanguage = MutableStateFlow(Language.ENGLISH)
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    /**
     * History of all sent and received messages.
     * Most recent messages are at the end of the list.
     */
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    /**
     * One-off user-facing errors (e.g., transport failures or invalid states).
     */
    val error: SharedFlow<String> = _error.asSharedFlow()

    private var expectedSessionId: Int = -1
    private var expectedSessionLanguage: Language? = null
    private var lastSentTextInSession: String? = null
    private var pendingIncomingMessage: Message? = null

    /**
     * Current state of the Speech-to-Text engine.
     */
    val sttState: StateFlow<SttState> = sttManager.state

    /**
     * Current state of the Text-to-Speech engine.
     */
    val ttsState: StateFlow<TtsState> = ttsManager.state

    /**
     * Current state of the transport connection (e.g., P2P and Socket status).
     */
    val connectionState: StateFlow<ConnectionState> = transportManager.connectionState

    init {
        // Collect STT results
        scope.launch {
            sttManager.result.collect { result ->
                if (result?.finalText != null) {
                    handleFinalResult(result)
                }
            }
        }

        // Collect incoming messages from Transport
        scope.launch {
            try {
                transportManager.incomingMessages.collect { message ->
                    // 1. Add to UI state immediately
                    _messages.update { it + message }

                    // 2 & 3. Handle TTS gating based on STT state
                    if (sttState.value == SttState.LISTENING) {
                        // Store as pending to avoid speaking over the user
                        pendingIncomingMessage = message
                    } else {
                        // Speak immediately
                        ttsManager.speak(message.text, message.language)
                    }
                }
            } catch (e: Exception) {
                _error.emit("Message receiver failed: ${e.message}")
            }
        }

        // 4. Handle Speaking "Pending" messages when STT finishes
        scope.launch {
            var wasListening = false
            sttState.collect { state ->
                val isListening = state == SttState.LISTENING
                if (wasListening && !isListening) {
                    // Transitioned away from LISTENING (READY, ERROR, or IDLE)
                    pendingIncomingMessage?.let {
                        ttsManager.speak(it.text, it.language)
                        pendingIncomingMessage = null
                    }
                }
                wasListening = isListening
            }
        }
    }

    private suspend fun handleFinalResult(result: SttResult) {
        val text = result.finalText ?: return
        if (text.isBlank()) return

        // Verify the sessionId matches the current expected ID
        if (result.sessionId != expectedSessionId) return

        // Avoid duplicate sends if onResult and onFinalResult report the same text
        if (text == lastSentTextInSession) return
        lastSentTextInSession = text
        
        // Use the Language that was active when THIS specific sessionId was started
        val lang = expectedSessionLanguage ?: return

        // Final connection check before attempting to send
        if (connectionState.value != ConnectionState.CONNECTED) {
            _error.emit("Failed to send: Device disconnected mid-session")
            return
        }

        val message = MessageFactory.createOutgoingMessage(
            text = text,
            language = lang,
            senderId = senderIdProvider.getSenderId()
        )

        try {
            transportManager.send(message)
            _messages.update { it + message }
        } catch (e: Exception) {
            _error.emit("Failed to send message: ${e.message}")
        }
    }

    /**
     * Without session invalidation, a Hindi recognition result arriving
     * after the user switched to English could be sent as an English-tagged message
     * containing Hindi text, or attached to the wrong language entirely.
     */
    fun setLanguage(language: Language) {
        _selectedLanguage.value = language

        if (sttState.value == SttState.LISTENING) {
            // Invalidate the session so any late results from the old language are discarded
            expectedSessionId = -1
        }

        sttManager.switchLanguage(language)
    }

    /**
     * Starts the voice recognition process.
     */
    fun startSpeaking() {
        scope.launch {
            if (connectionState.value != ConnectionState.CONNECTED) {
                _error.emit("Cannot start speaking: Device not connected")
                return@launch
            }

            if (sttState.value != SttState.READY) {
                val status = when (sttState.value) {
                    SttState.LOADING -> "Initializing speech engine..."
                    SttState.ERROR -> "Speech engine error. Try changing language."
                    SttState.IDLE -> "Speech engine not initialized."
                    else -> "Speech engine busy."
                }
                _error.emit(status)
                return@launch
            }

            // Interrupt TTS if it's currently speaking
            if (ttsState.value == TtsState.SPEAKING) {
                ttsManager.stop()
                ttsState.first { it != TtsState.SPEAKING }
            }

            // Capture current language for this session
            val lang = selectedLanguage.value

            // Start listening and get the sessionId generated by SttManager
            val sessionId = sttManager.startListening()
            
            if (sessionId != -1) {
                expectedSessionId = sessionId
                expectedSessionLanguage = lang
                lastSentTextInSession = null
            } else {
                _error.emit("Failed to start listening")
            }
        }
    }

    /**
     * Stops the voice recognition process and cancels any active session.
     */
    fun stopSpeaking() {
        sttManager.stopListening()
    }

    fun cleanup() {
        scope.cancel()
        transportManager.cleanup()
        sttManager.shutdown()
        ttsManager.shutdown()
    }
}
