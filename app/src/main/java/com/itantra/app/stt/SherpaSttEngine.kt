package com.itantra.app.stt

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.Language
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

class SherpaSttEngine(
    private val vaultManager: LanguageVaultManager
) : SttEngine {

    private var currentRecognizer: OfflineRecognizer? = null
    private var currentLangCode: String = ""
    private val recognizerCache = object : LinkedHashMap<String, OfflineRecognizer>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OfflineRecognizer>?): Boolean {
            if (size > MAX_CACHE_SIZE) {
                try {
                    eldest?.value?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing evicted recognizer", e)
                }
                return true
            }
            return false
        }
    }

    private val languageCodeMap = mapOf(
        Language.ENGLISH to "en",
        Language.HINDI to "hi",
        Language.BENGALI to "bn",
        Language.GUJARATI to "gu",
        Language.MARATHI to "mr",
        Language.KANNADA to "kn",
        Language.MALAYALAM to "ml",
        Language.TAMIL to "ta",
        Language.TELUGU to "te",
    )

    override fun initialize(context: Context) {
        Log.d(TAG, "Initializing SherpaSttEngine with IndicConformer for all languages")
        val downloadedLanguages = vaultManager.getDownloadedLanguageCodes()
        Log.d(TAG, "Downloaded languages: $downloadedLanguages")
    }

    override fun prepareLanguage(language: Language): Boolean {
        val langCode = languageCodeMap[language] ?: return false

        if (langCode == currentLangCode && currentRecognizer != null) {
            Log.d(TAG, "prepareLanguage: $langCode already ready")
            return true
        }

        val modelPath = vaultManager.getModelPath(langCode)
        val tokensPath = vaultManager.getTokensPath(langCode)

        if (modelPath == null || tokensPath == null) {
            Log.e(TAG, "Model not downloaded for $langCode")
            return false
        }

        return try {
            val recognizer = createRecognizer(langCode, modelPath, tokensPath)
            recognizerCache[langCode] = recognizer
            currentRecognizer = recognizer
            currentLangCode = langCode
            Log.d(TAG, "prepareLanguage: $langCode ready with IndicConformer")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer for $langCode", e)
            false
        }
    }

    override fun transcribe(audioSamples: ShortArray, sampleRate: Int, language: Language): String {
        if (audioSamples.isEmpty()) return ""

        val langCode = languageCodeMap[language] ?: return ""

        try {
            if (langCode != currentLangCode || currentRecognizer == null) {
                Log.d(TAG, "Language changed: $currentLangCode -> $langCode")

                val cached = recognizerCache[langCode]
                if (cached != null) {
                    currentRecognizer = cached
                    currentLangCode = langCode
                    Log.d(TAG, "Using cached recognizer for $langCode")
                } else {
                    val modelPath = vaultManager.getModelPath(langCode)
                    val tokensPath = vaultManager.getTokensPath(langCode)

                    if (modelPath == null || tokensPath == null) {
                        Log.e(TAG, "Model not available for $langCode")
                        return ""
                    }

                    val recognizer = createRecognizer(langCode, modelPath, tokensPath)
                    recognizerCache[langCode] = recognizer
                    currentRecognizer = recognizer
                    currentLangCode = langCode
                }
            }

            val rec = currentRecognizer ?: return ""

            val floatSamples = FloatArray(audioSamples.size) { audioSamples[it] / 32768.0f }
            val stream = rec.createStream()
            stream.acceptWaveform(floatSamples, sampleRate)
            rec.decode(stream)
            val result = rec.getResult(stream)
            stream.release()

            val text = result.text.trim()
            Log.d(TAG, "Transcribed [$langCode]: \"$text\" (${audioSamples.size} samples)")
            return text
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for language $langCode", e)
            return ""
        }
    }

    override fun release() {
        try {
            currentRecognizer?.release()
            recognizerCache.values.forEach { recognizer ->
                try {
                    recognizer.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing cached recognizer", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recognizers", e)
        }
        currentRecognizer = null
        recognizerCache.clear()
        currentLangCode = ""
        Log.d(TAG, "SherpaSttEngine released")
    }

    fun preloadAllAvailableModels() {
        val downloadedCodes = vaultManager.getDownloadedLanguageCodes()
        Log.d(TAG, "Pre-loading ${downloadedCodes.size} models...")

        for (langCode in downloadedCodes) {
            if (recognizerCache.containsKey(langCode)) continue

            val modelPath = vaultManager.getModelPath(langCode)
            val tokensPath = vaultManager.getTokensPath(langCode)

            if (modelPath != null && tokensPath != null) {
                try {
                    val recognizer = createRecognizer(langCode, modelPath, tokensPath)
                    recognizerCache[langCode] = recognizer
                    Log.d(TAG, "Pre-loaded model for $langCode")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to pre-load model for $langCode", e)
                }
            }
        }
        Log.d(TAG, "Pre-loaded ${recognizerCache.size} models")
    }

    fun clearCache() {
        recognizerCache.values.forEach { recognizer ->
            try {
                recognizer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing recognizer during cache clear", e)
            }
        }
        recognizerCache.clear()
        currentRecognizer = null
        currentLangCode = ""
        Log.d(TAG, "Recognizer cache cleared")
    }

    private fun createRecognizer(langCode: String, modelPath: String, tokensPath: String): OfflineRecognizer {
        val nemoConfig = OfflineNemoEncDecCtcModelConfig(
            model = modelPath,
        )

        val modelConfig = OfflineModelConfig(
            nemo = nemoConfig,
            tokens = tokensPath,
            numThreads = NUM_THREADS,
            debug = false,
            provider = "cpu",
            modelType = "nemo_ctc",
        )

        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )

        return OfflineRecognizer(config = config)
    }

    companion object {
        private const val TAG = "SherpaSttEngine"
        private const val NUM_THREADS = 4
        private const val MAX_CACHE_SIZE = 2
    }
}
