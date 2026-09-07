package com.itantra.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.itantra.app.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class TtsState {
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR,
    SHUTDOWN
}

class TtsManager(private val context: Context) {

    private var activePiperEngine: PiperEngine? = null
    private var activePiperLanguage: Language? = null
    private val piperMutex = Mutex()

    private val vitsRasaEngine: TtsEngine by lazy { VitsRasaEngine(context) }
    private val hindiMmsEngine: TtsEngine by lazy {
        MmsEngine(
            context, "tts/mms-hin/mms_tts_hin.onnx", "tts/mms-hin/vocab_hin.json",
            "mms_hin.onnx", "vocab_hin.json", maxValidTokenId = 71L
        )
    }
    private val gujaratiEngine: TtsEngine by lazy {
        MmsEngine(
            context, "tts/mms-guj/mms_tts_guj.onnx", "tts/mms-guj/vocab_guj.json",
            "mms_guj.onnx", "vocab_guj.json", maxValidTokenId = 58L
        )
    }
    private val odiaEngine: TtsEngine by lazy {
        MmsEngine(
            context, "tts/mms-ory/mms_tts_ory.onnx", "tts/mms-ory/vocab_ory.json",
            "mms_ory.onnx", "vocab_ory.json", maxValidTokenId = 75L
        )
    }

    private val nonPiperMutexes = ConcurrentHashMap<TtsEngine, Mutex>()
    private val initializedNonPiperEngines = ConcurrentHashMap<TtsEngine, Boolean>()

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        _state.value = TtsState.READY
    }

    private suspend fun getPiperEngine(language: Language): PiperEngine {
        return piperMutex.withLock {
            if (activePiperLanguage != language) {
                Log.d("TtsManager", "Swapping Piper engine to language: $language")
                _state.value = TtsState.INITIALIZING
                activePiperEngine?.release()
                val (assetDir, destDir, speakerId) = when (language) {
                    Language.ENGLISH -> Triple("tts/piper-en", "piper_en", 630)
                    else -> throw IllegalArgumentException("Not a Piper language: $language")
                }
                val engine = PiperEngine(context, assetDir, destDir, speakerId)
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
            }
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
                Language.HINDI -> {
                    ensureNonPiperEngineInitialized(hindiMmsEngine, language)
                    _state.value = TtsState.SPEAKING
                    Pair(hindiMmsEngine.synthesize(text, "hi"), 16000)
                }
                Language.MALAYALAM, Language.BENGALI, Language.KANNADA, Language.MARATHI, Language.TAMIL, Language.TELUGU -> {
                    ensureNonPiperEngineInitialized(vitsRasaEngine, language)
                    _state.value = TtsState.SPEAKING
                    val langCode = when (language) {
                        Language.MALAYALAM -> "ml"
                        Language.BENGALI -> "bn"
                        Language.KANNADA -> "kn"
                        Language.MARATHI -> "mr"
                        Language.TAMIL -> "ta"
                        Language.TELUGU -> "te"
                        else -> "bn"
                    }
                    Pair(vitsRasaEngine.synthesize(text, langCode), 24000)
                }
                Language.GUJARATI -> {
                    ensureNonPiperEngineInitialized(gujaratiEngine, language)
                    _state.value = TtsState.SPEAKING
                    Pair(gujaratiEngine.synthesize(text, "gu"), 16000)
                }
                Language.ODIA -> {
                    ensureNonPiperEngineInitialized(odiaEngine, language)
                    _state.value = TtsState.SPEAKING
                    Pair(odiaEngine.synthesize(text, "or"), 16000)
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
            maxOf(minBufferSize, samples.size * 2),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()

        val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
        Thread {
            Thread.sleep(durationMs + 200)
            audioTrack.stop()
            audioTrack.release()
        }.start()
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
        _state.value = TtsState.SHUTDOWN
    }
}
