package com.itantra.app.evaluation

import com.itantra.app.core.model.Language

/**
 * Complete result of a single benchmark test case execution.
 */
data class BenchmarkResult(
    val testId: String,
    val language: Language,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val mode: BenchmarkMode = BenchmarkMode.WARM,

    // STT metrics
    val stt: SttMetrics? = null,

    // TTS metrics
    val tts: TtsMetrics? = null,

    // Transmission metrics
    val transmission: TransmissionMetrics? = null,

    // End-to-end
    val endToEnd: EndToEndMetrics? = null,

    // Resource usage during this test case
    val resourceSnapshot: ResourceSnapshot? = null,

    // Errors
    val errors: List<String> = emptyList()
)

data class DeviceInfo(
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val androidSdkInt: Int = 0,
    val cpuArchitecture: String = "",
    val totalRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val cpuCores: Int = 0,
    val cpuMaxFreqKhz: Long = 0
)

data class SttMetrics(
    val referenceText: String = "",
    val recognizedText: String = "",
    val wer: Float = 0f,
    val substitutions: Int = 0,
    val deletions: Int = 0,
    val insertions: Int = 0,
    val referenceWordCount: Int = 0,

    // Latency
    val sttInferenceLatencyMs: Long = 0,
    val vadDetectionLatencyMs: Long = 0,
    val totalSttLatencyMs: Long = 0,

    // RTF
    val audioDurationMs: Long = 0,
    val sttRtf: Float = 0f
)

data class TtsMetrics(
    val text: String = "",
    val language: Language = Language.ENGLISH,

    // Quality (automated)
    val generationTimeMs: Long = 0,
    val outputAudioDurationMs: Long = 0,
    val outputAudioSizeBytes: Long = 0,
    val sampleRate: Int = 0,
    val channels: Int = 1,
    val clippingPercentage: Float = 0f,
    val silencePercentage: Float = 0f,
    val peakAmplitude: Float = 0f,
    val rmsAmplitude: Float = 0f,
    val failedSynthesis: Boolean = false,

    // Latency
    val ttsInferenceLatencyMs: Long = 0,
    val firstAudioSampleLatencyMs: Long = 0,
    val playbackStartLatencyMs: Long = 0,

    // RTF
    val ttsRtf: Float = 0f,

    // Human evaluation (filled later)
    val humanScores: HumanTtsScores? = null
)

data class HumanTtsScores(
    val intelligibility: Int = 0,  // 1-5
    val naturalness: Int = 0,      // 1-5
    val pronunciation: Int = 0,    // 1-5
    val flow: Int = 0,             // 1-5
    val listenerId: String = "",
    val notes: String = ""
)

data class TransmissionMetrics(
    val payloadBytes: Int = 0,
    val latencyMs: Long = 0,
    val encodingLatencyMs: Long = 0
)

data class EndToEndMetrics(
    val totalLatencyMs: Long = 0,
    val firstRemoteAudioMs: Long = 0,
    val ttsStartMs: Long = 0
)

data class ResourceSnapshot(
    val timestamp: Long = System.currentTimeMillis(),

    // RAM
    val pssKb: Long = 0,
    val javaHeapKb: Long = 0,
    val nativeHeapKb: Long = 0,

    // CPU
    val cpuUsagePercent: Float = 0f,
    val cpuMeasurementDurationMs: Long = 0,

    // Device conditions
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val temperature: Float = -1f
)
