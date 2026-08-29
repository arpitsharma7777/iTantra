package com.itantra.app.stt

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

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

    private val scope = CoroutineScope(Dispatchers.Main)

    private var activeSessionId: Int = -1
    private var sessionCounter: Int = 0
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
        _state.value = SttState.LOADING
        currentLanguage = language

        StorageService.unpack(
            context,
            modelPathFor(language),
            "model",
            { unpackedModel ->
                model = unpackedModel
                _state.value = SttState.READY
            },
            { exception ->
                _lastError.value = "Model load failed: ${exception.message}"
                _state.value = SttState.ERROR
            }
        )
    }

    fun switchLanguage(language: Language) {
        // Cancel any active session first so stale results can't leak into the new language
        cancelListening()

        speechService?.stop()
        speechService = null
        model = null

        initialize(language)
    }

    fun startListening() {
        val loadedModel = model
        if (loadedModel == null || _state.value != SttState.READY) {
            _lastError.value = "Cannot start listening: model not ready"
            _state.value = SttState.ERROR
            return
        }
        if (_state.value == SttState.LISTENING) {
            // Already listening — do not start a second parallel session
            return
        }

        val sessionId = ++sessionCounter
        activeSessionId = sessionId
        val language = currentLanguage ?: return

        try {
            val recognizer = Recognizer(loadedModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    Log.d("SttManager", "Partial: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        _result.value = SttResult(sessionId, language, partialText = text)
                    }
                }

                override fun onResult(hypothesis: String?) {
                    Log.d("SttManager", "Result: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                    // Keep LISTENING state to allow consecutive sentences
                }

                override fun onFinalResult(hypothesis: String?) {
                    Log.d("SttManager", "Final Result: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                    _state.value = SttState.READY
                }

                override fun onError(exception: Exception?) {
                    Log.e("SttManager", "Error: ${exception?.message}", exception)
                    if (sessionId != activeSessionId) return
                    _lastError.value = "Recognition error: ${exception?.message}"
                    _state.value = SttState.ERROR
                }

                override fun onTimeout() {
                    if (sessionId != activeSessionId) return
                    _state.value = SttState.READY
                }
            })
            _state.value = SttState.LISTENING
        } catch (e: IOException) {
            _lastError.value = "Failed to start recognizer: ${e.message}"
            _state.value = SttState.ERROR
        }
    }

    fun stopListening() {
        // Normal stop — allows final result to be emitted
        speechService?.stop()
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.READY
        }
    }

    fun cancelListening() {
        // Invalidate the session so any late results are discarded
        activeSessionId = -1
        speechService?.cancel()
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.READY
        }
    }

    fun shutdown() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model = null
        activeSessionId = -1
        _state.value = SttState.IDLE
    }

    private fun extractText(hypothesisJson: String?): String {
        // Vosk returns JSON like {"text" : "..."} or {"partial" : "..."}
        // Extract just the text value cleanly rather than exposing raw JSON
        if (hypothesisJson == null) return ""
        val regex = Regex("\"(?:text|partial)\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(hypothesisJson)?.groupValues?.get(1) ?: ""
    }
}