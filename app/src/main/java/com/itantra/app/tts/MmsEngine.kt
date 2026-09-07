package com.itantra.app.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

class MmsEngine(
    private val context: Context,
    private val modelAssetPath: String,
    private val vocabAssetPath: String,
    private val modelFileName: String,
    private val vocabFileName: String,
    private val maxValidTokenId: Long
) : TtsEngine {

    private lateinit var session: OrtSession
    private lateinit var env: OrtEnvironment
    private lateinit var vocab: Map<Char, Long>
    private var unkId: Long = 0L

    override fun initialize() {
        val modelPath = copyAssetFile(modelAssetPath, modelFileName)
        val vocabPath = copyAssetFile(vocabAssetPath, vocabFileName)

        env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelPath, OrtSession.SessionOptions())

        val vocabJson = JSONObject(File(vocabPath).readText())
        val vocabMap = mutableMapOf<Char, Long>()
        vocabJson.keys().forEach { key ->
            if (key.length == 1) {
                vocabMap[key[0]] = vocabJson.getLong(key)
            } else if (key == "<unk>") {
                unkId = vocabJson.getLong(key)
            }
        }
        vocab = vocabMap
    }

    override fun synthesize(text: String, langCode: String): ShortArray {
        val inputIds = tokenize(text)
        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong()))

        val inputs = mapOf("input_ids" to inputTensor)
        val results = session.run(inputs)
        val waveform = results[0].value as Array<FloatArray>
        val floatSamples = waveform[0]

        inputTensor.close()
        results.close()

        return ShortArray(floatSamples.size) { i ->
            (floatSamples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    override fun release() {
        session.close()
        env.close()
    }

    private fun tokenize(text: String): LongArray {
        val ids = mutableListOf<Long>()
        ids.add(0L) // leading blank
        for (char in text.lowercase()) {
            val id = vocab[char] ?: continue
            if (id > maxValidTokenId) continue // skip out-of-bounds tokens
            ids.add(id)
            ids.add(0L) // blank after every token
        }
        return ids.toLongArray()
    }

    private fun copyAssetFile(assetPath: String, destName: String): String {
        val outFile = File(context.filesDir, destName)
        if (!outFile.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }
}
