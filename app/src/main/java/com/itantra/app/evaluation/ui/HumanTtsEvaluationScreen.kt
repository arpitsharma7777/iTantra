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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.core.model.Language
import com.itantra.app.evaluation.HumanTtsScores
import com.itantra.app.evaluation.TestCorpus

/**
 * Screen for human evaluation of TTS quality.
 *
 * Displays each test case text and provides sliders for listeners to score:
 * 1. Intelligibility - Can you understand what is being said?
 * 2. Naturalness - Does it sound like a human voice?
 * 3. Pronunciation - Are words pronounced correctly?
 * 4. Flow - Does the speech flow naturally without awkward pauses?
 *
 * Scores are 1-5:
 * 1 = Very Poor
 * 2 = Poor
 * 3 = Acceptable
 * 4 = Good
 * 5 = Excellent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanTtsEvaluationScreen(
    language: Language,
    onScoresSubmitted: (List<HumanTtsScores>) -> Unit,
    onBack: () -> Unit
) {
    val testCases = remember { TestCorpus.getForLanguage(language) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var intelligibility by remember { mutableIntStateOf(3) }
    var naturalness by remember { mutableIntStateOf(3) }
    var pronunciation by remember { mutableIntStateOf(3) }
    var flow by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }
    val allScores = remember { mutableListOf<Pair<String, HumanTtsScores>>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Evaluation - ${language.displayName}") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            if (testCases.isEmpty()) {
                Text("No test cases for ${language.displayName}")
                return@Column
            }

            if (currentIndex >= testCases.size) {
                // All done
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Evaluation Complete!", style = MaterialTheme.typography.titleMedium)
                        Text("${allScores.size} samples evaluated")
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            onScoresSubmitted(allScores.map { it.second })
                        }) {
                            Text("Save Results")
                        }
                    }
                }
                return@Column
            }

            val testCase = testCases[currentIndex]

            // Progress
            Text(
                "Sample ${currentIndex + 1} of ${testCases.size}",
                style = MaterialTheme.typography.labelMedium
            )

            // Reference text
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reference Text:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        testCase.referenceText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Scoring sliders
            Text("Rate the TTS output:", style = MaterialTheme.typography.titleSmall)

            ScoreSlider("Intelligibility", "Can you understand what is being said?", intelligibility) {
                intelligibility = it
            }
            ScoreSlider("Naturalness", "Does it sound like a human voice?", naturalness) {
                naturalness = it
            }
            ScoreSlider("Pronunciation", "Are words pronounced correctly?", pronunciation) {
                pronunciation = it
            }
            ScoreSlider("Flow", "Does the speech flow naturally?", flow) {
                flow = it
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            // Restore previous scores
                            val prev = allScores.getOrNull(currentIndex)?.second
                            if (prev != null) {
                                intelligibility = prev.intelligibility
                                naturalness = prev.naturalness
                                pronunciation = prev.pronunciation
                                flow = prev.flow
                                notes = prev.notes
                            }
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Text("Previous")
                }

                TextButton(
                    onClick = {
                        // Save current scores
                        if (currentIndex < allScores.size) {
                            allScores[currentIndex] = testCase.id to HumanTtsScores(
                                intelligibility = intelligibility,
                                naturalness = naturalness,
                                pronunciation = pronunciation,
                                flow = flow,
                                notes = notes
                            )
                        } else {
                            allScores.add(testCase.id to HumanTtsScores(
                                intelligibility = intelligibility,
                                naturalness = naturalness,
                                pronunciation = pronunciation,
                                flow = flow,
                                notes = notes
                            ))
                        }
                        currentIndex++
                        // Reset for next
                        intelligibility = 3
                        naturalness = 3
                        pronunciation = 3
                        flow = 3
                        notes = ""
                    }
                ) {
                    Text(if (currentIndex < testCases.size - 1) "Next" else "Finish")
                }
            }
        }
    }
}

@Composable
private fun ScoreSlider(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "$value/5",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (value) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> MaterialTheme.colorScheme.error
                        3 -> MaterialTheme.colorScheme.onSurfaceVariant
                        4 -> MaterialTheme.colorScheme.primary
                        5 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Very Poor", style = MaterialTheme.typography.labelSmall)
                Text("Poor", style = MaterialTheme.typography.labelSmall)
                Text("Acceptable", style = MaterialTheme.typography.labelSmall)
                Text("Good", style = MaterialTheme.typography.labelSmall)
                Text("Excellent", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
