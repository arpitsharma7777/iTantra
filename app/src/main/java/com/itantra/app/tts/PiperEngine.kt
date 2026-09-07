package com.itantra.app.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream

class PiperEngine(
    private val context: Context,
    private val modelAssetDir: String,
    private val destDirName: String,
    private val speakerId: Int = 0
) : TtsEngine {

    private lateinit var tts: OfflineTts

    override fun initialize() {
        val modelPath = copyAssetFile("$modelAssetDir/model.onnx", "${destDirName}_model.onnx")
        val tokensPath = copyAssetFile("$modelAssetDir/tokens.txt", "${destDirName}_tokens.txt")
        val dataDirPath = copyAssetDir("$modelAssetDir/espeak-ng-data", "espeak-ng-data")

        val vitsConfig = OfflineTtsVitsModelConfig(model = modelPath, tokens = tokensPath, dataDir = dataDirPath)
        val modelConfig = OfflineTtsModelConfig(vits = vitsConfig, numThreads = 2, debug = false, provider = "cpu")
        val config = OfflineTtsConfig(model = modelConfig)

        tts = OfflineTts(config = config)
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

    private fun copyAssetFile(assetPath: String, destName: String): String {
        val outFile = File(context.filesDir, destName)
        if (!outFile.exists()) {
            context.assets.open(assetPath).use { input -> FileOutputStream(outFile).use { output -> input.copyTo(output) } }
        }
        return outFile.absolutePath
    }

    private fun copyAssetDir(assetDirPath: String, destDirName: String): String {
        val outDir = File(context.filesDir, destDirName)
        if (!outDir.exists()) {
            outDir.mkdirs()
            val files = context.assets.list(assetDirPath) ?: emptyArray()
            for (fileName in files) {
                val subAssetPath = "$assetDirPath/$fileName"
                val subFiles = context.assets.list(subAssetPath)
                if (subFiles.isNullOrEmpty()) {
                    context.assets.open(subAssetPath).use { input -> FileOutputStream(File(outDir, fileName)).use { output -> input.copyTo(output) } }
                } else {
                    copyAssetDir(subAssetPath, "$destDirName/$fileName")
                }
            }
        }
        return outDir.absolutePath
    }
}
