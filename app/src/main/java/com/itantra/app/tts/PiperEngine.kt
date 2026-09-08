package com.itantra.app.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class PiperEngine(
    private val context: Context,
    private val modelDir: File,
    private val speakerId: Int = 0
) : TtsEngine {

    private lateinit var tts: OfflineTts

    override fun initialize() {
        val modelFile = File(modelDir, "model.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        val dataDir = File(context.filesDir, "espeak-ng-data")

        if (!modelFile.exists()) throw IllegalStateException("Piper model not found: ${modelFile.absolutePath}")
        if (!tokensFile.exists()) throw IllegalStateException("Piper tokens not found: ${tokensFile.absolutePath}")
        if (!dataDir.exists() || !File(dataDir, "intonations").exists()) {
            if (dataDir.exists()) dataDir.deleteRecursively()
            copyEspeakNgData(dataDir)
        }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            tokens = tokensFile.absolutePath,
            dataDir = dataDir.absolutePath
        )
        val modelConfig = OfflineTtsModelConfig(vits = vitsConfig, numThreads = 2, debug = false, provider = "cpu")
        val config = OfflineTtsConfig(model = modelConfig)

        tts = OfflineTts(config = config)
        Log.d(TAG, "PiperEngine initialized from ${modelDir.absolutePath}")
    }

    override fun synthesize(text: String, langCode: String): ShortArray {
        val audio: GeneratedAudio = tts.generate(text = text, sid = speakerId, speed = 1.0f)
        val floatSamples = audio.samples
        return ShortArray(floatSamples.size) { i ->
            (floatSamples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    override fun release() {
        // Calling sherpa-onnx OfflineTts.release() destroys global C++ espeak-ng context,
        // which causes C++ std::terminate crashes when creating subsequent OfflineTts instances.
        // We let JVM GC handle native object lifecycle safely.
    }

    private fun copyEspeakNgData(destDir: File) {
        destDir.mkdirs()
        try {
            val files = context.assets.list("tts/piper-en/espeak-ng-data") ?: emptyArray()
            for (fileName in files) {
                val subAssetPath = "tts/piper-en/espeak-ng-data/$fileName"
                val subFiles = context.assets.list(subAssetPath)
                if (subFiles.isNullOrEmpty()) {
                    context.assets.open(subAssetPath).use { input ->
                        java.io.FileOutputStream(File(destDir, fileName)).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            Log.d(TAG, "Copied espeak-ng-data")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy espeak-ng-data", e)
        }
    }

    companion object {
        private const val TAG = "PiperEngine"
    }
}
