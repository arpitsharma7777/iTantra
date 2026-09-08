package com.itantra.app.evaluation

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a human-readable benchmark report in Markdown format.
 * Suitable for SIH presentation and documentation.
 */
class BenchmarkReportGenerator(private val context: Context) {

    companion object {
        private const val TAG = "BenchmarkReportGen"
        private const val REPORT_DIR = "benchmarks"
    }

    fun generate(report: BenchmarkReport): File {
        val dir = File(context.filesDir, REPORT_DIR)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(report.timestamp))
        val filename = "benchmark_report_${timestamp}.md"
        val file = File(dir, filename)

        val md = buildString {
            appendLine("# iTantra Benchmark Report")
            appendLine()
            appendLine("**Generated:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}")
            appendLine()
            appendLine("---")
            appendLine()

            // A. Executive Summary
            appendLine("## A. Executive Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Device | ${report.deviceInfo.manufacturer} ${report.deviceInfo.model} |")
            appendLine("| Android | ${report.deviceInfo.androidVersion} (SDK ${report.deviceInfo.androidSdkInt}) |")
            appendLine("| CPU | ${report.deviceInfo.cpuArchitecture}, ${report.deviceInfo.cpuCores} cores |")
            appendLine("| RAM | ${report.deviceInfo.totalRamMb} MB total |")
            appendLine("| Total Test Cases | ${report.totalTestCases} |")
            appendLine("| Total Runs | ${report.totalRuns} |")
            appendLine("| Passed | ${report.totalPassed} |")
            appendLine("| Failed | ${report.totalFailed} |")
            appendLine()

            // B. Efficiency
            appendLine("## B. Efficiency")
            appendLine()
            appendLine("### B.1 Model Sizes")
            appendLine()
            appendLine("| Model | Size (MB) |")
            appendLine("|-------|-----------|")
            appendLine("| STT (Whisper tiny) | ${String.format("%.2f", report.modelSizes.sttModelSizeMb)} |")
            appendLine("| VAD (Silero) | ${String.format("%.2f", report.modelSizes.vadModelSizeMb)} |")
            report.modelSizes.ttsModelSizesMb.forEach { (name, size) ->
                appendLine("| TTS ($name) | ${String.format("%.2f", size)} |")
            }
            appendLine("| **Total ML Assets** | **${String.format("%.2f", report.modelSizes.totalMlAssetSizeMb)}** |")
            appendLine()
            appendLine("### B.2 Application Size")
            appendLine()
            appendLine("| Metric | Size |")
            appendLine("|--------|------|")
            appendLine("| APK Size | ${formatBytes(report.apkSizeBytes)} |")
            appendLine("| Installed Size | ${formatBytes(report.apkInstalledSizeBytes)} |")
            appendLine()

            // C. STT Accuracy
            appendLine("## C. STT Accuracy (Word Error Rate)")
            appendLine()
            appendLine("### C. STT Accuracy (Word Error Rate)")
            appendLine()
            if (report.languageReports.isNotEmpty()) {
                appendLine("| Language | WER Mean | WER Median | WER P95 | Status |")
                appendLine("|----------|----------|------------|---------|--------|")
                report.languageReports.forEach { langReport ->
                    val werStr = if (langReport.implemented && langReport.overallWerMean > 0f) {
                        "${String.format("%.1f", langReport.overallWerMean * 100)}%"
                    } else {
                        "N/A"
                    }
                    appendLine("| ${langReport.language.displayName} | $werStr | - | - | ${if (langReport.implemented) "Implemented" else "Not Implemented"} |")
                }
            }
            appendLine()

            // D. TTS Quality
            appendLine("## D. TTS Quality")
            appendLine()
            appendLine("### D.1 Automated Metrics")
            appendLine()
            appendLine("| Language | Generation (ms) | RTF | Failed Rate | Status |")
            appendLine("|----------|-----------------|-----|-------------|--------|")
            report.languageReports.forEach { langReport ->
                val ttsSummary = langReport.summaries.firstOrNull()
                val genTime = ttsSummary?.let { "${String.format("%.0f", it.ttsGenerationMean)}" } ?: "-"
                val rtf = ttsSummary?.let { "${String.format("%.3f", it.ttsRtfMean)}" } ?: "-"
                val failRate = ttsSummary?.let { "${String.format("%.1f", it.ttsFailedSynthesisRate * 100)}%" } ?: "-"
                appendLine("| ${langReport.language.displayName} | $genTime | $rtf | $failRate | Implemented |")
            }
            appendLine()
            appendLine("### D.2 Human Evaluation")
            appendLine()
            appendLine("Human evaluation requires manual scoring via the TTS Evaluation Screen.")
            appendLine("Scores: 1 (Very Poor) to 5 (Excellent) on Intelligibility, Naturalness, Pronunciation, Flow.")
            appendLine()

            // E. Latency
            appendLine("## E. Latency")
            appendLine()
            appendLine("| Language | STT Latency (ms) | TTS Latency (ms) | E2E Latency (ms) | TX Latency (ms) |")
            appendLine("|----------|-------------------|-------------------|-------------------|-----------------|")
            report.languageReports.forEach { langReport ->
                val s = langReport.summaries.firstOrNull()
                val stt = s?.let { "${String.format("%.0f", it.sttLatencyMean)}" } ?: "-"
                val tts = s?.let { "${String.format("%.0f", it.ttsLatencyMean)}" } ?: "-"
                val e2e = s?.let { "${String.format("%.0f", it.e2eLatencyMean)}" } ?: "-"
                val tx = s?.let { "${String.format("%.0f", it.transmissionLatencyMean)}" } ?: "-"
                appendLine("| ${langReport.language.displayName} | $stt | $tts | $e2e | $tx |")
            }
            appendLine()

            // F. RTF
            appendLine("## F. Real-Time Factor (RTF)")
            appendLine()
            appendLine("| Language | STT RTF | TTS RTF | Interpretation |")
            appendLine("|----------|---------|---------|----------------|")
            report.languageReports.forEach { langReport ->
                val sttRtf = langReport.overallSttRtfMean
                val ttsRtf = langReport.overallTtsRtfMean
                val sttStr = if (sttRtf > 0) String.format("%.3f", sttRtf) else "N/A"
                val ttsStr = if (ttsRtf > 0) String.format("%.3f", ttsRtf) else "N/A"
                val interp = if (ttsRtf > 0) RtfCalculator.interpret(ttsRtf) else "N/A"
                appendLine("| ${langReport.language.displayName} | $sttStr | $ttsStr | $interp |")
            }
            appendLine()

            // G. Language-wise comparison
            appendLine("## G. Language-wise Comparison")
            appendLine()
            if (report.languageReports.isNotEmpty()) {
                appendLine("| Language | Implemented | WER | STT RTF | TTS RTF | TTS Gen (ms) |")
                appendLine("|----------|-------------|-----|---------|---------|--------------|")
                report.languageReports.forEach { langReport ->
                    val s = langReport.summaries.firstOrNull()
                    val wer = if (langReport.overallWerMean > 0f) "${String.format("%.1f", langReport.overallWerMean * 100)}%" else "N/A"
                    val sttRtf = if (langReport.overallSttRtfMean > 0) String.format("%.3f", langReport.overallSttRtfMean) else "N/A"
                    val ttsRtf = if (langReport.overallTtsRtfMean > 0) String.format("%.3f", langReport.overallTtsRtfMean) else "N/A"
                    val ttsGen = s?.let { "${String.format("%.0f", it.ttsGenerationMean)}" } ?: "N/A"
                    appendLine("| ${langReport.language.displayName} | Yes (TTS) | $wer | $sttRtf | $ttsRtf | $ttsGen |")
                }
            }
            appendLine()

            // H. Failures/Errors
            appendLine("## H. Failures and Errors")
            appendLine()
            val failedRuns = report.languageReports.flatMap { it.summaries }.filter { it.failedRuns > 0 }
            if (failedRuns.isNotEmpty()) {
                appendLine("| Test Case | Failed Runs | Total Runs |")
                appendLine("|-----------|-------------|------------|")
                failedRuns.forEach { s ->
                    appendLine("| ${s.testId} | ${s.failedRuns} | ${s.runCount} |")
                }
            } else {
                appendLine("No failures recorded.")
            }
            appendLine()

            // I. Methodology and Limitations
            appendLine("## I. Methodology and Limitations")
            appendLine()
            appendLine("### Methodology")
            appendLine()
            appendLine(report.methodology)
            appendLine()
            appendLine("### Limitations")
            appendLine()
            report.limitations.forEach { appendLine("- $it") }
            appendLine()

            // J. Benchmark Conditions
            appendLine("## J. Benchmark Conditions")
            appendLine()
            if (report.deviceInfo.model.isNotEmpty()) {
                appendLine("- Device: ${report.deviceInfo.manufacturer} ${report.deviceInfo.model}")
            }
            appendLine("- Android: ${report.deviceInfo.androidVersion}")
            appendLine("- CPU: ${report.deviceInfo.cpuArchitecture} (${report.deviceInfo.cpuCores} cores)")
            appendLine("- RAM: ${report.deviceInfo.totalRamMb} MB")
            appendLine("- Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(report.timestamp))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("*This report was generated by the iTantra Benchmark System.*")
        }

        file.writeText(md)
        Log.d(TAG, "Report generated at ${file.absolutePath}")
        return file
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
            else -> "$bytes bytes"
        }
    }
}
