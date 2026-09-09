package com.itantra.app.evaluation

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Measures actual on-device model file sizes from assets and copied files.
 */
class ModelSizeCollector(private val context: Context) {

    companion object {
        private const val TAG = "ModelSizeCollector"
    }

    fun collect(): ModelSizes {
        val piperModelSize = measureAssetSize("tts/piper-en/model.onnx")
        val piperTokensSize = measureAssetSize("tts/piper-en/tokens.txt")
        val piperEspeakSize = measureAssetDirSize("tts/piper-en/espeak-ng-data")

        val vitsRasaModelSize = measureAssetSize("tts/vits-rasa/vits_rasa_13.onnx")
        val vitsRasaVocabSize = measureAssetSize("tts/vits-rasa/vocab.json")

        val sttModelSize = measureDirSize(context.filesDir.resolve("indicconformer"))
        val sttTotalSize = sttModelSize

        val ttsModelSizes = mapOf(
            "piper-en" to (piperModelSize + piperTokensSize + piperEspeakSize) / MB,
            "vits-rasa" to (vitsRasaModelSize + vitsRasaVocabSize) / MB,
        )

        val totalMlBytes = sttTotalSize + piperModelSize + piperTokensSize + piperEspeakSize +
                vitsRasaModelSize + vitsRasaVocabSize

        // Count vocab entries for tokenizer size
        val vocabSize = countVocabEntries()

        val result = ModelSizes(
            sttModelSizeMb = sttTotalSize / MB,
            ttsModelSizesMb = ttsModelSizes,
            vadModelSizeMb = 0f,
            tokenizerVocabSize = vocabSize,
            totalMlAssetSizeMb = totalMlBytes / MB
        )

        Log.d(TAG, "Model sizes: TTS models=${ttsModelSizes}, Total=${result.totalMlAssetSizeMb}MB")
        return result
    }

    private fun measureAssetSize(assetPath: String): Long {
        return try {
            context.assets.open(assetPath).use { it.available().toLong() }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot measure asset: $assetPath - ${e.message}")
            0L
        }
    }

    private fun measureAssetDirSize(assetDirPath: String): Long {
        return try {
            var totalSize = 0L
            val files = context.assets.list(assetDirPath) ?: return 0L
            for (fileName in files) {
                val subPath = "$assetDirPath/$fileName"
                val subFiles = context.assets.list(subPath)
                if (subFiles.isNullOrEmpty()) {
                    totalSize += measureAssetSize(subPath)
                } else {
                    totalSize += measureAssetDirSize(subPath)
                }
            }
            totalSize
        } catch (e: Exception) {
            Log.w(TAG, "Cannot measure asset dir: $assetDirPath - ${e.message}")
            0L
        }
    }

    private fun measureDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var totalSize = 0L
        dir.listFiles()?.forEach { file ->
            totalSize += if (file.isDirectory) measureDirSize(file) else file.length()
        }
        return totalSize
    }

    private fun countVocabEntries(): Int {
        var maxVocab = 0
        val vocabFiles = listOf(
            "tts/vits-rasa/vocab.json",
        )
        for (path in vocabFiles) {
            try {
                val json = context.assets.open(path).bufferedReader().readText()
                val keys = org.json.JSONObject(json).length()
                if (keys > maxVocab) maxVocab = keys
            } catch (e: Exception) {
                // skip
            }
        }
        return maxVocab
    }

    /**
     * Measures the APK size on device.
     */
    fun measureApkSize(): Long {
        return try {
            @Suppress("DEPRECATION")
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val apkPath = appInfo.sourceDir ?: return 0L
            File(apkPath).length()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot measure APK size: ${e.message}")
            0L
        }
    }

    /**
     * Measures the installed APK size (total app directory).
     */
    fun measureInstalledSize(): Long {
        return try {
            @Suppress("DEPRECATION")
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val apkPath = appInfo.sourceDir ?: return 0L
            val appDir = File(apkPath).parentFile ?: return 0L
            calculateDirSize(appDir)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot measure installed size: ${e.message}")
            0L
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
        }
        return size
    }

    private val MB = 1024f * 1024f
}
