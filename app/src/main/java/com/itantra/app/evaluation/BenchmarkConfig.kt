package com.itantra.app.evaluation

import com.itantra.app.core.model.Language

/**
 * Configuration for a benchmark run.
 */
data class BenchmarkConfig(
    val language: Language,
    val runs: Int = 30,
    val mode: BenchmarkMode = BenchmarkMode.WARM,
    val includeStt: Boolean = true,
    val includeTts: Boolean = true,
    val includeTransmission: Boolean = true,
    val includeResourceMonitoring: Boolean = true,
    val testCorpusIds: List<String>? = null, // null = all
    val notes: String = ""
)

enum class BenchmarkMode(val displayName: String) {
    COLD_START("Cold Start - models loaded fresh"),
    WARM("Warm - models already in memory"),
    IDLE_LISTENING("Idle Listening - VAD active, no speech"),
    CONTINUOUS_SPEECH("Continuous Speech - sustained input"),
    SHORT_SENTENCE("Short Sentence - < 5 words"),
    LONG_SENTENCE("Long Sentence - > 10 words")
}
