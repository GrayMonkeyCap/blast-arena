package com.lifeledger.core.model

import kotlin.math.abs

/**
 * An exact monetary amount, stored in the currency's *minor* units (paise for INR).
 *
 * Floating point is never used for money anywhere in Life Ledger: every amount that
 * enters the system through the SMS parser is converted to minor units at the edge and
 * stays a [Long] until it is formatted for display.
 */
data class Money(
    val minor: Long,
    val currency: String = INR,
) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0
    val isNegative: Boolean get() = minor < 0

    /** Major-unit value, for charting and ratio maths only — never for arithmetic on money. */
    val asDouble: Double get() = minor / 100.0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minor = minor + other.minor)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minor = minor - other.minor)
    }

    operator fun times(factor: Int): Money = copy(minor = minor * factor)

    operator fun unaryMinus(): Money = copy(minor = -minor)

    fun abs(): Money = copy(minor = abs(minor))

    /** Ratio of this amount to [other], or `null` when [other] is zero. */
    fun ratioTo(other: Money): Double? {
        requireSameCurrency(other)
        return if (other.minor == 0L) null else minor.toDouble() / other.minor.toDouble()
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}; convert explicitly first"
        }
    }

    companion object {
        const val INR = "INR"

        val ZERO = Money(0, INR)

        fun zero(currency: String = INR) = Money(0, currency)

        fun ofMajor(major: Long, currency: String = INR) = Money(major * 100, currency)

        /**
         * Parses an amount as it appears in an SMS — `1,23,456.78`, `1234`, `12.5`, `1.2L` —
         * into minor units. Returns `null` when the text is not a usable amount.
         *
         * Handles Indian lakh/crore suffixes because banks do use them in alert SMS.
         */
        fun parse(raw: String, currency: String = INR): Money? {
            val cleaned = raw.trim()
                .removePrefix("₹").removePrefix("Rs.").removePrefix("Rs").removePrefix("INR")
                .trim()
                .replace(",", "")
                .replace(" ", "")
            if (cleaned.isEmpty()) return null

            val multiplier: Long
            val numeric: String
            when {
                cleaned.endsWith("Cr", ignoreCase = true) -> {
                    multiplier = 10_000_000L; numeric = cleaned.dropLast(2)
                }
                cleaned.endsWith("L", ignoreCase = true) -> {
                    multiplier = 100_000L; numeric = cleaned.dropLast(1)
                }
                cleaned.endsWith("K", ignoreCase = true) -> {
                    multiplier = 1_000L; numeric = cleaned.dropLast(1)
                }
                else -> {
                    multiplier = 1L; numeric = cleaned
                }
            }
            if (numeric.isEmpty() || numeric.none { it.isDigit() }) return null
            if (numeric.any { !it.isDigit() && it != '.' && it != '-' }) return null

            val dotCount = numeric.count { it == '.' }
            if (dotCount > 1) return null

            val negative = numeric.startsWith("-")
            val unsigned = numeric.removePrefix("-")

            val wholePart: String
            val fractionPart: String
            if (dotCount == 1) {
                val parts = unsigned.split(".")
                wholePart = parts[0].ifEmpty { "0" }
                fractionPart = parts[1].take(2).padEnd(2, '0')
            } else {
                wholePart = unsigned
                fractionPart = "00"
            }

            val whole = wholePart.toLongOrNull() ?: return null
            val fraction = fractionPart.toLongOrNull() ?: return null
            val minor = (whole * 100 + fraction) * multiplier
            return Money(if (negative) -minor else minor, currency)
        }
    }
}

fun Iterable<Money>.sumOrZero(currency: String = Money.INR): Money =
    fold(Money.zero(currency)) { acc, money -> acc + money }
