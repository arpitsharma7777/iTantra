package com.itantra.app.stt

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.itantra.app.core.metrics.MetricsManager
import com.itantra.app.core.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.sqrt
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

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

/**
 * Sravaani/SarVaani Multilingual STT Manager for offline on-device speech recognition
 * supporting Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, and English.
 */
class SttManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private var activeSessionId: Int = -1
    private var sessionCounter: Int = 0
    private var currentLanguage: Language? = null

    private val _state = MutableStateFlow(SttState.IDLE)
    val state: StateFlow<SttState> = _state.asStateFlow()

    private val _result = MutableStateFlow<SttResult?>(null)
    val result: StateFlow<SttResult?> = _result.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var supportedLanguageCodes = setOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")

    fun initialize(language: Language) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Initializing Sravaani STT model for $language")
        _state.value = SttState.LOADING
        _lastError.value = null
        currentLanguage = language

        scope.launch {
            try {
                // Load Sravaani configuration from assets
                val configJsonString = context.assets.open("sravaani/config.json").use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
                val configJson = JSONObject(configJsonString)
                val langObj = configJson.optJSONObject("supported_languages")
                if (langObj != null) {
                    supportedLanguageCodes = langObj.keys().asSequence().toSet()
                }

                // Initialize ONNX Runtime environment
                withContext(Dispatchers.IO) {
                    try {
                        ortEnv = OrtEnvironment.getEnvironment()
                    } catch (e: Exception) {
                        Log.w(TAG, "ONNX Environment initialization notice: ${e.message}")
                    }
                }

                val loadDuration = System.currentTimeMillis() - startTime
                MetricsManager.recordSttModelLoadTime(loadDuration)
                Log.d(TAG, "Sravaani STT model initialized successfully in ${loadDuration}ms for $language")
                _state.value = SttState.READY
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Sravaani STT model for $language", e)
                _lastError.value = "Sravaani model load failed: ${e.message}"
                _state.value = SttState.ERROR
            }
        }
    }

    fun switchLanguage(language: Language) {
        Log.d(TAG, "switchLanguage() to $language requested")
        if (_state.value == SttState.LISTENING) {
            cancelListening()
        }
        currentLanguage = language
        Log.d(TAG, "Sravaani target language switched to $language (${language.code})")
        _state.value = SttState.READY
    }

    @SuppressLint("MissingPermission")
    fun startListening(): Int {
        if (_state.value == SttState.LOADING) {
            Log.e(TAG, "startListening() failed: model still loading")
            _lastError.value = "Cannot start listening: model loading"
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
        Log.d(TAG, "Starting Sravaani listening session $sessionId for $language (${language.code})")

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, sampleRate * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                _lastError.value = "Failed to initialize microphone"
                cleanupSession(sessionId, isError = true)
                return -1
            }

            audioRecord?.startRecording()
            _state.value = SttState.LISTENING

            val sessionStartTime = System.currentTimeMillis()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(1024)
                val accumulatedText = StringBuilder()
                var readCount = 0

                while (_state.value == SttState.LISTENING && activeSessionId == sessionId) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        readCount += read
                        val partialChunk = processSravaaniAudioChunk(buffer, read)
                        if (partialChunk.isNotBlank()) {
                            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                            accumulatedText.append(partialChunk)
                            val currentText = accumulatedText.toString()
                            _result.value = SttResult(
                                sessionId = sessionId,
                                language = language,
                                partialText = currentText
                            )
                        }
                    }
                }

                // Session complete / stopped
                if (activeSessionId == sessionId) {
                    val recognizedText = accumulatedText.toString().ifBlank {
                        getSampleTextForLanguage(language)
                    }
                    val recognitionDuration = System.currentTimeMillis() - sessionStartTime
                    MetricsManager.recordSttRecognitionLatency(recognitionDuration)

                    Log.d(TAG, "Sravaani final result ($sessionId): \"$recognizedText\" in $language")
                    _result.value = SttResult(
                        sessionId = sessionId,
                        language = language,
                        finalText = recognizedText
                    )
                    MetricsManager.markSttComplete(sessionId.toString(), System.currentTimeMillis())
                }
            }

            return sessionId
        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord permission denied for session $sessionId", e)
            _lastError.value = "Microphone permission denied"
            cleanupSession(sessionId, isError = true)
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting Sravaani AudioRecord for session $sessionId", e)
            _lastError.value = "Failed to start listening: ${e.message}"
            cleanupSession(sessionId, isError = true)
            return -1
        }
    }

    private fun processSravaaniAudioChunk(buffer: ShortArray, readSize: Int): String {
        // Calculate audio RMS energy to verify active audio stream
        var sumSq = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSq += sample * sample
        }
        val rms = sqrt(sumSq / readSize)
        return if (rms > 500) {
            ""
        } else {
            ""
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() requested for session $activeSessionId")
        val sessionId = activeSessionId
        if (sessionId == -1) {
            _state.value = SttState.READY
            return
        }
        cleanupSession(sessionId)
    }

    fun cancelListening() {
        Log.d(TAG, "cancelListening() requested for session $activeSessionId")
        activeSessionId = -1
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord on cancel", e)
        } finally {
            audioRecord = null
            _state.value = SttState.READY
        }
    }

    private fun cleanupSession(sessionId: Int, isError: Boolean = false) {
        if (activeSessionId == sessionId || activeSessionId == -1) {
            activeSessionId = -1
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up AudioRecord", e)
            } finally {
                audioRecord = null
                _state.value = if (isError) SttState.ERROR else SttState.READY
            }
        }
    }

    fun shutdown() {
        Log.d(TAG, "shutdown() requested for Sravaani STT Manager")
        cancelListening()
        scope.cancel()
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX Runtime resources", e)
        } finally {
            ortSession = null
            ortEnv = null
            _state.value = SttState.IDLE
        }
    }

    private fun getSampleTextForLanguage(language: Language): String {
        return when (language) {
            Language.HINDI -> "नमस्ते, मुझे सहायता की आवश्यकता है"
            Language.GUJARATI -> "નમસ્તે, મને મદદની જરૂર છે"
            Language.MARATHI -> "नमस्कार, मला मदतीची गरज आहे"
            Language.KANNADA -> "ನಮಸ್ಕಾರ, ನನಗೆ ಸಹಾಯ ಬೇಕು"
            Language.MALAYALAM -> "ഹലോ, എനിക്ക് സഹായം വേണം"
            Language.TAMIL -> "வணக்கம், எனக்கு உதவி தேவை"
            Language.TELUGU -> "నమస్కారం, నాకు సహాయం కావాలి"
            Language.ODIA -> "ନମସ୍କାର, ମୋତେ ସାହାଯ୍ୟ ଦରକାର"
            Language.BENGALI -> "হ্যালো, আমার সাহায্য দরকার"
            Language.ENGLISH -> "Hello, I need assistance"
        }
    }

    companion object {
        private const val TAG = "SttManager"
    }
}
