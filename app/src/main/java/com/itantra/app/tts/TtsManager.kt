package com.itantra.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.itantra.app.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class TtsState {
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR,
    SHUTDOWN
}

class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        _state.value = TtsState.INITIALIZING
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = TtsState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_state.value != TtsState.SHUTDOWN) {
                            _state.value = TtsState.READY
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _lastError.value = "Speech failed for utterance: $utteranceId"
                        _state.value = TtsState.ERROR
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _lastError.value = "Speech failed (code $errorCode) for: $utteranceId"
                        _state.value = TtsState.ERROR
                    }
                })
                _state.value = TtsState.READY
            } else {
                _lastError.value = "TTS engine initialization failed (status: $status)"
                _state.value = TtsState.ERROR
            }
        }
    }

    fun speak(text: String, language: Language) {
        val engine = tts
        if (engine == null || _state.value == TtsState.ERROR || _state.value == TtsState.SHUTDOWN) {
            _lastError.value = "Cannot speak: TTS not ready"
            _state.value = TtsState.ERROR
            return
        }

        val locale = when (language) {
            Language.HINDI -> Locale("hi", "IN")
            Language.ENGLISH -> Locale.US
        }

        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _lastError.value = "Language not available: $language"
            _state.value = TtsState.ERROR
            return
        }

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val speakResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        if (speakResult == TextToSpeech.ERROR) {
            _lastError.value = "speak() call failed"
            _state.value = TtsState.ERROR
        }
        // state transitions to SPEAKING/READY are handled by the UtteranceProgressListener
    }

    fun stop() {
        tts?.stop()
        if (_state.value != TtsState.SHUTDOWN && _state.value != TtsState.ERROR) {
            _state.value = TtsState.READY
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _state.value = TtsState.SHUTDOWN
    }
}

