package com.lifeledger.sms.lex

import com.lifeledger.core.model.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The shared extraction toolkit every parser is built from.
 *
 * Indian bank SMS is a small, weird dialect: amounts appear as `Rs.1,234.00`, `INR 1234`,
 * `₹1,23,456.78`; accounts as `A/c XX4521`, `ac no. **1234`, `card ending 5678`; references
 * as `UPI Ref 412345678901`, `txn# ABC123`. Centralising those shapes here means a parser
 * is mostly a few keyword checks plus calls into this object, and a fix to a pattern
 * benefits every bank at once.
 *
 * All patterns are compiled once, are case-insensitive, and are anchored loosely enough to
 * survive the formatting drift banks introduce over time.
 */
object SmsPatterns {

    // ---------------------------------------------------------------- amounts

    private val AMOUNT = Regex(
        """(?:rs\.?|inr|₹|mrp)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Amount written with the currency word *after* the number: `1,234.00 INR`, `500 Rs`. */
    private val AMOUNT_TRAILING = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|₹)""",
        RegexOption.IGNORE_CASE,
    )

    private val BALANCE = Regex(
        """(?:avl(?:\.|able)?\s*(?:bal|balance)|a/?c\s*bal(?:ance)?|bal(?:ance)?|available\s*balance|clear\s*bal)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Every amount in the message, in order of appearance, de-duplicated by position.
     * Parsers use this when a message contains both a transacted amount and a balance.
     */
    fun amounts(body: String, currency: String = Money.INR): List<Money> {
        val found = mutableListOf<Pair<Int, Money>>()
        AMOUNT.findAll(body).forEach { m ->
            Money.parse(m.groupValues[1], currency)?.let { found += m.range.first to it }
        }
        AMOUNT_TRAILING.findAll(body).forEach { m ->
            Money.parse(m.groupValues[1], currency)?.let { found += m.range.first to it }
        }
        return found.distinctBy { it.first }.sortedBy { it.first }.map { it.second }
    }

    /**
     * The transacted amount: the first amount in the message that is not the balance.
     *
     * Banks put the transacted figure first in the overwhelming majority of alert formats,
     * and the balance behind an "Avl Bal" style label — so excluding the labelled balance
     * and taking the first remainder is both simple and accurate.
     */
    fun primaryAmount(body: String, currency: String = Money.INR): Money? {
        val balance = balance(body, currency)
        val all = amounts(body, currency)
        if (all.isEmpty()) return null
        return all.firstOrNull { it != balance } ?: all.first()
    }

    fun balance(body: String, currency: String = Money.INR): Money? =
        BALANCE.find(body)?.groupValues?.get(1)?.let { Money.parse(it, currency) }

    // --------------------------------------------------------------- accounts

    private val ACCOUNT = Regex(
        """(?:a/?c(?:count)?(?:\s*no\.?)?|acct)\s*(?:no\.?|number|#)?\s*[:\-]?\s*(?:x+|\*+|X+)?\s*(\d{3,6})""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT_MASKED = Regex(
        """(?:x{2,}|\*{2,})\s*(\d{3,6})""",
        RegexOption.IGNORE_CASE,
    )

    private val CARD = Regex(
        """(?:card|cc|dc)\s*(?:no\.?|number|ending(?:\s*(?:in|with))?|xx)?\s*[:\-]?\s*(?:x+|\*+|X+)?\s*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    /** Masked account tail, normalised to `XX1234`. Returns null when the message has none. */
    fun maskedAccount(body: String): String? {
        val digits = ACCOUNT.find(body)?.groupValues?.get(1)
            ?: ACCOUNT_MASKED.find(body)?.groupValues?.get(1)
            ?: return null
        return "XX$digits"
    }

    /** Masked card tail, normalised to `XX1234`. */
    fun maskedCard(body: String): String? =
        CARD.find(body)?.groupValues?.get(1)?.let { "XX$it" }

    // ------------------------------------------------------------- references

    private val UPI_REF = Regex(
        """(?:upi\s*(?:ref(?:erence)?|rrn)?\s*(?:no\.?|id)?|rrn)\s*[:\-#]?\s*(\d{9,18})""",
        RegexOption.IGNORE_CASE,
    )

    private val GENERIC_REF = Regex(
        """(?:ref(?:erence)?|txn|transaction|trn|utr|imps|neft|rtgs)\s*(?:no\.?|id|#)?\s*[:\-]?\s*([A-Z0-9]{6,25})""",
        RegexOption.IGNORE_CASE,
    )

    private val INFO_REF = Regex(
        """\binfo[:\s\-]+([A-Z0-9/\-*.]{4,40})""",
        RegexOption.IGNORE_CASE,
    )

    fun referenceNumber(body: String): String? =
        UPI_REF.find(body)?.groupValues?.get(1)
            ?: GENERIC_REF.find(body)?.groupValues?.get(1)?.takeIf { it.any(Char::isDigit) }

    fun infoField(body: String): String? = INFO_REF.find(body)?.groupValues?.get(1)?.trim()

    private val UPI_ID = Regex("""\b([a-zA-Z0-9._\-]{2,64}@[a-zA-Z]{2,20})\b""")

    /** VPA such as `someone@okhdfcbank`. Deliberately excludes email-looking strings. */
    fun upiId(body: String): String? = UPI_ID.find(body)?.groupValues?.get(1)
        ?.takeIf { candidate -> EMAIL_TLDS.none { candidate.endsWith(it, ignoreCase = true) } }

    private val EMAIL_TLDS = listOf(".com", ".in", ".org", ".net", ".co", ".io")

    // ------------------------------------------------------------ date & time

    private val DATE_PATTERNS = listOf(
        // 05-08-26, 05/08/2026, 05.08.2026
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""") to DateShape.DMY_NUMERIC,
        // 05-Aug-26, 05 Aug 2026, 05Aug26
        Regex("""\b(\d{1,2})[-\s]?([A-Za-z]{3})[-\s]?(\d{2,4})\b""") to DateShape.DMY_NAMED,
        // Aug 05, 2026
        Regex("""\b([A-Za-z]{3})[-\s](\d{1,2})[,\s]+(\d{2,4})\b""") to DateShape.MDY_NAMED,
        // 2026-08-05
        Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""") to DateShape.YMD_NUMERIC,
    )

    private val TIME = Regex(
        """\b(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(am|pm|AM|PM|hrs)?\b""",
    )

    private enum class DateShape { DMY_NUMERIC, DMY_NAMED, MDY_NAMED, YMD_NUMERIC }

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    /**
     * Date stated inside the message body, if any.
     *
     * Bank alerts usually arrive within seconds of the event, so the SMS receipt time is a
     * good default — but statement-style and delayed messages (`credited on 01-Aug-26`)
     * carry the real date, and using it keeps the timeline honest.
     */
    fun dateIn(body: String, referenceYear: Int = LocalDate.now().year): LocalDate? {
        for ((regex, shape) in DATE_PATTERNS) {
            val m = regex.find(body) ?: continue
            val g = m.groupValues
            val date = runCatching {
                when (shape) {
                    DateShape.DMY_NUMERIC -> LocalDate.of(
                        expandYear(g[3].toInt(), referenceYear), g[2].toInt(), g[1].toInt(),
                    )
                    DateShape.DMY_NAMED -> MONTHS[g[2].lowercase()]?.let { month ->
                        LocalDate.of(expandYear(g[3].toInt(), referenceYear), month, g[1].toInt())
                    }
                    DateShape.MDY_NAMED -> MONTHS[g[1].lowercase()]?.let { month ->
                        LocalDate.of(expandYear(g[3].toInt(), referenceYear), month, g[2].toInt())
                    }
                    DateShape.YMD_NUMERIC -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                }
            }.getOrNull()
            if (date != null) return date
        }
        return null
    }

    fun timeIn(body: String): LocalTime? {
        val m = TIME.find(body) ?: return null
        val g = m.groupValues
        var hour = g[1].toIntOrNull() ?: return null
        val minute = g[2].toIntOrNull() ?: return null
        val second = g[3].toIntOrNull() ?: 0
        val meridiem = g[4].lowercase()
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return LocalTime.of(hour, minute, second)
    }

    /**
     * Best-effort instant for the event: the in-body date/time when present, otherwise the
     * time the SMS was received.
     */
    fun instantIn(body: String, fallback: Instant, zone: ZoneId): Instant {
        val date = dateIn(body, fallback.atZone(zone).year) ?: return fallback
        val time = timeIn(body) ?: fallback.atZone(zone).toLocalTime()
        return LocalDateTime.of(date, time).atZone(zone).toInstant()
    }

    private fun expandYear(value: Int, referenceYear: Int): Int = when {
        value >= 1000 -> value
        value >= 100 -> referenceYear
        else -> 2000 + value
    }

    @Suppress("unused")
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    // ---------------------------------------------------------------- merchant

    private val MERCHANT_PATTERNS = listOf(
        Regex("""\bto\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|\s+ref\b|\s+upi\b|\s+a/?c\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|\s+ref\b|\s+upi\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|\s+ref\b|\s+upi\b|\s+a/?c\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\btowards\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfor\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bVPA\s+([A-Za-z0-9._\-]{2,64}@[A-Za-z]{2,20})(?:\s*\(([^)]{2,45})\))?""", RegexOption.IGNORE_CASE),
    )

    /**
     * Raw merchant text as the bank wrote it.
     *
     * Returns the *unnormalised* string on purpose — turning `AMZN*MKTP IN` into `Amazon`
     * is the merchant resolver's job, and keeping the two separate means a resolver
     * improvement does not require re-parsing.
     */
    fun rawMerchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val m = pattern.find(body) ?: continue
            val candidate = (m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                ?: m.groupValues[1]).trim().trim('.', ',', '-', ' ')
            if (candidate.length < 2) continue
            if (isNoiseMerchant(candidate)) continue
            return candidate
        }
        return null
    }

    /** Words that follow "to"/"at" but never name a merchant. */
    private val MERCHANT_STOPWORDS = setOf(
        "your", "you", "the", "a", "an", "be", "avoid", "know", "check", "view", "block",
        "report", "call", "click", "download", "pay", "date", "avl", "bal", "balance",
        "account", "card", "bank", "customer", "care", "credit", "debit", "this", "that",
    )

    private fun isNoiseMerchant(candidate: String): Boolean {
        val first = candidate.substringBefore(' ').lowercase()
        if (first in MERCHANT_STOPWORDS) return true
        // A "merchant" made only of digits is a reference number the pattern over-matched.
        return candidate.all { !it.isLetter() }
    }
}
