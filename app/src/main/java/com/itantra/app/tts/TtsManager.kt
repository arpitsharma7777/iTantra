package com.itantra.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
    private var initialized = false

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        Log.d(TAG, "Initializing TTS engine")
        _state.value = TtsState.INITIALIZING
        _lastError.value = null

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS engine initialized successfully")
                initialized = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS playback started: $utteranceId")
                        _state.value = TtsState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS playback completed: $utteranceId")
                        resetStateAfterPlayback()
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        Log.d(TAG, "TTS playback stopped: $utteranceId | interrupted=$interrupted")
                        resetStateAfterPlayback()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS playback error: $utteranceId")
                        _lastError.value = "Speech playback failed"
                        _state.value = TtsState.ERROR
                        resetStateAfterPlayback()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS playback error (code $errorCode): $utteranceId")
                        _lastError.value = "Speech playback failed (error $errorCode)"
                        _state.value = TtsState.ERROR
                        resetStateAfterPlayback()
                    }
                })
                _state.value = TtsState.READY
            } else {
                Log.e(TAG, "TTS engine initialization failed: $status")
                initialized = false
                _lastError.value = "TTS initialization failed"
                _state.value = TtsState.ERROR
            }
        }
    }

    private fun resetStateAfterPlayback() {
        if (_state.value != TtsState.SHUTDOWN) {
            _state.value = TtsState.READY
        }
    }

    fun speak(text: String, language: Language) {
        val engine = tts
        if (engine == null || _state.value == TtsState.SHUTDOWN) {
            Log.e(TAG, "speak() failed: TTS not initialized or shutdown")
            return
        }
        
        if (!initialized) {
            Log.w(TAG, "speak() called but initialization is still in progress")
            return
        }
        
        if (text.isBlank()) return

        Log.d(TAG, "speak() in $language: \"$text\"")

        val locale = when (language) {
            Language.HINDI -> Locale("hi", "IN")
            Language.GUJARATI -> Locale("gu", "IN")
            Language.MARATHI -> Locale("mr", "IN")
            Language.KANNADA -> Locale("kn", "IN")
            Language.MALAYALAM -> Locale("ml", "IN")
            Language.TAMIL -> Locale("ta", "IN")
            Language.TELUGU -> Locale("te", "IN")
            Language.ODIA -> Locale("or", "IN")
            Language.BENGALI -> Locale("bn", "IN")
            Language.ENGLISH -> Locale.US
        }

        try {
            val langResult = engine.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language $language not supported or data missing")
                _lastError.value = "Language $language not supported"
                return
            }

            val utteranceId = "utt_${System.currentTimeMillis()}"
            // Use QUEUE_FLUSH to immediately play the latest message
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "engine.speak() returned ERROR")
                _lastError.value = "Failed to start speech"
                _state.value = TtsState.READY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during speak()", e)
            _state.value = TtsState.READY
        }
    }

    fun stop() {
        Log.d(TAG, "stop() requested")
        try {
            tts?.stop()
        } finally {
            if (_state.value != TtsState.SHUTDOWN) {
                _state.value = TtsState.READY
            }
        }
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() requested")
        initialized = false
        _state.value = TtsState.SHUTDOWN
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS shutdown", e)
        } finally {
            tts = null
        }
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
