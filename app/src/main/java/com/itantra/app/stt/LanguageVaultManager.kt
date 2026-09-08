package com.itantra.app.stt

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ModelType { STT, TTS }

data class LanguageModelInfo(
    val language: Language,
    val langCode: String,
    val isDownloaded: Boolean,
    val isPreloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val modelSizeBytes: Long = 0L,
    val modelType: ModelType = ModelType.STT,
)

data class TtsModelInfo(
    val id: String,
    val displayName: String,
    val isDownloaded: Boolean,
    val isPreloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val modelSizeBytes: Long = 0L,
)

class LanguageVaultManager(private val context: Context) {

    private val _languageStates = MutableStateFlow<Map<String, LanguageModelInfo>>(emptyMap())
    val languageStates: StateFlow<Map<String, LanguageModelInfo>> = _languageStates.asStateFlow()

    private val _ttsModelStates = MutableStateFlow<Map<String, TtsModelInfo>>(emptyMap())
    val ttsModelStates: StateFlow<Map<String, TtsModelInfo>> = _ttsModelStates.asStateFlow()

    private val filesDir: File = context.filesDir
    private val indicConformerDir: File = File(filesDir, "indicconformer")
    private val ttsDir: File = File(filesDir, "tts")

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

    private val sttModelSizes = mapOf(
        "en" to 175_000_000L,
        "hi" to 198_000_000L,
        "bn" to 112_000_000L,
        "gu" to 171_000_000L,
        "mr" to 135_000_000L,
        "kn" to 119_000_000L,
        "ml" to 111_000_000L,
        "ta" to 127_000_000L,
        "te" to 158_000_000L,
    )

    data class TtsModelDef(
        val id: String,
        val displayName: String,
        val sizeBytes: Long,
        val downloadUrl: String,
        val extraFiles: List<Pair<String, String>> = emptyList(),
        val archiveUrl: String? = null,
        val archiveFiles: List<Pair<String, String>> = emptyList(),
    )

    private val ttsModelDefs = listOf(
        TtsModelDef(
            id = "piper-en",
            displayName = "English (Piper)",
            sizeBytes = 78_000_000L,
            downloadUrl = "$HUGGINGFACE_PIPER_EN_URL/en_US-ryan-medium.onnx",
            extraFiles = listOf(
                "$HUGGINGFACE_PIPER_EN_URL/tokens.txt" to "tokens.txt",
            )
        ),
        TtsModelDef(
            id = "vits-rasa",
            displayName = "Indic Multi (VITS Rasa)",
            sizeBytes = 121_000_000L,
            downloadUrl = "https://huggingface.co/Srinath-Pulaverthi/indic-tts/resolve/main/vits_rasa_13.onnx",
            extraFiles = listOf(
                "https://huggingface.co/Srinath-Pulaverthi/indic-tts/resolve/main/vocab.json" to "vocab.json",
            )
        ),
    )

    private val tokensUrl = "$STT_BASE_URL/tokens.txt"

    fun initialize() {
        indicConformerDir.mkdirs()
        ttsDir.mkdirs()
        refreshStates()
        refreshTtsStates()
    }

    fun initializeFromAssets() {
        indicConformerDir.mkdirs()
        ttsDir.mkdirs()
        val codes = languageCodeMap.values.distinct()
        for (langCode in codes) {
            if (isModelDownloaded(langCode)) continue
            copyFromAssets(langCode)
        }
        copyTtsFromAssets()
        refreshStates()
        refreshTtsStates()
    }

    private fun copyFromAssets(langCode: String) {
        try {
            val modelDir = File(indicConformerDir, langCode)
            modelDir.mkdirs()

            val modelFile = File(modelDir, "model.int8.onnx")
            if (!modelFile.exists()) {
                val assetModelPath = "indicconformer/$langCode/model.int8.onnx"
                try {
                    context.assets.open(assetModelPath).use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied model from assets for $langCode (${modelFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "No bundled model for $langCode in assets")
                }
            }

            val tokensFile = File(modelDir, "tokens.txt")
            if (!tokensFile.exists()) {
                val assetTokensPath = "indicconformer/$langCode/tokens.txt"
                try {
                    context.assets.open(assetTokensPath).use { input ->
                        FileOutputStream(tokensFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied tokens from assets for $langCode")
                } catch (e: Exception) {
                    Log.w(TAG, "No bundled tokens for $langCode, trying shared tokens")
                    val sharedTokensPath = "indicconformer/tokens.txt"
                    try {
                        context.assets.open(sharedTokensPath).use { input ->
                            FileOutputStream(tokensFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Copied shared tokens for $langCode")
                    } catch (e2: Exception) {
                        Log.w(TAG, "No shared tokens in assets")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying from assets for $langCode", e)
        }
    }

    private fun copyTtsFromAssets() {
        val piperDir = File(ttsDir, "piper-en")
        piperDir.mkdirs()
        val piperModelFile = File(piperDir, "model.onnx")
        if (!piperModelFile.exists()) {
            try {
                context.assets.open("tts/piper-en/model.onnx").use { input ->
                    FileOutputStream(piperModelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied Piper EN TTS model from assets (${piperModelFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "No bundled Piper EN TTS model in assets")
            }
        }

        val vitsDir = File(ttsDir, "vits-rasa")
        vitsDir.mkdirs()
        val vitsModelFile = File(vitsDir, "model.onnx")
        if (!vitsModelFile.exists()) {
            try {
                context.assets.open("tts/vits-rasa/model.onnx").use { input ->
                    FileOutputStream(vitsModelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied VITS Rasa TTS model from assets (${vitsModelFile.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "No bundled VITS Rasa TTS model in assets")
            }
        }
    }

    private val preloadedSTTLangCodes = setOf("en", "hi")

    private fun refreshStates() {
        val states = mutableMapOf<String, LanguageModelInfo>()
        for ((lang, langCode) in languageCodeMap) {
            val modelFile = getModelFile(langCode)
            val tokensFile = getTokensFile(langCode)
            val downloaded = modelFile.exists() && tokensFile.exists()
            val preloaded = downloaded && langCode in preloadedSTTLangCodes
            val size = if (modelFile.exists()) modelFile.length() else sttModelSizes[langCode] ?: 0L
            states[langCode] = LanguageModelInfo(
                language = lang,
                langCode = langCode,
                isDownloaded = downloaded,
                isPreloaded = preloaded,
                modelSizeBytes = size,
            )
        }
        _languageStates.value = states
    }

    private val preloadedTtsIds = setOf("piper-en", "vits-rasa")

    private fun refreshTtsStates() {
        val states = mutableMapOf<String, TtsModelInfo>()
        for (def in ttsModelDefs) {
            val modelDir = File(ttsDir, def.id)
            val modelFile = File(modelDir, "model.onnx")
            val downloaded = modelFile.exists()
            val preloaded = downloaded && def.id in preloadedTtsIds
            val size = if (modelFile.exists()) modelFile.length() else def.sizeBytes
            states[def.id] = TtsModelInfo(
                id = def.id,
                displayName = def.displayName,
                isDownloaded = downloaded,
                isPreloaded = preloaded,
                modelSizeBytes = size,
            )
        }
        _ttsModelStates.value = states
    }

    fun isModelDownloaded(langCode: String): Boolean {
        return getModelFile(langCode).exists() && getTokensFile(langCode).exists()
    }

    fun isModelPreloaded(langCode: String): Boolean {
        return langCode in preloadedSTTLangCodes
    }

    fun isTtsModelDownloaded(ttsId: String): Boolean {
        val def = ttsModelDefs.find { it.id == ttsId } ?: return false
        return File(ttsDir, "${def.id}/model.onnx").exists()
    }

    fun getTtsModelDir(ttsId: String): File? {
        val dir = File(ttsDir, ttsId)
        return if (File(dir, "model.onnx").exists()) dir else null
    }

    fun getModelPath(langCode: String): String? {
        val modelFile = getModelFile(langCode)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }

    fun getTokensPath(langCode: String): String? {
        val tokensFile = getTokensFile(langCode)
        return if (tokensFile.exists()) tokensFile.absolutePath else null
    }

    fun getDownloadedLanguageCodes(): Set<String> {
        return languageCodeMap.values.filter { isModelDownloaded(it) }.toSet()
    }

    fun getDownloadedLanguages(): Set<Language> {
        return languageCodeMap.filter { isModelDownloaded(it.value) }.keys
    }

    fun getTotalDownloadedSize(): Long {
        val sttSize = indicConformerDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        val ttsSize = ttsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        return sttSize + ttsSize
    }

    suspend fun downloadModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        val modelDir = File(indicConformerDir, langCode)
        modelDir.mkdirs()

        updateState(langCode, isDownloading = true, progress = 0f, error = null)

        try {
            val modelFile = File(modelDir, "model.int8.onnx")
            if (!modelFile.exists()) {
                val assetModelPath = "indicconformer/$langCode/model.int8.onnx"
                var copiedFromAssets = false
                try {
                    context.assets.open(assetModelPath).use { input ->
                        FileOutputStream(modelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copiedFromAssets = true
                    Log.d(TAG, "Copied model from assets for $langCode (${modelFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.d(TAG, "No bundled model for $langCode, downloading from HuggingFace")
                }

                if (!copiedFromAssets) {
                    val modelUrl = "$STT_BASE_URL/$langCode/model.int8.onnx"
                    Log.d(TAG, "Downloading model from: $modelUrl")
                    if (!downloadFile(modelUrl, modelFile) { progress ->
                        updateState(langCode, progress = progress)
                    }) {
                        updateState(langCode, isDownloading = false, error = "Failed to download model")
                        return@withContext false
                    }
                } else {
                    updateState(langCode, progress = 0.9f)
                }
            }

            val tokensFile = File(modelDir, "tokens.txt")
            if (!tokensFile.exists()) {
                val assetTokensPath = "indicconformer/$langCode/tokens.txt"
                var copiedFromAssets = false
                try {
                    context.assets.open(assetTokensPath).use { input ->
                        FileOutputStream(tokensFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    copiedFromAssets = true
                    Log.d(TAG, "Copied tokens from assets for $langCode")
                } catch (e: Exception) {
                    Log.d(TAG, "No bundled tokens for $langCode")
                }

                if (!copiedFromAssets) {
                    val sharedTokensPath = "indicconformer/tokens.txt"
                    try {
                        context.assets.open(sharedTokensPath).use { input ->
                            FileOutputStream(tokensFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copiedFromAssets = true
                        Log.d(TAG, "Copied shared tokens for $langCode")
                    } catch (e: Exception) {
                        Log.d(TAG, "No shared tokens in assets")
                    }
                }

                if (!copiedFromAssets) {
                    val url = if (langCode == "en") "$STT_BASE_URL/en/tokens.txt" else tokensUrl
                    Log.d(TAG, "Downloading tokens from: $url")
                    if (!downloadFile(url, tokensFile) { progress ->
                        updateState(langCode, progress = 0.9f + progress * 0.1f)
                    }) {
                        updateState(langCode, isDownloading = false, error = "Failed to download tokens")
                        return@withContext false
                    }
                }
            }

            updateState(langCode, isDownloaded = true, isDownloading = false, progress = 1f)
            Log.d(TAG, "Model for $langCode ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model for $langCode", e)
            updateState(langCode, isDownloading = false, error = e.message)
            false
        }
    }

    suspend fun downloadTtsModel(ttsId: String): Boolean = withContext(Dispatchers.IO) {
        val def = ttsModelDefs.find { it.id == ttsId }
            ?: run {
                Log.e(TAG, "Unknown TTS model: $ttsId")
                return@withContext false
            }

        val modelDir = File(ttsDir, def.id)
        modelDir.mkdirs()

        updateTtsState(ttsId, isDownloading = true, progress = 0f, error = null)

        try {
            if (def.archiveUrl != null) {
                val modelFile = File(modelDir, "model.onnx")
                if (!modelFile.exists()) {
                    Log.d(TAG, "Downloading TTS archive ${def.id} from: ${def.archiveUrl}")
                    val archiveFile = File(modelDir, "${def.id}.tar.bz2")
                    if (!downloadFile(def.archiveUrl, archiveFile) { progress ->
                        updateTtsState(ttsId, progress = progress)
                    }) {
                        updateTtsState(ttsId, isDownloading = false, error = "Failed to download archive")
                        return@withContext false
                    }
                    updateTtsState(ttsId, progress = 0.9f)
                    Log.d(TAG, "Extracting archive for ${def.id}")
                    extractTarBz2(archiveFile, modelDir, def.archiveFiles)
                    archiveFile.delete()
                }
            } else {
                val modelFile = File(modelDir, "model.onnx")
                if (!modelFile.exists()) {
                    Log.d(TAG, "Downloading TTS model ${def.id} from: ${def.downloadUrl}")
                    if (!downloadFile(def.downloadUrl, modelFile) { progress ->
                        updateTtsState(ttsId, progress = progress)
                    }) {
                        updateTtsState(ttsId, isDownloading = false, error = "Failed to download model")
                        return@withContext false
                    }
                }

                for ((url, fileName) in def.extraFiles) {
                    val destFile = File(modelDir, fileName)
                    if (!destFile.exists()) {
                        Log.d(TAG, "Downloading $fileName for ${def.id}")
                        if (!downloadFile(url, destFile)) {
                            Log.e(TAG, "Failed to download $fileName for ${def.id}")
                            updateTtsState(ttsId, isDownloading = false, error = "Failed to download $fileName")
                            return@withContext false
                        }
                    }
                }
            }

            updateTtsState(ttsId, isDownloaded = true, isDownloading = false, progress = 1f)
            Log.d(TAG, "TTS model $ttsId ready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading TTS model $ttsId", e)
            updateTtsState(ttsId, isDownloading = false, error = e.message)
            false
        }
    }

    suspend fun deleteModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        if (langCode in preloadedSTTLangCodes) {
            Log.d(TAG, "Cannot delete preloaded model: $langCode")
            return@withContext false
        }
        val modelDir = File(indicConformerDir, langCode)
        if (modelDir.exists()) {
            val deleted = modelDir.deleteRecursively()
            refreshStates()
            Log.d(TAG, "Deleted model for $langCode: $deleted")
            deleted
        } else {
            true
        }
    }

    suspend fun deleteTtsModel(ttsId: String): Boolean = withContext(Dispatchers.IO) {
        if (ttsId in preloadedTtsIds) {
            Log.d(TAG, "Cannot delete preloaded TTS model: $ttsId")
            return@withContext false
        }
        val modelDir = File(ttsDir, ttsId)
        if (modelDir.exists()) {
            val deleted = modelDir.deleteRecursively()
            refreshTtsStates()
            Log.d(TAG, "Deleted TTS model $ttsId: $deleted")
            deleted
        } else {
            true
        }
    }

    private fun updateState(
        langCode: String,
        isDownloaded: Boolean? = null,
        isDownloading: Boolean? = null,
        progress: Float? = null,
        error: String? = null,
    ) {
        val current = _languageStates.value.toMutableMap()
        val existing = current[langCode] ?: return
        current[langCode] = existing.copy(
            isDownloaded = isDownloaded ?: existing.isDownloaded,
            isDownloading = isDownloading ?: existing.isDownloading,
            downloadProgress = progress ?: existing.downloadProgress,
            error = error,
        )
        _languageStates.value = current
    }

    private fun updateTtsState(
        ttsId: String,
        isDownloaded: Boolean? = null,
        isDownloading: Boolean? = null,
        progress: Float? = null,
        error: String? = null,
    ) {
        val current = _ttsModelStates.value.toMutableMap()
        val existing = current[ttsId] ?: return
        current[ttsId] = existing.copy(
            isDownloaded = isDownloaded ?: existing.isDownloaded,
            isDownloading = isDownloading ?: existing.isDownloading,
            downloadProgress = progress ?: existing.downloadProgress,
            error = error,
        )
        _ttsModelStates.value = current
    }

    private fun getModelFile(langCode: String): File {
        return File(indicConformerDir, "$langCode/model.int8.onnx")
    }

    private fun getTokensFile(langCode: String): File {
        return File(indicConformerDir, "$langCode/tokens.txt")
    }

    private fun extractTarBz2(archiveFile: File, destDir: File, expectedFiles: List<Pair<String, String>>) {
        BufferedInputStream(archiveFile.inputStream()).use { bis ->
            BZip2CompressorInputStream(bis).use { bzis ->
                TarArchiveInputStream(bzis).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        val name = entry.name
                        val mapping = expectedFiles.find { it.first == name }
                        if (mapping != null) {
                            val outFile = File(destDir, mapping.second)
                            FileOutputStream(outFile).use { fos ->
                                tar.copyTo(fos)
                            }
                            Log.d(TAG, "Extracted $name -> ${mapping.second} (${outFile.length()} bytes)")
                        }
                        entry = tar.nextTarEntry
                    }
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, destFile: File, onProgress: (Float) -> Unit = {}): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.connect()

            if (connection.responseCode != 200) {
                Log.e(TAG, "HTTP ${connection.responseCode} for $urlStr")
                return false
            }

            val totalBytes = connection.contentLength.toLong()
            val inputStream = connection.inputStream
            val tempFile = File(destFile.parent, destFile.name + ".tmp")

            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes)
                    }
                }
            }

            tempFile.renameTo(destFile)
            Log.d(TAG, "Downloaded ${destFile.name} (${destFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $urlStr", e)
            return false
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "LanguageVaultManager"
        private const val STT_BASE_URL = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
        private const val HUGGINGFACE_PIPER_EN_URL = "https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-medium/resolve/main"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
    }
}
