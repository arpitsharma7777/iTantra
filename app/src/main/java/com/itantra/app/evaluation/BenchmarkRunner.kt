package com.itantra.app.evaluation

import android.content.Context
import android.util.Log
import com.itantra.app.core.model.Language
import com.itantra.app.stt.SttManager
import com.itantra.app.stt.SttState
import com.itantra.app.tts.TtsManager
import com.itantra.app.tts.TtsState
import com.itantra.app.transport.TransportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates benchmark execution.
 *
 * The runner:
 * 1. Collects device info and model sizes
 * 2. Iterates over test cases for the target language(s)
 * 3. For each test case, executes the requested pipeline stages
 * 4. Records all measurements
 * 5. Aggregates results into statistical summaries
 * 6. Exports to JSON/CSV
 *
 * The runner is designed to be invoked from the BenchmarkScreen or programmatically.
 * It does NOT modify production pipeline behavior.
 *
 * Important limitations:
 * - STT accuracy (WER) requires actual STT inference which is currently a skeleton.
 *   WER will only be meaningful when a real STT model is integrated.
 * - End-to-end latency requires two devices. The runner measures single-device
 *   pipeline stages and documents this limitation.
 * - TTS evaluation uses the production TtsManager.speak() which blocks until
 *   playback completes. For more precise timing, instrument the TTS engines directly.
 */
class BenchmarkRunner(
    private val context: Context,
    private val sttManager: SttManager?,
    private val ttsManager: TtsManager?,
    private val transportManager: TransportManager?
) {
    companion object {
        private const val TAG = "BenchmarkRunner"
        private const val WARMUP_RUNS = 3
    }

    private val deviceCollector = BenchmarkDeviceCollector(context)
    private val modelSizeCollector = ModelSizeCollector(context)
    private val latencyTracker = LatencyTracker()
    private val resourceMonitor = ResourceMonitor(context)
    private val exporter = BenchmarkExporter(context)

    private val isRunning = AtomicBoolean(false)

    val isBenchmarkRunning: Boolean get() = isRunning.get()

    /**
     * Executes a full benchmark run.
     *
     * @param config Benchmark configuration
     * @param onProgress Callback with progress (0.0 to 1.0) and status message
     * @return Complete list of benchmark results
     */
    suspend fun run(
        config: BenchmarkConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): BenchmarkResultBundle = withContext(Dispatchers.IO) {
        if (isRunning.compareAndSet(false, true)) {
            try {
                executeBenchmark(config, onProgress)
            } finally {
                isRunning.set(false)
            }
        } else {
            Log.w(TAG, "Benchmark already running")
            BenchmarkResultBundle(emptyList(), BenchmarkReport())
        }
    }

    private suspend fun executeBenchmark(
        config: BenchmarkConfig,
        onProgress: (Float, String) -> Unit
    ): BenchmarkResultBundle {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting benchmark: lang=${config.language.code}, runs=${config.runs}, mode=${config.mode}")

        onProgress(0f, "Collecting device info...")
        val deviceInfo = deviceCollector.collect()
        val modelSizes = modelSizeCollector.collect()
        val apkSize = modelSizeCollector.measureApkSize()
        val installedSize = modelSizeCollector.measureInstalledSize()

        // Get test cases
        val corpus = if (config.testCorpusIds != null) {
            config.testCorpusIds.mapNotNull { TestCorpus.getById(it) }
        } else {
            TestCorpus.getForLanguage(config.language)
        }

        if (corpus.isEmpty()) {
            Log.w(TAG, "No test cases found for ${config.language.code}")
            return BenchmarkResultBundle(
                emptyList(),
                BenchmarkReport(
                    deviceInfo = deviceInfo,
                    modelSizes = modelSizes,
                    apkSizeBytes = apkSize,
                    apkInstalledSizeBytes = installedSize,
                    limitations = listOf("No test cases available for ${config.language.code}")
                )
            )
        }

        val allResults = mutableListOf<BenchmarkResult>()
        val totalIterations = corpus.size * config.runs

        for ((caseIdx, testCase) in corpus.withIndex()) {
            Log.d(TAG, "Test case ${caseIdx + 1}/${corpus.size}: ${testCase.id}")

            // Warmup runs
            onProgress(
                (caseIdx * config.runs).toFloat() / totalIterations,
                "Warming up for ${testCase.id}..."
            )
            for (i in 0 until WARMUP_RUNS) {
                executeSingleRun(testCase, config, isWarmup = true)
            }

            // Measured runs
            for (runIdx in 0 until config.runs) {
                val progress = (caseIdx * config.runs + runIdx).toFloat() / totalIterations
                onProgress(progress, "Running ${testCase.id} (${runIdx + 1}/${config.runs})...")

                val result = executeSingleRun(testCase, config, isWarmup = false)
                allResults.add(result)
            }
        }

        onProgress(0.95f, "Generating report...")

        // Aggregate results
        val summaries = aggregateResults(allResults)
        val report = generateReport(
            deviceInfo, modelSizes, apkSize, installedSize,
            config, summaries, allResults
        )

        // Export
        val exportFile = exporter.exportResultsJson(allResults)
        val csvFile = exporter.exportResultsCsv(allResults)
        val reportFile = exporter.exportReportJson(report)

        Log.d(TAG, "Benchmark complete. ${allResults.size} results. Files: ${exportFile.name}, ${csvFile.name}, ${reportFile.name}")

        onProgress(1f, "Complete. ${allResults.size} results saved.")

        return BenchmarkResultBundle(allResults, report)
    }

    private suspend fun executeSingleRun(
        testCase: TestCorpus.TestCase,
        config: BenchmarkConfig,
        isWarmup: Boolean
    ): BenchmarkResult {
        val sessionId = "${testCase.id}_${System.currentTimeMillis()}"
        val errors = mutableListOf<String>()

        // Capture initial resource state
        val startResource = if (config.includeResourceMonitoring) resourceMonitor.snapshot() else null

        // === TTS Measurement ===
        var ttsMetrics: TtsMetrics? = null
        if (config.includeTts && ttsManager != null) {
            try {
                val ttsEvaluator = TtsQualityEvaluator(ttsManager)
                val ttsResult = latencyTracker.startSession("${sessionId}_tts")
                latencyTracker.recordCheckpoint(ttsResult, LatencyTracker.Checkpoints.T7_TTS_INFERENCE_STARTS)

                ttsMetrics = ttsEvaluator.evaluate(
                    text = testCase.referenceText,
                    language = testCase.language,
                    sampleRate = getSampleRateForLanguage(testCase.language)
                )

                latencyTracker.recordCheckpoint(ttsResult, LatencyTracker.Checkpoints.T8_FIRST_AUDIO_SAMPLE)
                latencyTracker.endSession(ttsResult)
            } catch (e: Exception) {
                errors.add("TTS evaluation failed: ${e.message}")
                Log.e(TAG, "TTS evaluation failed for ${testCase.id}", e)
            }
        }

        // === STT Measurement ===
        var sttMetrics: SttMetrics? = null
        if (config.includeStt && sttManager != null) {
            try {
                val sttStartTime = System.currentTimeMillis()
                val sttSessionId = sttManager.startListening()
                if (sttSessionId != -1) {
                    kotlinx.coroutines.delay(testCase.estimatedAudioDurationMs)
                    sttManager.stopListening()
                    kotlinx.coroutines.delay(500)

                    val sttResult = sttManager.result.value
                    val recognizedText = sttResult?.finalText ?: sttResult?.partialText ?: ""
                    val sttLatency = System.currentTimeMillis() - sttStartTime

                    val wer = if (recognizedText.isNotBlank()) {
                        EnhancedWerCalculator.calculate(
                            reference = testCase.referenceText,
                            recognized = recognizedText
                        ).wer
                    } else {
                        1.0f
                    }

                    val audioDurationSec = testCase.estimatedAudioDurationMs / 1000f
                    val sttRtf = if (audioDurationSec > 0f) sttLatency / (audioDurationSec * 1000f) else 0f

                    sttMetrics = SttMetrics(
                        referenceText = testCase.referenceText,
                        recognizedText = recognizedText,
                        wer = wer,
                        referenceWordCount = EnhancedWerCalculator.tokenize(testCase.referenceText).size,
                        audioDurationMs = testCase.estimatedAudioDurationMs,
                        sttRtf = sttRtf
                    )
                    if (!isWarmup) {
                        Log.d(TAG, "STT result for ${testCase.id}: WER=${String.format("%.2f", wer)}, text=\"$recognizedText\"")
                    }
                } else {
                    sttMetrics = SttMetrics(
                        referenceText = testCase.referenceText,
                        recognizedText = "(STT failed to start)",
                        wer = 1.0f,
                        referenceWordCount = EnhancedWerCalculator.tokenize(testCase.referenceText).size,
                        audioDurationMs = testCase.estimatedAudioDurationMs,
                        sttRtf = 0f
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT evaluation failed for ${testCase.id}", e)
                sttMetrics = SttMetrics(
                    referenceText = testCase.referenceText,
                    recognizedText = "(STT error: ${e.message})",
                    wer = 1.0f,
                    referenceWordCount = EnhancedWerCalculator.tokenize(testCase.referenceText).size,
                    audioDurationMs = testCase.estimatedAudioDurationMs,
                    sttRtf = 0f
                )
            }
        }

        // === Resource snapshot ===
        val endResource = if (config.includeResourceMonitoring) resourceMonitor.snapshot() else null

        // Combine resource snapshots
        val resourceSnapshot = if (startResource != null && endResource != null) {
            ResourceSnapshot(
                timestamp = endResource.timestamp,
                pssKb = maxOf(startResource.pssKb, endResource.pssKb),
                javaHeapKb = maxOf(startResource.javaHeapKb, endResource.javaHeapKb),
                nativeHeapKb = maxOf(startResource.nativeHeapKb, endResource.nativeHeapKb),
                cpuUsagePercent = endResource.cpuUsagePercent,
                batteryLevel = endResource.batteryLevel,
                isCharging = endResource.isCharging,
                temperature = endResource.temperature
            )
        } else endResource

        return BenchmarkResult(
            testId = testCase.id,
            language = testCase.language,
            timestamp = System.currentTimeMillis(),
            deviceInfo = if (!isWarmup) deviceCollector.collect() else DeviceInfo(),
            mode = config.mode,
            stt = sttMetrics,
            tts = ttsMetrics,
            resourceSnapshot = resourceSnapshot,
            errors = errors
        )
    }

    private fun getSampleRateForLanguage(language: Language): Int {
        return when (language) {
            Language.ENGLISH -> 22050
            Language.HINDI, Language.GUJARATI, Language.ODIA -> 16000
            else -> 24000
        }
    }

    private fun aggregateResults(results: List<BenchmarkResult>): Map<String, BenchmarkRunSummary> {
        val grouped = results.groupBy { it.testId }
        val summaries = mutableMapOf<String, BenchmarkRunSummary>()

        for ((testId, runs) in grouped) {
            val language = runs.first().language
            val successful = runs.filter { it.errors.isEmpty() }
            val failed = runs.filter { it.errors.isNotEmpty() }

            // WER statistics
            val werValues = successful.mapNotNull { it.stt?.wer }
            val sttLatencies = successful.mapNotNull { it.stt?.sttInferenceLatencyMs }
            val ttsLatencies = successful.mapNotNull { it.tts?.generationTimeMs }
            val e2eLatencies = successful.mapNotNull { it.endToEnd?.totalLatencyMs }
            val txLatencies = successful.mapNotNull { it.transmission?.latencyMs }
            val sttRtfs = successful.mapNotNull { it.stt?.sttRtf }.filter { it < Float.MAX_VALUE }
            val ttsRtfs = successful.mapNotNull { it.tts?.ttsRtf }.filter { it < Float.MAX_VALUE }
            val ramValues = successful.mapNotNull { it.resourceSnapshot?.pssKb }
            val cpuValues = successful.mapNotNull { it.resourceSnapshot?.cpuUsagePercent }

            summaries[testId] = BenchmarkRunSummary(
                testId = testId,
                language = language,
                runCount = runs.size,
                successfulRuns = successful.size,
                failedRuns = failed.size,
                werMean = werValues.mean(),
                werMedian = werValues.median(),
                werStdDev = werValues.stdDev(),
                werMin = werValues.minOrNull() ?: 0f,
                werMax = werValues.maxOrNull() ?: 0f,
                werP95 = werValues.p95(),
                sttLatencyMean = sttLatencies.map { it.toFloat() }.mean(),
                sttLatencyMedian = sttLatencies.map { it.toFloat() }.median(),
                sttLatencyStdDev = sttLatencies.map { it.toFloat() }.stdDev(),
                sttLatencyMin = sttLatencies.minOrNull() ?: 0L,
                sttLatencyMax = sttLatencies.maxOrNull() ?: 0L,
                sttLatencyP95 = sttLatencies.p95Long(),
                ttsLatencyMean = ttsLatencies.map { it.toFloat() }.mean(),
                ttsLatencyMedian = ttsLatencies.map { it.toFloat() }.median(),
                ttsLatencyStdDev = ttsLatencies.map { it.toFloat() }.stdDev(),
                ttsLatencyMin = ttsLatencies.minOrNull() ?: 0L,
                ttsLatencyMax = ttsLatencies.maxOrNull() ?: 0L,
                ttsLatencyP95 = ttsLatencies.p95Long(),
                e2eLatencyMean = e2eLatencies.map { it.toFloat() }.mean(),
                e2eLatencyMedian = e2eLatencies.map { it.toFloat() }.median(),
                e2eLatencyStdDev = e2eLatencies.map { it.toFloat() }.stdDev(),
                e2eLatencyMin = e2eLatencies.minOrNull() ?: 0L,
                e2eLatencyMax = e2eLatencies.maxOrNull() ?: 0L,
                e2eLatencyP95 = e2eLatencies.p95Long(),
                transmissionLatencyMean = txLatencies.map { it.toFloat() }.mean(),
                transmissionLatencyP95 = txLatencies.p95Long(),
                sttRtfMean = sttRtfs.mean(),
                ttsRtfMean = ttsRtfs.mean(),
                ttsGenerationMean = successful.mapNotNull { it.tts?.generationTimeMs?.toFloat() }.mean(),
                ttsFailedSynthesisRate = if (runs.isNotEmpty()) {
                    runs.count { it.tts?.failedSynthesis == true }.toFloat() / runs.size
                } else 0f,
                peakRamMb = ramValues.maxOrNull()?.let { it / 1024f } ?: 0f,
                avgCpuPercent = cpuValues.mean(),
                results = runs
            )
        }

        return summaries
    }

    private fun generateReport(
        deviceInfo: DeviceInfo,
        modelSizes: ModelSizes,
        apkSize: Long,
        installedSize: Long,
        config: BenchmarkConfig,
        summaries: Map<String, BenchmarkRunSummary>,
        allResults: List<BenchmarkResult>
    ): BenchmarkReport {
        val langSummaries = summaries.values.groupBy { it.language }

        val languageReports = langSummaries.map { (lang, langSummaries) ->
            LanguageBenchmarkReport(
                language = lang,
                summaries = langSummaries,
                overallWerMean = langSummaries.map { it.werMean }.mean(),
                overallSttLatencyMean = langSummaries.map { it.sttLatencyMean }.mean(),
                overallTtsLatencyMean = langSummaries.map { it.ttsLatencyMean }.mean(),
                overallE2eLatencyMean = langSummaries.map { it.e2eLatencyMean }.mean(),
                overallSttRtfMean = langSummaries.map { it.sttRtfMean }.mean(),
                overallTtsRtfMean = langSummaries.map { it.ttsRtfMean }.mean(),
                implemented = true
            )
        }

        return BenchmarkReport(
            timestamp = System.currentTimeMillis(),
            deviceInfo = deviceInfo,
            config = config,
            modelSizes = modelSizes,
            apkSizeBytes = apkSize,
            apkInstalledSizeBytes = installedSize,
            languageReports = languageReports,
            totalTestCases = summaries.size,
            totalRuns = allResults.size,
            totalPassed = allResults.count { it.errors.isEmpty() },
            totalFailed = allResults.count { it.errors.isNotEmpty() },
            methodology = buildMethodology(),
            limitations = buildLimitations()
        )
    }

    private fun buildMethodology(): String {
        return """
            Benchmark methodology:
            1. Device info collected via Android APIs (Build, ActivityManager, /proc)
            2. Model sizes measured from actual asset files on device
            3. TTS quality evaluated using production TtsManager.speak()
            4. STT accuracy: IndicConformer (NeMo CTC) via Sherpa-ONNX; WER computed against reference text
            5. CPU measured via /proc/self/stat sampling (approximate)
            6. RAM measured via Debug.MemoryInfo (PSS/heap breakdown)
            7. Warmup runs ($WARMUP_RUNS) separated from measured runs
            8. Statistical summaries include mean, median, std dev, P95
            9. End-to-end latency: single-device measurement only; cross-device
               synchronization not available without two-device setup
        """.trimIndent()
    }

    private fun buildLimitations(): List<String> {
        val limitations = mutableListOf<String>()
        limitations.add("STT uses IndicConformer (NeMo CTC) model; accuracy may vary for low-resource languages")
        limitations.add("CPU measurement is approximate (process-level via /proc/self/stat)")
        limitations.add("End-to-end latency measured on single device only; clock sync unavailable")
        limitations.add("TTS audio quality analysis limited without raw audio capture")
        limitations.add("Temperature reading may not be available on all devices")
        limitations.add("PSS measurement may be restricted on some OEM ROMs")
        return limitations
    }

    data class BenchmarkResultBundle(
        val results: List<BenchmarkResult>,
        val report: BenchmarkReport
    )

    // Statistical helper extensions
    private fun List<Float>.mean(): Float = if (isEmpty()) 0f else average().toFloat()
    private fun List<Long>.meanOfLongs(): Float = if (isEmpty()) 0f else average().toFloat()
    private fun List<Float>.median(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        val n = sorted.size
        return if (n % 2 == 0) (sorted[n / 2 - 1] + sorted[n / 2]) / 2f else sorted[n / 2]
    }
    private fun List<Float>.stdDev(): Float {
        if (size < 2) return 0f
        val avg = average()
        return kotlin.math.sqrt(sumOf { ((it - avg) * (it - avg)).toDouble() } / (size - 1)).toFloat()
    }
    private fun List<Float>.p95(): Float {
        if (isEmpty()) return 0f
        val sorted = sorted()
        return sorted[(size * 0.95).toInt().coerceAtMost(size - 1)]
    }
    private fun List<Long>.p95Long(): Long {
        if (isEmpty()) return 0L
        val sorted = sorted()
        return sorted[(size * 0.95).toInt().coerceAtMost(size - 1)]
    }
}
