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
        _state.value = SttState.LOADING
        _lastError.value = null
        currentLanguage = language

        StorageService.unpack(
            context,
            modelPathFor(language),
            "model",
            { unpackedModel ->
                if (loadId != activeLoadId) {
                    unpackedModel.close()
                    return@unpack
                }
                model = unpackedModel
                _state.value = SttState.READY
            },
            { exception ->
                if (loadId != activeLoadId) return@unpack
                _lastError.value = "Model load failed: ${exception.message}"
                _state.value = SttState.ERROR
            }
        )
    }

    fun switchLanguage(language: Language) {
        cancelListening()
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
        initialize(language)
    }

    fun startListening(): Int {
        val loadedModel = model
        if (loadedModel == null) {
            _lastError.value = "Cannot start listening: model not ready"
            _state.value = SttState.ERROR
            return -1
        }
        if (_state.value == SttState.ERROR) {
            _state.value = SttState.READY
        }
        if (_state.value == SttState.LISTENING) {
            return activeSessionId
        }
        if (_state.value != SttState.READY) {
            _lastError.value = "Cannot start listening: model is ${_state.value}"
            return -1
        }

        val language = currentLanguage ?: run {
            _lastError.value = "Cannot start listening: no language selected"
            return -1
        }
        val sessionId = ++sessionCounter
        activeSessionId = sessionId

        try {
            val recognizer = Recognizer(loadedModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    Log.d(TAG, "Partial: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        _result.value = SttResult(sessionId, language, partialText = text)
                    }
                }

                override fun onResult(hypothesis: String?) {
                    Log.d(TAG, "Result: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        // Treat Vosk's onResult (sentence detected) as a final text event
                        // so CommunicationManager can send it immediately.
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    Log.d(TAG, "Final Result: $hypothesis")
                    if (sessionId != activeSessionId) return
                    val text = extractText(hypothesis)
                    if (text.isNotEmpty()) {
                        _result.value = SttResult(sessionId, language, finalText = text)
                    }
                    activeSessionId = -1
                    _state.value = SttState.READY
                }

                override fun onError(exception: Exception?) {
                    Log.e(TAG, "Error: ${exception?.message}", exception)
                    if (sessionId != activeSessionId) return
                    _lastError.value = "Recognition error: ${exception?.message}"
                    activeSessionId = -1
                    _state.value = SttState.ERROR
                }

                override fun onTimeout() {
                    if (sessionId != activeSessionId) return
                    activeSessionId = -1
                    _state.value = SttState.READY
                }
            })
            _state.value = SttState.LISTENING
            return sessionId
        } catch (e: IOException) {
            _lastError.value = "Failed to start recognizer: ${e.message}"
            activeSessionId = -1
            _state.value = SttState.ERROR
            return -1
        } catch (e: RuntimeException) {
            _lastError.value = "Failed to start recognizer: ${e.message}"
            activeSessionId = -1
            _state.value = SttState.ERROR
            return -1
        }
    }

    fun stopListening() {
        speechService?.stop()
        if (_state.value == SttState.LISTENING) {
            _state.value = SttState.READY
        }
    }

    fun cancelListening() {
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
            json.optString("text").ifBlank { json.optString("partial") }
        }.getOrDefault("")
    }

    companion object {
        private const val TAG = "SttManager"
    }
}
