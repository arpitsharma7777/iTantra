package com.itantra.app.evaluation

import com.itantra.app.core.model.Language

/**
 * Aggregated summary of multiple benchmark runs for a single test case.
 */
data class BenchmarkRunSummary(
    val testId: String,
    val language: Language,
    val runCount: Int,
    val successfulRuns: Int,
    val failedRuns: Int,

    // STT WER statistics
    val werMean: Float = 0f,
    val werMedian: Float = 0f,
    val werStdDev: Float = 0f,
    val werMin: Float = 0f,
    val werMax: Float = 0f,
    val werP95: Float = 0f,

    // Latency statistics (ms)
    val sttLatencyMean: Float = 0f,
    val sttLatencyMedian: Float = 0f,
    val sttLatencyStdDev: Float = 0f,
    val sttLatencyMin: Long = 0,
    val sttLatencyMax: Long = 0,
    val sttLatencyP95: Long = 0,

    val ttsLatencyMean: Float = 0f,
    val ttsLatencyMedian: Float = 0f,
    val ttsLatencyStdDev: Float = 0f,
    val ttsLatencyMin: Long = 0,
    val ttsLatencyMax: Long = 0,
    val ttsLatencyP95: Long = 0,

    val e2eLatencyMean: Float = 0f,
    val e2eLatencyMedian: Float = 0f,
    val e2eLatencyStdDev: Float = 0f,
    val e2eLatencyMin: Long = 0,
    val e2eLatencyMax: Long = 0,
    val e2eLatencyP95: Long = 0,

    val transmissionLatencyMean: Float = 0f,
    val transmissionLatencyP95: Long = 0,

    // RTF statistics
    val sttRtfMean: Float = 0f,
    val ttsRtfMean: Float = 0f,

    // TTS quality
    val ttsGenerationMean: Float = 0f,
    val ttsFailedSynthesisRate: Float = 0f,

    // Resource usage
    val peakRamMb: Float = 0f,
    val avgCpuPercent: Float = 0f,

    // Raw results
    val results: List<BenchmarkResult> = emptyList()
)

/**
 * Language-level aggregated benchmark report.
 */
data class LanguageBenchmarkReport(
    val language: Language,
    val summaries: List<BenchmarkRunSummary>,
    val overallWerMean: Float = 0f,
    val overallSttLatencyMean: Float = 0f,
    val overallTtsLatencyMean: Float = 0f,
    val overallE2eLatencyMean: Float = 0f,
    val overallSttRtfMean: Float = 0f,
    val overallTtsRtfMean: Float = 0f,
    val implemented: Boolean = true,
    val notes: String = ""
)

/**
 * Complete benchmark report across all languages.
 */
data class BenchmarkReport(
    val timestamp: Long = System.currentTimeMillis(),
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val config: BenchmarkConfig? = null,

    // Efficiency
    val modelSizes: ModelSizes = ModelSizes(),
    val apkSizeBytes: Long = 0,
    val apkInstalledSizeBytes: Long = 0,

    // Per-language reports
    val languageReports: List<LanguageBenchmarkReport> = emptyList(),

    // Summary
    val totalTestCases: Int = 0,
    val totalRuns: Int = 0,
    val totalPassed: Int = 0,
    val totalFailed: Int = 0,

    val methodology: String = "",
    val limitations: List<String> = emptyList()
)

data class ModelSizes(
    val sttModelSizeMb: Float = 0f,
    val ttsModelSizesMb: Map<String, Float> = emptyMap(),
    val vadModelSizeMb: Float = 0f,
    val tokenizerVocabSize: Int = 0,
    val totalMlAssetSizeMb: Float = 0f
)
