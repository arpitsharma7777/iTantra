package com.itantra.app.core.metrics

import java.util.Locale
import kotlin.math.min

/**
 * Utility to calculate Word Error Rate (WER) using Levenshtein distance at the word level.
 *
 * Examples:
 * - reference "I need help", recognized "I need help" -> WER 0.0
 * - reference "I need emergency help", recognized "I need help" -> WER 0.25 (1 deletion / 4 words)
 */
object WerCalculator {

    /**
     * Calculates the Word Error Rate (WER) between a reference string and a recognized string.
     * WER = (Substitutions + Deletions + Insertions) / Number of words in reference.
     *
     * @param reference The ground truth sentence.
     * @param recognized The text produced by the STT engine.
     * @return The WER as a Float. Returns 0.0 for two empty strings.
     * If the reference is empty but recognized is not, returns the count of recognized words
     * capped at 1.0f to represent a 100% error state while avoiding division by zero.
     */
    fun calculate(reference: String, recognized: String): Float {
        val refWords = tokenize(reference)
        val recWords = tokenize(recognized)

        if (refWords.isEmpty()) {
            return if (recWords.isEmpty()) 0f else 1.0f
        }
        if (recWords.isEmpty()) {
            return 1.0f
        }

        val n = refWords.size
        val m = recWords.size

        // dp[i][j] will be the edit distance between ref[0..i-1] and rec[0..j-1]
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                if (refWords[i - 1] == recWords[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val substitution = dp[i - 1][j - 1] + 1
                    val deletion = dp[i - 1][j] + 1
                    val insertion = dp[i][j - 1] + 1
                    dp[i][j] = min(substitution, min(deletion, insertion))
                }
            }
        }

        return dp[n][m].toFloat() / n
    }

    private fun tokenize(text: String): List<String> {
        return text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { word ->
                // Normalizing case for Latin characters.
                // Kotlin's lowercase() is safe for Devanagari as it has no case.
                word.lowercase(Locale.ROOT)
            }
    }
}
