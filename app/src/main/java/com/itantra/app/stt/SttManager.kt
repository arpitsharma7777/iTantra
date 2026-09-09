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
import kotlinx.coroutines.channels.Channel

enum class SttState {
    IDLE,
    LOADING,
    PREPARING,
    READY,
    LISTENING,
    ERROR
}

data class SttResult(
    val sessionId: Int,
    val language: Language,
    val partialText: String? = null,
    val finalText: String? = null,
)

class SttManager(
    private val context: Context,
    private val vadManager: VadEngine,
    private val vaultManager: LanguageVaultManager,
    private val sttEngine: SttEngine = SherpaSttEngine(vaultManager),
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    fun initialize(language: Language) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Initializing STT for $language")
        _state.value = SttState.LOADING
        _lastError.value = null
        currentLanguage = language

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    vadManager.initialize()
                    Log.d(TAG, "VAD initialized successfully")
                    sttEngine.initialize(context)
                }

                val langCode = language.indicConformerCode
                if (langCode != null && vaultManager.isModelDownloaded(langCode)) {
                    val prepared = withContext(Dispatchers.IO) {
                        sttEngine.prepareLanguage(language)
                    }
                    if (prepared) {
                        val loadDuration = System.currentTimeMillis() - startTime
                        MetricsManager.recordSttModelLoadTime(loadDuration)
                        Log.d(TAG, "STT initialized in ${loadDuration}ms for $language")
                        _state.value = SttState.READY
                    } else {
                        _lastError.value = "Failed to prepare STT model for ${language.displayName}"
                        _state.value = SttState.ERROR
                    }
                } else {
                    _lastError.value = "Model not downloaded for ${language.displayName}. Please download it in Language Vault."
                    _state.value = SttState.ERROR
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize STT for $language", e)
                _lastError.value = "STT init failed: ${e.message}"
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

        val langCode = language.indicConformerCode
        if (langCode != null && vaultManager.isModelDownloaded(langCode)) {
            _state.value = SttState.PREPARING

            scope.launch {
                Log.d(TAG, "Preparing recognizer for $language in background...")
                val prepared = withContext(Dispatchers.IO) {
                    sttEngine.prepareLanguage(language)
                }
                if (prepared) {
                    Log.d(TAG, "Recognizer ready for $language")
                    if (_state.value == SttState.PREPARING) {
                        _state.value = SttState.READY
                    }
                } else {
                    Log.e(TAG, "Failed to prepare recognizer for $language")
                    _lastError.value = "Failed to prepare STT for ${language.displayName}"
                }
            }
        } else {
            _lastError.value = "Model not downloaded for ${language.displayName}. Please download it in Language Vault."
            Log.e(TAG, "Cannot switch to $language: model not downloaded")
        }
    }

    fun prePrepareLanguage(language: Language) {
        val langCode = language.indicConformerCode ?: return
        if (!vaultManager.isModelDownloaded(langCode)) return

        scope.launch {
            Log.d(TAG, "Pre-preparing recognizer for $language...")
            withContext(Dispatchers.IO) {
                sttEngine.prepareLanguage(language)
            }
            Log.d(TAG, "Pre-prepare complete for $language")
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(): Int {
        if (_state.value == SttState.LOADING || _state.value == SttState.PREPARING) {
            Log.e(TAG, "startListening() failed: model still loading/preparing")
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

        val langCode = language.indicConformerCode
        if (langCode == null || !vaultManager.isModelDownloaded(langCode)) {
            Log.e(TAG, "startListening() failed: model not downloaded for $language")
            _lastError.value = "Model not downloaded for ${language.displayName}"
            return -1
        }

        val sessionId = ++sessionCounter
        activeSessionId = sessionId
        Log.d(TAG, "Starting listening session $sessionId for $language (${language.code})")

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, sampleRate * 2)
        Log.d(TAG, "AudioRecord: sampleRate=$sampleRate, minBuffer=$minBufferSize, bufferSize=$bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed, state=${audioRecord?.state}")
                _lastError.value = "Failed to initialize microphone"
                cleanupSession(sessionId, isError = true)
                return -1
            }

            audioRecord?.startRecording()
            Log.d(TAG, "AudioRecord started, recordingState=${audioRecord?.recordingState}")
            _state.value = SttState.LISTENING

            val sessionStartTime = System.currentTimeMillis()
            val audioChannel = Channel<ShortArray>(capacity = 100)

            recordingJob = scope.launch(Dispatchers.Default) {
                val maxSamples = MAX_RECORDING_SECONDS * sampleRate
                val speechAudioBuffer = ShortArray(maxSamples)
                var writeIndex = 0
                var endSilenceTriggered = false
                var vadStateMachine: VadStateMachine? = null
                var speechDetected = false
                var chunksReceived = 0
                var totalSamples = 0

                vadManager.resetState()
                vadStateMachine = VadStateMachine()
                Log.d(TAG, "Consumer $sessionId: VAD mode, waiting for audio chunks...")

                try {
                    for (chunk in audioChannel) {
                        if (_state.value != SttState.LISTENING || activeSessionId != sessionId) {
                            Log.d(TAG, "Consumer $sessionId: exiting loop, state=${_state.value}, activeSession=$activeSessionId")
                            break
                        }

                        chunksReceived++
                        totalSamples += chunk.size

                        val copyLen = minOf(chunk.size, maxSamples - writeIndex)
                        if (copyLen > 0) {
                            System.arraycopy(chunk, 0, speechAudioBuffer, writeIndex, copyLen)
                            writeIndex += copyLen
                        }

                        if (vadStateMachine != null && chunk.size >= 1024) {
                            val frame1 = chunk.copyOfRange(0, 512)
                            val frame2 = chunk.copyOfRange(512, 1024)

                            for (frame in arrayOf(frame1, frame2)) {
                                val prob = vadManager.processFrame(frame)
                                vadStateMachine.processFrame(frame, prob)

                                val currentState = vadStateMachine.state.value
                                if (currentState == VadState.SPEECH || currentState == VadState.POSSIBLE_SILENCE) {
                                    if (!speechDetected) {
                                        Log.d(TAG, "Consumer $sessionId: Speech detected! VAD state=$currentState, prob=$prob")
                                    }
                                    speechDetected = true
                                }

                                if (speechDetected && currentState == VadState.IDLE) {
                                    Log.d(TAG, "Consumer $sessionId: End silence detected, VAD state=$currentState")
                                    endSilenceTriggered = true
                                    break
                                }
                            }
                        }

                        if (endSilenceTriggered) break
                    }

                    Log.d(TAG, "Consumer $sessionId: loop ended, chunks=$chunksReceived, totalSamples=$totalSamples, buffered=$writeIndex, endSilence=$endSilenceTriggered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in consumer loop for session $sessionId", e)
                } finally {
                    if (writeIndex > 0) {
                        val finalBuffer = speechAudioBuffer.copyOf(writeIndex)
                        Log.d(TAG, "Consumer $sessionId: transcribing $writeIndex samples")
                        val recognizedText = withContext(Dispatchers.Default) {
                            sttEngine.transcribe(finalBuffer, sampleRate, language)
                        }

                        val finalText = recognizedText.ifBlank { "" }

                        val recognitionDuration = System.currentTimeMillis() - sessionStartTime
                        MetricsManager.recordSttRecognitionLatency(recognitionDuration)

                        Log.d(TAG, "Consumer $sessionId: FINAL result: \"$finalText\" (${finalText.length} chars, ${recognitionDuration}ms)")
                        _result.value = SttResult(
                            sessionId = sessionId,
                            language = language,
                            finalText = finalText,
                        )
                        MetricsManager.markSttComplete(sessionId.toString(), System.currentTimeMillis())
                    } else {
                        Log.w(TAG, "Consumer $sessionId: no audio samples to transcribe")
                    }

                    Log.d(TAG, "Consumer $sessionId: manual stop, result emitted")
                    _state.value = SttState.READY
                }
            }

            scope.launch(Dispatchers.IO) {
                val readBuffer = ShortArray(1024)
                var totalReads = 0
                var totalSamplesRead = 0
                try {
                    while ((_state.value == SttState.LISTENING) && (activeSessionId == sessionId)) {
                        val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                        if (read > 0) {
                            totalReads++
                            totalSamplesRead += read
                            audioChannel.send(readBuffer.copyOf(read))
                        } else if (read < 0) {
                            Log.e(TAG, "Producer $sessionId: audioRecord.read error: $read")
                            break
                        }
                    }
                    Log.d(TAG, "Producer $sessionId: exited, totalReads=$totalReads, totalSamples=$totalSamplesRead")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in AudioRecord producer loop for session $sessionId", e)
                } finally {
                    Log.d(TAG, "Producer $sessionId: closing audioChannel")
                    audioChannel.close()
                }
            }

            return sessionId
        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord permission denied for session $sessionId", e)
            _lastError.value = "Microphone permission denied"
            cleanupSession(sessionId, isError = true)
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord for session $sessionId", e)
            _lastError.value = "Failed to start listening: ${e.message}"
            cleanupSession(sessionId, isError = true)
            return -1
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening() requested for session $activeSessionId")
        val sessionId = activeSessionId
        if (sessionId == -1) {
            Log.w(TAG, "stopListening() called but no active session")
            _state.value = SttState.READY
            return
        }
        _state.value = SttState.READY

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
                Log.d(TAG, "AudioRecord stopped for session $sessionId")
            }
            audioRecord?.release()
            Log.d(TAG, "AudioRecord released for session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord on stopListening", e)
        } finally {
            audioRecord = null
        }
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
        if ((activeSessionId == sessionId) || (activeSessionId == -1)) {
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
        Log.d(TAG, "shutdown() requested for STT Manager")
        cancelListening()
        scope.cancel()
        try {
            sttEngine.release()
            vadManager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing STT resources", e)
        } finally {
            _state.value = SttState.IDLE
        }
    }

    companion object {
        private const val TAG = "SttManager"
        private const val MAX_RECORDING_SECONDS = 60
    }
}
