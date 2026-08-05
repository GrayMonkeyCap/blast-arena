package com.lifeledger.core.common.text

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * String similarity used by merchant resolution and duplicate detection.
 *
 * Jaro–Winkler is the workhorse here rather than plain edit distance: merchant strings
 * differ mostly by suffixes and noise tokens (`SWIGGY` vs `SWIGGY LTD BANGALORE`), and
 * Jaro–Winkler's common-prefix bonus matches that shape well while staying O(n·m) on
 * strings that are always short.
 */
object TextSimilarity {

    /** Jaro–Winkler similarity in 0..1. */
    fun jaroWinkler(a: String, b: String, prefixScale: Double = 0.1): Double {
        val jaro = jaro(a, b)
        if (jaro < 0.7) return jaro
        val prefix = a.commonPrefixWith(b).length.coerceAtMost(4)
        return jaro + prefix * prefixScale * (1 - jaro)
    }

    fun jaro(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val matchWindow = max(a.length, b.length) / 2 - 1
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)

        var matches = 0
        for (i in a.indices) {
            val start = max(0, i - matchWindow)
            val end = min(i + matchWindow + 1, b.length)
            for (j in start until end) {
                if (bMatched[j] || a[i] != b[j]) continue
                aMatched[i] = true
                bMatched[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
    }

    /** Classic Levenshtein distance, kept for exactness-sensitive checks like reference ids. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Levenshtein expressed as a 0..1 similarity. */
    fun levenshteinRatio(a: String, b: String): Double {
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    /**
     * Token-set similarity: order-insensitive overlap of word sets.
     * Good for `PAYTM SWIGGY ORDER` vs `SWIGGY PAYTM`.
     */
    fun tokenSetRatio(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() && tb.isEmpty()) return 1.0
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.intersect(tb).size
        val union = ta.union(tb).size
        return intersection.toDouble() / union
    }

    /**
     * Blended score used by the merchant resolver — the max of a character-level and a
     * token-level view, so both `SWIGY` (typo) and `LTD SWIGGY` (noise) score highly.
     */
    fun merchantSimilarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        if (na.contains(nb) || nb.contains(na)) {
            val shortest = min(na.length, nb.length).toDouble()
            val longest = max(na.length, nb.length).toDouble()
            // Containment is strong evidence, but not when the shared part is trivially short.
            if (shortest >= 4) return 0.9 + 0.1 * (shortest / longest)
        }
        return max(jaroWinkler(na, nb), tokenSetRatio(na, nb))
    }

    fun normalize(raw: String): String = raw
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() && it !in NOISE_TOKENS }
        .joinToString(" ")

    fun tokens(raw: String): Set<String> = normalize(raw).split(' ').filter { it.isNotBlank() }.toSet()

    /** True when two amounts are within [toleranceMinor] of each other. */
    fun amountsClose(a: Long, b: Long, toleranceMinor: Long = 0): Boolean =
        abs(a - b) <= toleranceMinor

    /** Corporate suffixes and payment-network noise that carry no identifying signal. */
    private val NOISE_TOKENS = setOf(
        "ltd", "limited", "pvt", "private", "llp", "inc", "co", "corp", "company",
        "india", "in", "ind", "the", "and", "of", "services", "service", "solutions",
        "technologies", "technology", "tech", "retail", "store", "stores", "online",
        "payments", "payment", "paytm", "razorpay", "bharatpe", "billdesk", "ccavenue",
        "pay", "upi", "vpa", "txn", "ref", "no", "id",
    )
}
