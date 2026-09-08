package com.itantra.app.evaluation

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports benchmark results to machine-readable formats (JSON, CSV).
 *
 * Results are saved to app-internal storage under the "benchmarks" directory.
 * Each run gets a unique filename with timestamp.
 */
class BenchmarkExporter(private val context: Context) {

    companion object {
        private const val TAG = "BenchmarkExporter"
        private const val BENCHMARK_DIR = "benchmarks"
    }

    private fun getBenchmarkDir(): File {
        val dir = File(context.filesDir, BENCHMARK_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Exports a complete benchmark report to JSON.
     */
    fun exportReportJson(report: BenchmarkReport): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(report.timestamp))
        val filename = "benchmark_report_${timestamp}.json"
        val file = File(getBenchmarkDir(), filename)

        val json = reportToJson(report)
        file.writeText(json.toString(2))

        Log.d(TAG, "Report exported to ${file.absolutePath}")
        return file
    }

    /**
     * Exports individual benchmark results to JSON.
     */
    fun exportResultsJson(results: List<BenchmarkResult>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "benchmark_results_${timestamp}.json"
        val file = File(getBenchmarkDir(), filename)

        val jsonArray = JSONArray()
        results.forEach { jsonArray.put(resultToJson(it)) }

        val wrapper = JSONObject()
        wrapper.put("export_timestamp", System.currentTimeMillis())
        wrapper.put("result_count", results.size)
        wrapper.put("results", jsonArray)

        file.writeText(wrapper.toString(2))

        Log.d(TAG, "Results exported to ${file.absolutePath} (${results.size} results)")
        return file
    }

    /**
     * Exports results to CSV format.
     */
    fun exportResultsCsv(results: List<BenchmarkResult>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "benchmark_results_${timestamp}.csv"
        val file = File(getBenchmarkDir(), filename)

        val sb = StringBuilder()

        // Header
        sb.appendLine("test_id,language,timestamp,mode," +
                "wer,reference_words,substitutions,deletions,insertions," +
                "stt_latency_ms,stt_rtf," +
                "tts_generation_ms,tts_rtf,tts_audio_duration_ms,tts_failed," +
                "transmission_bytes,transmission_latency_ms," +
                "e2e_latency_ms," +
                "pss_kb,native_heap_kb,java_heap_kb,cpu_percent," +
                "errors")

        // Data rows
        results.forEach { r ->
            sb.appendLine(buildString {
                append(r.testId)
                append(",")
                append(r.language.code)
                append(",")
                append(r.timestamp)
                append(",")
                append(r.mode.name)
                append(",")
                append(r.stt?.wer ?: "")
                append(",")
                append(r.stt?.referenceWordCount ?: "")
                append(",")
                append(r.stt?.substitutions ?: "")
                append(",")
                append(r.stt?.deletions ?: "")
                append(",")
                append(r.stt?.insertions ?: "")
                append(",")
                append(r.stt?.sttInferenceLatencyMs ?: "")
                append(",")
                append(r.stt?.sttRtf ?: "")
                append(",")
                append(r.tts?.generationTimeMs ?: "")
                append(",")
                append(r.tts?.ttsRtf ?: "")
                append(",")
                append(r.tts?.outputAudioDurationMs ?: "")
                append(",")
                append(r.tts?.failedSynthesis ?: "")
                append(",")
                append(r.transmission?.payloadBytes ?: "")
                append(",")
                append(r.transmission?.latencyMs ?: "")
                append(",")
                append(r.endToEnd?.totalLatencyMs ?: "")
                append(",")
                append(r.resourceSnapshot?.pssKb ?: "")
                append(",")
                append(r.resourceSnapshot?.nativeHeapKb ?: "")
                append(",")
                append(r.resourceSnapshot?.javaHeapKb ?: "")
                append(",")
                append(r.resourceSnapshot?.cpuUsagePercent ?: "")
                append(",")
                append(r.errors.joinToString(";").replace(",", ";"))
            })
        }

        file.writeText(sb.toString())

        Log.d(TAG, "CSV exported to ${file.absolutePath} (${results.size} rows)")
        return file
    }

    /**
     * Exports TTS human evaluation data for later scoring.
     */
    fun exportTtsEvaluationTemplate(testCases: List<TestCorpus.TestCase>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "tts_evaluation_template_${timestamp}.json"
        val file = File(getBenchmarkDir(), filename)

        val jsonArray = JSONArray()
        testCases.forEach { tc ->
            val obj = JSONObject()
            obj.put("test_id", tc.id)
            obj.put("language", tc.language.code)
            obj.put("reference_text", tc.referenceText)
            obj.put("scores", JSONObject().apply {
                put("intelligibility", 0)
                put("naturalness", 0)
                put("pronunciation", 0)
                put("flow", 0)
            })
            obj.put("listener_id", "")
            obj.put("notes", "")
            jsonArray.put(obj)
        }

        val wrapper = JSONObject()
        wrapper.put("evaluation_type", "tts_human")
        wrapper.put("scale", "1-5 (1=Very Poor, 5=Excellent)")
        wrapper.put("samples", jsonArray)

        file.writeText(wrapper.toString(2))

        Log.d(TAG, "TTS evaluation template exported to ${file.absolutePath}")
        return file
    }

    /**
     * Imports completed human evaluation scores.
     */
    fun importTtsEvaluationScores(file: File): List<HumanTtsScores> {
        val json = JSONObject(file.readText())
        val samples = json.getJSONArray("samples")
        val scores = mutableListOf<HumanTtsScores>()

        for (i in 0 until samples.length()) {
            val sample = samples.getJSONObject(i)
            val scoreObj = sample.getJSONObject("scores")
            scores.add(HumanTtsScores(
                intelligibility = scoreObj.optInt("intelligibility", 0),
                naturalness = scoreObj.optInt("naturalness", 0),
                pronunciation = scoreObj.optInt("pronunciation", 0),
                flow = scoreObj.optInt("flow", 0),
                listenerId = sample.optString("listener_id", ""),
                notes = sample.optString("notes", "")
            ))
        }
        return scores
    }

    /**
     * Lists all benchmark result files.
     */
    fun listResultFiles(): List<File> {
        return getBenchmarkDir().listFiles()?.filter {
            it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".csv"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun reportToJson(report: BenchmarkReport): JSONObject {
        val json = JSONObject()
        json.put("timestamp", report.timestamp)
        json.put("device_info", deviceInfoToJson(report.deviceInfo))
        json.put("model_sizes", modelSizesToJson(report.modelSizes))
        json.put("apk_size_bytes", report.apkSizeBytes)
        json.put("apk_installed_size_bytes", report.apkInstalledSizeBytes)
        json.put("total_test_cases", report.totalTestCases)
        json.put("total_runs", report.totalRuns)
        json.put("total_passed", report.totalPassed)
        json.put("total_failed", report.totalFailed)
        json.put("methodology", report.methodology)

        val limitsArr = JSONArray()
        report.limitations.forEach { limitsArr.put(it) }
        json.put("limitations", limitsArr)

        val langArr = JSONArray()
        report.languageReports.forEach { langArr.put(languageReportToJson(it)) }
        json.put("language_reports", langArr)

        return json
    }

    private fun deviceInfoToJson(info: DeviceInfo): JSONObject {
        return JSONObject().apply {
            put("model", info.model)
            put("manufacturer", info.manufacturer)
            put("android_version", info.androidVersion)
            put("android_sdk", info.androidSdkInt)
            put("cpu_architecture", info.cpuArchitecture)
            put("total_ram_mb", info.totalRamMb)
            put("available_ram_mb", info.availableRamMb)
            put("cpu_cores", info.cpuCores)
            put("cpu_max_freq_khz", info.cpuMaxFreqKhz)
        }
    }

    private fun modelSizesToJson(sizes: ModelSizes): JSONObject {
        return JSONObject().apply {
            put("stt_model_size_mb", sizes.sttModelSizeMb)
            put("vad_model_size_mb", sizes.vadModelSizeMb)
            put("total_ml_asset_size_mb", sizes.totalMlAssetSizeMb)
            put("tokenizer_vocab_size", sizes.tokenizerVocabSize)
            val ttsObj = JSONObject()
            sizes.ttsModelSizesMb.forEach { (k, v) -> ttsObj.put(k, v) }
            put("tts_model_sizes_mb", ttsObj)
        }
    }

    private fun languageReportToJson(report: LanguageBenchmarkReport): JSONObject {
        return JSONObject().apply {
            put("language", report.language.code)
            put("implemented", report.implemented)
            put("overall_wer_mean", report.overallWerMean)
            put("overall_stt_latency_mean_ms", report.overallSttLatencyMean)
            put("overall_tts_latency_mean_ms", report.overallTtsLatencyMean)
            put("overall_e2e_latency_mean_ms", report.overallE2eLatencyMean)
            put("overall_stt_rtf_mean", report.overallSttRtfMean)
            put("overall_tts_rtf_mean", report.overallTtsRtfMean)
            put("notes", report.notes)

            val sumsArr = JSONArray()
            report.summaries.forEach { sumsArr.put(summaryToJson(it)) }
            put("summaries", sumsArr)
        }
    }

    private fun summaryToJson(s: BenchmarkRunSummary): JSONObject {
        return JSONObject().apply {
            put("test_id", s.testId)
            put("language", s.language.code)
            put("run_count", s.runCount)
            put("successful_runs", s.successfulRuns)
            put("failed_runs", s.failedRuns)
            put("wer_mean", s.werMean)
            put("wer_median", s.werMedian)
            put("wer_p95", s.werP95)
            put("stt_latency_mean_ms", s.sttLatencyMean)
            put("stt_latency_p95_ms", s.sttLatencyP95)
            put("tts_latency_mean_ms", s.ttsLatencyMean)
            put("tts_latency_p95_ms", s.ttsLatencyP95)
            put("e2e_latency_mean_ms", s.e2eLatencyMean)
            put("e2e_latency_p95_ms", s.e2eLatencyP95)
            put("transmission_latency_mean_ms", s.transmissionLatencyMean)
            put("transmission_latency_p95_ms", s.transmissionLatencyP95)
            put("stt_rtf_mean", s.sttRtfMean)
            put("tts_rtf_mean", s.ttsRtfMean)
            put("tts_generation_mean_ms", s.ttsGenerationMean)
            put("tts_failed_synthesis_rate", s.ttsFailedSynthesisRate)
            put("peak_ram_mb", s.peakRamMb)
            put("avg_cpu_percent", s.avgCpuPercent)
        }
    }

    private fun resultToJson(r: BenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("test_id", r.testId)
            put("language", r.language.code)
            put("timestamp", r.timestamp)
            put("mode", r.mode.name)
            put("device_info", deviceInfoToJson(r.deviceInfo))

            r.stt?.let { stt ->
                put("stt", JSONObject().apply {
                    put("reference_text", stt.referenceText)
                    put("recognized_text", stt.recognizedText)
                    put("wer", stt.wer)
                    put("substitutions", stt.substitutions)
                    put("deletions", stt.deletions)
                    put("insertions", stt.insertions)
                    put("reference_word_count", stt.referenceWordCount)
                    put("inference_latency_ms", stt.sttInferenceLatencyMs)
                    put("vad_detection_latency_ms", stt.vadDetectionLatencyMs)
                    put("total_stt_latency_ms", stt.totalSttLatencyMs)
                    put("audio_duration_ms", stt.audioDurationMs)
                    put("stt_rtf", stt.sttRtf)
                })
            }

            r.tts?.let { tts ->
                put("tts", JSONObject().apply {
                    put("text", tts.text)
                    put("language", tts.language.code)
                    put("generation_time_ms", tts.generationTimeMs)
                    put("output_audio_duration_ms", tts.outputAudioDurationMs)
                    put("output_audio_size_bytes", tts.outputAudioSizeBytes)
                    put("sample_rate", tts.sampleRate)
                    put("clipping_percentage", tts.clippingPercentage)
                    put("silence_percentage", tts.silencePercentage)
                    put("peak_amplitude", tts.peakAmplitude)
                    put("rms_amplitude", tts.rmsAmplitude)
                    put("failed_synthesis", tts.failedSynthesis)
                    put("inference_latency_ms", tts.ttsInferenceLatencyMs)
                    put("first_audio_sample_latency_ms", tts.firstAudioSampleLatencyMs)
                    put("tts_rtf", tts.ttsRtf)
                })
            }

            r.transmission?.let { tx ->
                put("transmission", JSONObject().apply {
                    put("payload_bytes", tx.payloadBytes)
                    put("latency_ms", tx.latencyMs)
                    put("encoding_latency_ms", tx.encodingLatencyMs)
                })
            }

            r.endToEnd?.let { e2e ->
                put("end_to_end", JSONObject().apply {
                    put("total_latency_ms", e2e.totalLatencyMs)
                    put("first_remote_audio_ms", e2e.firstRemoteAudioMs)
                })
            }

            r.resourceSnapshot?.let { res ->
                put("resource_snapshot", JSONObject().apply {
                    put("pss_kb", res.pssKb)
                    put("native_heap_kb", res.nativeHeapKb)
                    put("java_heap_kb", res.javaHeapKb)
                    put("cpu_percent", res.cpuUsagePercent)
                    put("battery_level", res.batteryLevel)
                    put("is_charging", res.isCharging)
                    put("temperature", res.temperature)
                })
            }

            if (r.errors.isNotEmpty()) {
                val errArr = JSONArray()
                r.errors.forEach { errArr.put(it) }
                put("errors", errArr)
            }
        }
    }
}
