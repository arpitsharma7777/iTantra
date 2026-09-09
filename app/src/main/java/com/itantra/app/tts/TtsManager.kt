package com.itantra.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.itantra.app.core.model.Language
import com.itantra.app.stt.LanguageVaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class TtsState {
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR,
    SHUTDOWN
}

class TtsManager(
    private val context: Context,
    private val vaultManager: LanguageVaultManager? = null
) {

    private var activePiperEngine: PiperEngine? = null
    private var activePiperLanguage: Language? = null
    private val piperMutex = Mutex()

    private val vitsRasaEngine: TtsEngine by lazy { VitsRasaEngine(context) }

    private val nonPiperMutexes = ConcurrentHashMap<TtsEngine, Mutex>()
    private val initializedNonPiperEngines = ConcurrentHashMap<TtsEngine, Boolean>()
    private var vitsRasaInitialized = false
    private val playbackScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        _state.value = TtsState.READY
        Log.d(TAG, "TTS engine initialized (engines load on-demand)")
    }

    private suspend fun getPiperEngine(language: Language): PiperEngine {
        return piperMutex.withLock {
            if (activePiperLanguage != language) {
                Log.d("TtsManager", "Swapping Piper engine to language: $language")
                _state.value = TtsState.INITIALIZING
                activePiperEngine?.release()
                val modelDir = vaultManager?.getTtsModelDir("piper-en")
                    ?: throw IllegalStateException("Piper English TTS model not downloaded. Please download it in Language Vault.")
                val engine = PiperEngine(context, modelDir, speakerId = 0)
                engine.initialize()
                activePiperEngine = engine
                activePiperLanguage = language
            }
            activePiperEngine!!
        }
    }

    private suspend fun ensureNonPiperEngineInitialized(engine: TtsEngine, language: Language) {
        if (initializedNonPiperEngines[engine] == true) return
        val mutex = nonPiperMutexes.getOrPut(engine) { Mutex() }
        mutex.withLock {
            if (initializedNonPiperEngines[engine] != true) {
                Log.d("TtsManager", "Initializing non-Piper engine for language: $language")
                _state.value = TtsState.INITIALIZING
                engine.initialize()
                initializedNonPiperEngines[engine] = true
                if (engine === vitsRasaEngine) {
                    vitsRasaInitialized = true
                }
            }
        }
    }

    fun isTtsAvailable(language: Language): Boolean {
        return when (language) {
            Language.ENGLISH -> vaultManager?.isTtsModelDownloaded("piper-en") == true
            else -> vaultManager?.isTtsModelDownloaded("vits-rasa") == true
        }
    }

    suspend fun speak(text: String, language: Language) = withContext(Dispatchers.IO) {
        if (_state.value == TtsState.ERROR || _state.value == TtsState.SHUTDOWN) {
            _lastError.value = "Cannot speak: TTS not ready"
            _state.value = TtsState.ERROR
            return@withContext
        }

        try {
            val (samples, sampleRate) = when (language) {
                Language.ENGLISH -> {
                    val engine = getPiperEngine(language)
                    _state.value = TtsState.SPEAKING
                    Pair(engine.synthesize(text, "en"), 22050)
                }
                else -> {
                    try {
                        ensureNonPiperEngineInitialized(vitsRasaEngine, language)
                    } catch (e: Exception) {
                        Log.w(TAG, "VitsRasa TTS unavailable for $language: ${e.message}, TTS skipped")
                        _lastError.value = "TTS not available for ${language.displayName}: ${e.message}"
                        _state.value = TtsState.ERROR
                        return@withContext
                    }
                    _state.value = TtsState.SPEAKING
                    val langCode = when (language) {
                        Language.HINDI -> "hi"
                        Language.BENGALI -> "bn"
                        Language.KANNADA -> "kn"
                        Language.MALAYALAM -> "ml"
                        Language.MARATHI -> "mr"
                        Language.TAMIL -> "ta"
                        Language.TELUGU -> "te"
                        Language.GUJARATI -> "gu"
                        Language.ODIA -> "or"
                        else -> "bn"
                    }
                    Pair(vitsRasaEngine.synthesize(text, langCode), 24000)
                }
            }

            playAudio(samples, sampleRate)

            if (_state.value != TtsState.SHUTDOWN) {
                _state.value = TtsState.READY
            }
        } catch (e: Exception) {
            _lastError.value = "Speech synthesis failed: ${e.message}"
            _state.value = TtsState.ERROR
        }
    }

    private fun playAudio(samples: ShortArray, sampleRate: Int) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            maxOf(minBufferSize, STREAM_BUFFER_SIZE),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack.play()

        val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
        playbackScope.launch {
            try {
                var offset = 0
                while (offset < samples.size) {
                    val writeLen = minOf(STREAM_CHUNK_SIZE, samples.size - offset)
                    val written = audioTrack.write(samples, offset, writeLen)
                    if (written > 0) {
                        offset += written
                    } else {
                        break
                    }
                }
                delay(durationMs + 200)
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        if (_state.value != TtsState.SHUTDOWN && _state.value != TtsState.ERROR) {
            _state.value = TtsState.READY
        }
    }

    fun shutdown() {
        try {
            activePiperEngine?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        activePiperEngine = null
        activePiperLanguage = null

        initializedNonPiperEngines.keys.forEach { engine ->
            try {
                engine.release()
            } catch (e: Exception) {
                // Ignore release errors on shutdown
            }
        }
        initializedNonPiperEngines.clear()

        if (vitsRasaInitialized) {
            try {
                vitsRasaEngine.release()
            } catch (e: Exception) {
                // Ignore release errors on shutdown
            }
            vitsRasaInitialized = false
        }

        _state.value = TtsState.SHUTDOWN
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val STREAM_BUFFER_SIZE = 16384
        private const val STREAM_CHUNK_SIZE = 4096
    }
}
