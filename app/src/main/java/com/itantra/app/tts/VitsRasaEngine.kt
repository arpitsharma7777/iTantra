package com.itantra.app.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

class VitsRasaEngine(private val context: Context) : TtsEngine {

    private lateinit var session: OrtSession
    private lateinit var env: OrtEnvironment
    private lateinit var vocab: Map<Char, Long>

    // Language -> speaker_id mapping (female defaults; male where available)
    private val speakerIds = mapOf(
        "bn" to 2L,  // Bengali (female)
        "kn" to 8L,  // Kannada (female)
        "ml" to 11L, // Malayalam (female, no male available)
        "mr" to 12L, // Marathi (female)
        "ta" to 18L, // Tamil (female, no male available)
        "te" to 19L  // Telugu (female, no male available)
    )

    override fun initialize() {
        val modelPath = copyAssetFile("tts/vits-rasa/vits_rasa_13.onnx", "vits_rasa_13.onnx")
        val vocabPath = copyAssetFile("tts/vits-rasa/vocab.json", "vits_rasa_vocab.json")

        env = OrtEnvironment.getEnvironment()
        session = env.createSession(modelPath, OrtSession.SessionOptions())

        val vocabJson = JSONObject(File(vocabPath).readText())
        val vocabMap = mutableMapOf<Char, Long>()
        vocabJson.keys().forEach { key ->
            if (key.length == 1) {
                vocabMap[key[0]] = vocabJson.getLong(key)
            }
        }
        vocab = vocabMap
    }

    override fun synthesize(text: String, langCode: String): ShortArray {
        val speakerId = speakerIds[langCode] ?: 2L // default to Bengali if unknown
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
        ids.add(0L) // leading blank
        for (char in text.lowercase()) {
            val id = vocab[char] ?: continue // skip unknown characters
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
