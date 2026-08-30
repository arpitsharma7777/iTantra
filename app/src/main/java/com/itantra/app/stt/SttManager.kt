package com.itantra.app.stt

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

enum class SttState {
    IDLE,
    LOADING,
    READY,
    LISTENING,
    ERROR
}

data class SttResult(
    val sessionId: Int,
    val language: Language,
    val partialText: String? = null,
    val finalText: String? = null
)

class SttManager(private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null

    private var activeSessionId: Int = -1
    private var sessionCounter: Int = 0
    private var activeLoadId: Int = 0
    private var currentLanguage: Language? = null

    private val _state = MutableStateFlow(SttState.IDLE)
    val state: StateFlow<SttState> = _state.asStateFlow()

    private val _result = MutableStateFlow<SttResult?>(null)
    val result: StateFlow<SttResult?> = _result.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private fun modelPathFor(language: Language): String = when (language) {
        Language.HINDI -> "model-hi"
        Language.ENGLISH -> "model-en"
    }

    fun initialize(language: Language) {
        val loadId = ++activeLoadId
        Log.d(TAG, "Initializing STT for $language (loadId: $loadId)")
        _state.value = SttState.LOADING
        _lastError.value = null
        currentLanguage = language

        StorageService.unpack(
            context,
            modelPathFor(language),
            "model",
            { unpackedModel ->
                if (loadId != activeLoadId) {
                    Log.w(TAG, "Stale model load finished for $language, discarding")
                    unpackedModel.close()
                    return@unpack
                }
                Log.d(TAG, "Model unpacked successfully for $language")
                model = unpackedModel
                _state.value = SttState.READY
            },
            { exception ->
                if (loadId != activeLoadId) return@unpack
                Log.e(TAG, "Model load failed for $language", exception)
                _lastError.value = "Model load failed: ${exception.message}"
                _state.value = SttState.ERROR
            }
        )
    }

    fun switchLanguage(language: Language) {
        Log.d(TAG, "switchLanguage() to $language requested")
        cancelListening()
        
        // Ensure clean slate
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
        
        initialize(language)
    }

    fun startListening(): Int {
        val loadedModel = model
        if (loadedModel == null) {
            Log.e(TAG, "startListening() failed: model not ready")
            _lastError.value = "Cannot start listening: model not ready"
            _state.value = SttState.ERROR
            return -1
        }
        
        if (_state.value == SttState.LISTENING) {
            Log.w(TAG, "Already listening, returning active session ID $activeSessionId")
            return activeSessionId
        }

        val language = currentLanguage ?: run {
            Log.e(TAG, "startListening() failed: no language selected")
            _lastError.value = "Cannot start listening: no language selected"
            return -1
        }
        
        val sessionId = ++sessionCounter
        activeSessionId = sessionId
        Log.d(TAG, "Starting listening session $sessionId for $language")

        try {
            // Recreating the recognizer each time is recommended by Vosk for fresh state
            val recognizer = Recognizer(loadedModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        Log.v(TAG, "Partial ($sessionId): \"$text\"")
                        _result.value = SttResult(sessionId, language, partialText = text)
                    }
                }

                override fun onResult(hypothesis: String?) {
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "Sentence complete ($sessionId): \"$text\"")
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    if (sessionId != activeSessionId) {
                        Log.d(TAG, "Final result arrived for inactive session $sessionId, ignoring")
                        return
                    }
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "Final session result ($sessionId): \"$text\"")
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                    Log.d(TAG, "STT session $sessionId fully completed")
                    cleanupSession(sessionId)
                }

                override fun onError(exception: Exception?) {
                    if (sessionId != activeSessionId) return
                    Log.e(TAG, "STT session $sessionId error", exception)
                    _lastError.value = "Recognition error: ${exception?.message}"
                    cleanupSession(sessionId, isError = true)
                }

                override fun onTimeout() {
                    if (sessionId != activeSessionId) return
                    Log.d(TAG, "STT session $sessionId timed out")
                    cleanupSession(sessionId)
                }
            }

            if (speechService?.startListening(listener) == true) {
                _state.value = SttState.LISTENING
                return sessionId
            } else {
                Log.e(TAG, "SpeechService.startListening() returned false")
                _lastError.value = "Failed to start microphone"
                cleanupSession(sessionId, isError = true)
                return -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startListening() for session $sessionId", e)
            _lastError.value = "Failed to start recognizer: ${e.message}"
            cleanupSession(sessionId, isError = true)
            return -1
        }
    }

    private fun cleanupSession(sessionId: Int, isError: Boolean = false) {
        val isCurrentSession = sessionId == activeSessionId || activeSessionId == -1
        if (!isCurrentSession) {
            Log.d(TAG, "Ignoring stale cleanup for session $sessionId; active session is $activeSessionId")
            return
        }

        activeSessionId = -1
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "cleanupSession() failed for session $sessionId", e)
        } finally {
            speechService = null
            _state.value = if (isError) SttState.ERROR else SttState.READY
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() requested for session $activeSessionId")
        val sessionId = activeSessionId
        if (sessionId == -1) {
            _state.value = SttState.READY
            return
        }
        try {
            speechService?.stop()
        } finally {
            cleanupSession(sessionId)
        }
    }

    fun cancelListening() {
        Log.d(TAG, "cancelListening() requested for session $activeSessionId")
        activeSessionId = -1
        speechService?.cancel()
        _state.value = SttState.READY
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() requested")
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
        activeSessionId = -1
        activeLoadId++
        _state.value = SttState.IDLE
    }

    private fun extractText(hypothesisJson: String?): String {
        if (hypothesisJson == null) return ""
        return runCatching {
            val json = JSONObject(hypothesisJson)
            // Vosk results can have "text" or "partial" fields
            val text = json.optString("text")
            if (text.isNotBlank()) return text
            json.optString("partial")
        }.getOrDefault("")
    }

    companion object {
        private const val TAG = "SttManager"
    }
}
