package com.itantra.app.ui.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.app.core.metrics.MetricsManager
import com.itantra.app.core.metrics.PerformanceMetrics
import com.itantra.app.core.metrics.PipelineStage
import com.itantra.app.core.metrics.WerCalculator

@Composable
fun DeveloperScreen() {
    val metrics by MetricsManager.metrics.collectAsState()
    var selectedReferencePhrase by remember { mutableStateOf("I need help") }
    var recognizedTextForWer by remember { mutableStateOf("") }

    val referencePhrases = listOf(
        "I need help",
        "There is a fire",
        "Send the rescue team",
        "Everyone is safe",
        "मुझे मदद चाहिए",
        "आग लग गई है",
        "बचाव दल को बुलाइए",
        "सभी लोग सुरक्षित हैं"
    )

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Developer Metrics") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PipelineStatusSection(metrics.pipelineStage)
            ConnectionSection(metrics)
            SttPerformanceSection(metrics)
            TransmissionSection(metrics)
            TtsSection(metrics)
            EndToEndSection(metrics)
            AccuracyTestSection(
                selectedReference = selectedReferencePhrase,
                onReferenceSelected = { selectedReferencePhrase = it },
                referencePhrases = referencePhrases,
                recognizedText = recognizedTextForWer,
                onRecognizedTextChanged = { recognizedTextForWer = it }
            )
        }
    }
}

@Composable
private fun PipelineStatusSection(currentStage: PipelineStage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PIPELINE STATUS", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PipelineStage.entries.forEach { stage ->
                    val (symbol, color) = when {
                        stage.ordinal < currentStage.ordinal -> "✓" to Color.Green
                        stage == currentStage -> "●" to Color.Blue
                        else -> "○" to Color.Gray
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(symbol, color = color, fontWeight = FontWeight.Bold)
                        Text(stage.name, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionSection(metrics: PerformanceMetrics) {
    MetricCard("CONNECTION") {
        MetricRow("Messages Sent", metrics.messagesSent.toString())
        MetricRow("Messages Received", metrics.messagesReceived.toString())
        MetricRow("Current Msg ID", metrics.currentMessageId ?: "None")
    }
}

@Composable
private fun SttPerformanceSection(metrics: PerformanceMetrics) {
    MetricCard("STT PERFORMANCE") {
        MetricRow("Model Load Time", metrics.sttModelLoadTimeMs.toLatencyString())
        MetricRow("Recognition Latency", metrics.sttRecognitionLatencyMs.toLatencyString())
    }
}

@Composable
private fun TransmissionSection(metrics: PerformanceMetrics) {
    MetricCard("TRANSMISSION") {
        MetricRow("Message Size", metrics.lastMessageSizeBytes?.let { "$it bytes" } ?: "Not measured")
        MetricRow("Encoding Latency", metrics.encodingLatencyMs.toLatencyString())
        MetricRow("Transmission Latency", metrics.transmissionLatencyMs.toLatencyString())
    }
}

@Composable
private fun TtsSection(metrics: PerformanceMetrics) {
    MetricCard("TTS") {
        MetricRow("TTS Start Latency", metrics.ttsStartLatencyMs.toLatencyString())
    }
}

@Composable
private fun EndToEndSection(metrics: PerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("END-TO-END LATENCY", style = MaterialTheme.typography.labelLarge)
            Text(
                text = metrics.endToEndLatencyMs.toLatencyString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccuracyTestSection(
    selectedReference: String,
    onReferenceSelected: (String) -> Unit,
    referencePhrases: List<String>,
    recognizedText: String,
    onRecognizedTextChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    MetricCard("STT ACCURACY TEST (WER)") {
        Text("Reference Phrase:", style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selectedReference)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                referencePhrases.forEach { phrase ->
                    DropdownMenuItem(
                        text = { Text(phrase) },
                        onClick = {
                            onReferenceSelected(phrase)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = recognizedText,
            onValueChange = onRecognizedTextChanged,
            label = { Text("Recognized Text") },
            modifier = Modifier.fillMaxWidth()
        )

        val wer = if (recognizedText.isNotEmpty()) {
            WerCalculator.calculate(selectedReference, recognizedText)
        } else {
            null
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Word Error Rate (WER): ", fontWeight = FontWeight.Bold)
            Text(wer?.let { "%.1f%%".format(it * 100) } ?: "N/A")
        }
    }
}

@Composable
private fun MetricCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun Long?.toLatencyString(): String = this?.let { "$it ms" } ?: "Not measured"
