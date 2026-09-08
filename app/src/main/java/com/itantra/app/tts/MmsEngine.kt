package com.itantra.app.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

class MmsEngine(
    private val context: Context,
    private val ttsId: String,
    private val maxValidTokenId: Long
) : TtsEngine {

    private lateinit var session: OrtSession
    private lateinit var env: OrtEnvironment
    private lateinit var vocab: Map<Char, Long>
    private var unkId: Long = 0L

    override fun initialize() {
        if (!OnnxRuntimeCompat.isAvailable) {
            throw IllegalStateException("ONNX Runtime native library is unavailable on this device (OrtGetApiBase symbol missing). MMS TTS is not supported.")
        }

        val ttsDir = File(context.filesDir, "tts")
        val modelDir = File(ttsDir, ttsId)
        val modelFile = File(modelDir, "model.onnx")

        if (!modelFile.exists()) throw IllegalStateException("MMS model not found: ${modelFile.absolutePath}")

        env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

        val vocabFile = File(modelDir, "vocab.json")
        val tokensFile = File(modelDir, "tokens.txt")
        val vocabMap = mutableMapOf<Char, Long>()

        if (vocabFile.exists()) {
            val vocabJson = JSONObject(vocabFile.readText())
            vocabJson.keys().forEach { key ->
                if (key.length == 1) {
                    vocabMap[key[0]] = vocabJson.getLong(key)
                } else if (key == "<unk>") {
                    unkId = vocabJson.getLong(key)
                }
            }
        } else if (tokensFile.exists()) {
            tokensFile.readLines().forEach { line ->
                val parts = line.trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    val token = parts[0]
                    val id = parts[1].toLongOrNull() ?: return@forEach
                    if (token.length == 1) {
                        vocabMap[token[0]] = id
                    } else if (token == "<unk>" || token == "<blank>") {
                        unkId = id
                    }
                }
            }
        } else {
            throw IllegalStateException("MMS vocab not found in ${modelDir.absolutePath}")
        }

        vocab = vocabMap
        Log.d(TAG, "MmsEngine($ttsId) initialized from ${modelDir.absolutePath}")
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
        ids.add(0L)
        for (char in text.lowercase()) {
            val id = vocab[char] ?: continue
            if (id > maxValidTokenId) continue
            ids.add(id)
            ids.add(0L)
        }
        return ids.toLongArray()
    }

    companion object {
        private const val TAG = "MmsEngine"
    }
}
