package com.lifeledger.data.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses the date text a bank statement export puts in front of us, without knowing in advance
 * which of the handful of locale conventions the exporting bank used.
 *
 * The strategy is "lock onto the first format that works": the first successful parse in a
 * column pins [resolved] for every subsequent row in the same file, so an ambiguous date like
 * `01/02/2024` appearing later in the column is interpreted the same way as the unambiguous
 * dates earlier in it, rather than re-guessed row by row.
 */
internal class FlexibleDateParser(private val explicitPattern: String? = null) {

    private var resolved: Candidate? = null

    fun parse(raw: String): LocalDateTime? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        explicitPattern?.let { pattern ->
            val hasTime = pattern.contains('H') || pattern.contains('h')
            return runCatching { parseWith(text, Candidate(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH), hasTime)) }
                .getOrNull()
        }

        resolved?.let { return runCatching { parseWith(text, it) }.getOrNull() }

        for (candidate in CANDIDATES) {
            val result = runCatching { parseWith(text, candidate) }.getOrNull()
            if (result != null) {
                resolved = candidate
                return result
            }
        }
        return null
    }

    private fun parseWith(text: String, candidate: Candidate): LocalDateTime =
        if (candidate.hasTime) {
            LocalDateTime.parse(text, candidate.formatter)
        } else {
            LocalDate.parse(text, candidate.formatter).atStartOfDay()
        }

    private data class Candidate(val formatter: DateTimeFormatter, val hasTime: Boolean)

    companion object {
        private fun pattern(p: String, hasTime: Boolean) =
            Candidate(DateTimeFormatter.ofPattern(p, Locale.ENGLISH), hasTime)

        // Ordered by how often each shape shows up in Indian bank/UPI exports; the first match
        // wins so genuinely ambiguous strings (e.g. "01/02/2024") resolve the same way every time.
        private val CANDIDATES = listOf(
            pattern("dd/MM/yyyy HH:mm:ss", true),
            pattern("dd/MM/yyyy HH:mm", true),
            pattern("dd-MM-yyyy HH:mm:ss", true),
            pattern("yyyy-MM-dd HH:mm:ss", true),
            pattern("yyyy-MM-dd'T'HH:mm:ss", true),
            pattern("dd/MM/yyyy", false),
            pattern("dd-MM-yyyy", false),
            pattern("yyyy-MM-dd", false),
            pattern("yyyy/MM/dd", false),
            pattern("MM/dd/yyyy", false),
            pattern("dd MMM yyyy", false),
            pattern("dd-MMM-yyyy", false),
            pattern("dd MMM, yyyy", false),
            pattern("d MMM yyyy", false),
            pattern("MMM dd, yyyy", false),
        )
    }
}
