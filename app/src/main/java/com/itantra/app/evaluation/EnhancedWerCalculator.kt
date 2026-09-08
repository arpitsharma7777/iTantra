package com.itantra.app.evaluation

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Enhanced Word Error Rate calculator with language-aware normalization.
 *
 * Normalization is configurable and language-aware:
 * - Unicode NFC normalization (standard for Devanagari, Bengali, etc.)
 * - Whitespace normalization
 * - Punctuation handling (configurable)
 * - Case normalization for Latin scripts only
 * - Numeral handling (configurable)
 *
 * The normalization is conservative: it does NOT aggressively strip characters
 * that could represent actual recognition errors.
 */
object EnhancedWerCalculator {

    data class WerResult(
        val wer: Float,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val referenceWordCount: Int,
        val normalizedReference: String,
        val normalizedRecognized: String
    )

    data class NormalizationConfig(
        val unicodeNormalize: Boolean = true,
        val lowercaseLatinOnly: Boolean = true,
        val normalizeWhitespace: Boolean = true,
        val stripPunctuation: Boolean = true,
        val handleNumerals: NumeralHandling = NumeralHandling.KEEP
    )

    enum class NumeralHandling {
        KEEP,        // Keep numerals as-is
        WORDS,       // Convert digits to word equivalents (0-9 only)
        STRIP        // Remove all numerals
    }

    private val defaultConfig = NormalizationConfig()

    /**
     * Normalizes text for WER calculation.
     * Language-aware: only lowercases Latin scripts.
     */
    fun normalize(text: String, config: NormalizationConfig = defaultConfig): String {
        var result = text

        if (config.unicodeNormalize) {
            result = Normalizer.normalize(result, Normalizer.Form.NFC)
        }

        if (config.normalizeWhitespace) {
            result = result.replace(Regex("\\s+"), " ").trim()
        }

        if (config.stripPunctuation) {
            // Remove common punctuation but keep script-specific characters
            result = result.replace(Regex("[,\\.\\?!;:\"'\\-\\(\\)\\[\\]{}/]"), "")
        }

        if (config.handleNumerals == NumeralHandling.STRIP) {
            result = result.replace(Regex("\\d+"), "")
        }

        if (config.lowercaseLatinOnly && isLatinScript(result)) {
            result = result.lowercase(Locale.ROOT)
        }

        return result.trim()
    }

    /**
     * Tokenizes text into words using locale-aware word boundaries.
     */
    fun tokenize(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()

        val words = mutableListOf<String>()
        val breakIterator = BreakIterator.getWordInstance(Locale.ROOT)
        breakIterator.setText(normalized)

        var start = breakIterator.first()
        var end = breakIterator.next()
        while (end != BreakIterator.DONE) {
            val word = normalized.substring(start, end).trim()
            if (word.isNotEmpty() && word.any { it.isLetterOrDigit() }) {
                words.add(word)
            }
            start = end
            end = breakIterator.next()
        }
        return words
    }

    /**
     * Calculates WER between reference and recognized text.
     */
    fun calculate(
        reference: String,
        recognized: String,
        config: NormalizationConfig = defaultConfig
    ): WerResult {
        val normRef = normalize(reference, config)
        val normRec = normalize(recognized, config)

        val refWords = tokenize(normRef)
        val recWords = tokenize(normRec)

        if (refWords.isEmpty() && recWords.isEmpty()) {
            return WerResult(0f, 0, 0, 0, 0, normRef, normRec)
        }
        if (refWords.isEmpty()) {
            return WerResult(1.0f, 0, 0, recWords.size, 0, normRef, normRec)
        }
        if (recWords.isEmpty()) {
            return WerResult(1.0f, 0, refWords.size, 0, refWords.size, normRef, normRec)
        }

        val n = refWords.size
        val m = recWords.size

        // Levenshtein DP table
        val dp = Array(n + 1) { IntArray(m + 1) }
        // Backtracking for edit operations
        val ops = Array(n + 1) { IntArray(m + 1) } // 0=match, 1=sub, 2=del, 3=ins

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                if (refWords[i - 1] == recWords[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                    ops[i][j] = 0 // match
                } else {
                    val substitution = dp[i - 1][j - 1] + 1
                    val deletion = dp[i - 1][j] + 1
                    val insertion = dp[i][j - 1] + 1
                    val minOp = min(substitution, min(deletion, insertion))
                    dp[i][j] = minOp
                    ops[i][j] = when (minOp) {
                        substitution -> 1 // substitution
                        deletion -> 2    // deletion
                        else -> 3        // insertion
                    }
                }
            }
        }

        // Backtrack to count operations
        var subs = 0
        var dels = 0
        var ins = 0
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && ops[i][j] == 0 -> { i--; j-- } // match
                i > 0 && j > 0 && ops[i][j] == 1 -> { subs++; i--; j-- } // substitution
                i > 0 && ops[i][j] == 2 -> { dels++; i-- } // deletion
                j > 0 && ops[i][j] == 3 -> { ins++; j-- } // insertion
                i > 0 -> { dels++; i-- }
                j > 0 -> { ins++; j-- }
                else -> break
            }
        }

        val wer = dp[n][m].toFloat() / n

        return WerResult(
            wer = wer,
            substitutions = subs,
            deletions = dels,
            insertions = ins,
            referenceWordCount = n,
            normalizedReference = normRef,
            normalizedRecognized = normRec
        )
    }

    /**
     * Calculates corpus-level WER (total edits / total reference words).
     */
    fun calculateCorpusWer(results: List<WerResult>): Float {
        if (results.isEmpty()) return 0f
        val totalEdits = results.sumOf { it.substitutions + it.deletions + it.insertions }
        val totalRefWords = results.sumOf { it.referenceWordCount }
        if (totalRefWords == 0) return 0f
        return totalEdits.toFloat() / totalRefWords
    }

    private fun isLatinScript(text: String): Boolean {
        return text.any { it in 'A'..'Z' || it in 'a'..'z' }
    }
}
