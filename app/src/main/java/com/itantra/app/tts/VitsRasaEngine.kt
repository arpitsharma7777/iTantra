package com.itantra.app.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

class VitsRasaEngine(private val context: Context) : TtsEngine {

    private lateinit var session: OrtSession
    private lateinit var env: OrtEnvironment
    private lateinit var vocab: Map<Char, Long>

    private val speakerIds = mapOf(
        "bn" to 2L,
        "kn" to 8L,
        "ml" to 11L,
        "mr" to 12L,
        "ta" to 18L,
        "te" to 19L
    )

    override fun initialize() {
        if (!OnnxRuntimeCompat.isAvailable) {
            throw IllegalStateException("ONNX Runtime native library is unavailable on this device (OrtGetApiBase symbol missing). Hindi TTS is not supported.")
        }

        val ttsDir = File(context.filesDir, "tts")
        val modelDir = File(ttsDir, "vits-rasa")
        val modelFile = File(modelDir, "model.onnx")
        val vocabFile = File(modelDir, "vocab.json")

        if (!modelFile.exists()) throw IllegalStateException("VITS Rasa model not found: ${modelFile.absolutePath}")
        if (!vocabFile.exists()) throw IllegalStateException("VITS Rasa vocab not found: ${vocabFile.absolutePath}")

        env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

        val vocabJson = JSONObject(vocabFile.readText())
        val vocabMap = mutableMapOf<Char, Long>()
        vocabJson.keys().forEach { key ->
            if (key.length == 1) {
                vocabMap[key[0]] = vocabJson.getLong(key)
            }
        }
        vocab = vocabMap
        Log.d(TAG, "VitsRasaEngine initialized from ${modelDir.absolutePath}")
    }

    override fun synthesize(text: String, langCode: String): ShortArray {
        val speakerId = speakerIds[langCode] ?: 2L
        val inputIds = tokenize(text)

        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, inputIds.size.toLong()))
        val speakerTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(speakerId)), longArrayOf(1))
        val emotionTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0L)), longArrayOf(1))

        val inputs = mapOf(
            "input_ids" to inputTensor,
            "speaker_id" to speakerTensor,
            "emotion_id" to emotionTensor
        )

        val results = session.run(inputs)
        val waveform = results[0].value as Array<FloatArray>
        val floatSamples = waveform[0]

        inputTensor.close()
        speakerTensor.close()
        emotionTensor.close()
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
            ids.add(id)
            ids.add(0L)
        }
        return ids.toLongArray()
    }

    companion object {
        private const val TAG = "VitsRasaEngine"
    }
}
