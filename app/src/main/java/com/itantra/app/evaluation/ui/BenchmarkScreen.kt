package com.itantra.app.evaluation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.Language
import com.itantra.app.evaluation.BenchmarkConfig
import com.itantra.app.evaluation.BenchmarkMode
import com.itantra.app.evaluation.BenchmarkReport
import com.itantra.app.evaluation.BenchmarkReportGenerator
import com.itantra.app.evaluation.BenchmarkResult
import com.itantra.app.evaluation.BenchmarkRunner
import com.itantra.app.evaluation.BenchmarkExporter
import com.itantra.app.evaluation.TestCorpus
import kotlinx.coroutines.launch

/**
 * Main benchmark screen for running and viewing benchmark results.
 *
 * Provides:
 * - Language selection
 * - Benchmark mode selection
 * - Number of runs configuration
 * - Start/stop benchmark
 * - Progress display
 * - Results summary
 * - Export options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    benchmarkRunner: BenchmarkRunner? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedLanguage by remember { mutableStateOf(Language.ENGLISH) }
    var selectedMode by remember { mutableStateOf(BenchmarkMode.WARM) }
    var numberOfRuns by remember { mutableFloatStateOf(30f) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var lastResults by remember { mutableStateOf<List<BenchmarkResult>>(emptyList()) }
    var lastReport by remember { mutableStateOf<BenchmarkReport?>(null) }
    var showResults by remember { mutableStateOf(false) }

    var languageExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benchmark Runner") },
                navigationIcon = {
                    androidx.compose.material3.TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Configuration
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configuration", style = MaterialTheme.typography.titleMedium)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Language selector
                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedLanguage.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false }
                        ) {
                            Language.entries.forEach { lang ->
                                val testCount = TestCorpus.getForLanguage(lang).size
                                DropdownMenuItem(
                                    text = { Text("${lang.displayName} ($testCount tests)") },
                                    onClick = {
                                        selectedLanguage = lang
                                        languageExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode selector
                    ExposedDropdownMenuBox(
                        expanded = modeExpanded,
                        onExpandedChange = { modeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Benchmark Mode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            BenchmarkMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName) },
                                    onClick = {
                                        selectedMode = mode
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Number of runs
                    Text("Runs: ${numberOfRuns.toInt()}", fontWeight = FontWeight.Bold)
                    Slider(
                        value = numberOfRuns,
                        onValueChange = { numberOfRuns = it },
                        valueRange = 5f..50f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5", style = MaterialTheme.typography.labelSmall)
                        Text("50", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Run controls
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Run", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isRunning) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isRunning = true
                                progress = 0f
                                statusMessage = "Starting..."
                                scope.launch {
                                    try {
                                        val config = BenchmarkConfig(
                                            language = selectedLanguage,
                                            runs = numberOfRuns.toInt(),
                                            mode = selectedMode
                                        )
                                        val resultBundle = benchmarkRunner?.run(config) { p, msg ->
                                            progress = p
                                            statusMessage = msg
                                        }
                                        if (resultBundle != null) {
                                            lastResults = resultBundle.results
                                            lastReport = resultBundle.report
                                            showResults = true
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Error: ${e.message}"
                                    } finally {
                                        isRunning = false
                                    }
                                }
                            },
                            enabled = !isRunning && benchmarkRunner != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Benchmark")
                        }

                        if (showResults && lastReport != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val generator = BenchmarkReportGenerator(context)
                                        generator.generate(lastReport!!)
                                        statusMessage = "Report saved to benchmarks/"
                                    }
                                },
                                enabled = !isRunning
                            ) {
                                Text("Export Report")
                            }
                        }
                    }
                }
            }

            // Results summary
            if (showResults && lastResults.isNotEmpty()) {
                ResultsSummarySection(lastResults, lastReport)
            }
        }
    }
}

@Composable
private fun ResultsSummarySection(
    results: List<BenchmarkResult>,
    report: BenchmarkReport?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Results Summary", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Total runs: ${results.size}")
            Text("Passed: ${results.count { it.errors.isEmpty() }}")
            Text("Failed: ${results.count { it.errors.isNotEmpty() }}")

            report?.let { r ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("Model Sizes:", fontWeight = FontWeight.Bold)
                Text("  VAD: ${String.format("%.2f", r.modelSizes.vadModelSizeMb)} MB")
                r.modelSizes.ttsModelSizesMb.forEach { (name, size) ->
                    Text("  TTS ($name): ${String.format("%.2f", size)} MB")
                }
                Text("  Total: ${String.format("%.2f", r.modelSizes.totalMlAssetSizeMb)} MB")

                if (r.languageReports.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Language Reports:", fontWeight = FontWeight.Bold)
                    r.languageReports.forEach { langReport ->
                        val s = langReport.summaries.firstOrNull()
                        Text(
                            "  ${langReport.language.displayName}: " +
                                    "TTS RTF=${String.format("%.3f", langReport.overallTtsRtfMean)}, " +
                                    "TTS Gen=${s?.let { "${String.format("%.0f", it.ttsGenerationMean)}ms" } ?: "N/A"}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Results saved to app internal storage under 'benchmarks/' directory.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
