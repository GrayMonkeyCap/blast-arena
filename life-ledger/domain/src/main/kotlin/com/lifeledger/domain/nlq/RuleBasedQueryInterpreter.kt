package com.lifeledger.domain.nlq

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shipped natural-language interpreter: deterministic, explainable, offline.
 *
 * It works because the question space is narrow. People ask about a *subject* (a merchant,
 * a category, a kind of transaction), over a *period*, wanting one of a handful of things
 * (a total, a list, the latest, a count, a comparison). Recognising those four slots covers
 * the overwhelming majority of real questions and — unlike a model — it can state exactly
 * what it understood, which is what makes the answer trustworthy.
 *
 * Every extraction records the span it consumed. Whatever is left over after all extractors
 * have run becomes [Interpretation.unresolvedTerms], so the UI can say "I ignored 'roughly'"
 * instead of silently answering a different question.
 */
@Singleton
class RuleBasedQueryInterpreter @Inject constructor() : QueryInterpreter {

    override val id: String = "rules.v1"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun interpret(question: String, context: InterpreterContext): Interpretation {
        val normalized = question.lowercase().trim()
        val consumed = mutableSetOf<String>()

        val intent = detectIntent(normalized, consumed)
        val range = detectPeriod(normalized, context.today, consumed)
        val category = detectCategory(normalized, consumed)
        val types = detectTypes(normalized, consumed)
        val direction = detectDirection(normalized, intent, types, consumed)
        val merchants = detectMerchants(normalized, context.knownMerchants, consumed)
        val amounts = detectAmountBounds(normalized, consumed)
        val freeText = residualText(normalized, consumed)

        val query = TransactionQuery(
            text = freeText.takeIf { it.isNotBlank() && merchants.isEmpty() && category == null },
            range = range,
            categories = setOfNotNull(category),
            types = types,
            merchantNames = merchants,
            direction = direction,
            minAmountMinor = amounts.first,
            maxAmountMinor = amounts.second,
            sort = if (intent == QueryIntent.LATEST) {
                TransactionQuery.Sort.DATE_DESC
            } else {
                TransactionQuery.Sort.DATE_DESC
            },
            limit = if (intent == QueryIntent.LATEST) 1 else null,
        )

        return Interpretation(
            query = query,
            intent = intent,
            confidence = scoreConfidence(intent, range, category, merchants, freeText),
            explanation = explain(intent, range, category, types, merchants, amounts, freeText),
            unresolvedTerms = residualTokens(normalized, consumed),
        )
    }

    // ------------------------------------------------------------------ intent

    private fun detectIntent(text: String, consumed: MutableSet<String>): QueryIntent {
        INTENT_PHRASES.forEach { (phrase, intent) ->
            if (text.contains(phrase)) {
                consumed += phrase
                return intent
            }
        }
        // "swiggy" on its own is a request to see them, not to total them.
        return QueryIntent.LIST
    }

    private val INTENT_PHRASES: List<Pair<String, QueryIntent>> = listOf(
        "how much did i spend" to QueryIntent.TOTAL,
        "how much have i spent" to QueryIntent.TOTAL,
        "how much did i invest" to QueryIntent.TOTAL,
        "how much did i earn" to QueryIntent.TOTAL,
        "how much" to QueryIntent.TOTAL,
        "total" to QueryIntent.TOTAL,
        "what did i spend" to QueryIntent.TOTAL,
        "when was my last" to QueryIntent.LATEST,
        "when did i last" to QueryIntent.LATEST,
        "when was the last" to QueryIntent.LATEST,
        "last time" to QueryIntent.LATEST,
        "most recent" to QueryIntent.LATEST,
        "how many times" to QueryIntent.COUNT,
        "how many" to QueryIntent.COUNT,
        "number of" to QueryIntent.COUNT,
        "breakdown" to QueryIntent.BREAKDOWN,
        "where did my money go" to QueryIntent.BREAKDOWN,
        "what did i spend most on" to QueryIntent.BREAKDOWN,
        "top " to QueryIntent.BREAKDOWN,
        "compared to" to QueryIntent.COMPARE,
        "vs " to QueryIntent.COMPARE,
        "versus" to QueryIntent.COMPARE,
        "more than last" to QueryIntent.COMPARE,
        "average" to QueryIntent.AVERAGE,
        "per day" to QueryIntent.AVERAGE,
        "show all" to QueryIntent.LIST,
        "show me" to QueryIntent.LIST,
        "list " to QueryIntent.LIST,
        "find " to QueryIntent.LIST,
    )

    // ------------------------------------------------------------------ period

    /**
     * Resolves the time window.
     *
     * Bare month names are read as the *most recent* occurrence of that month, not the one
     * in the current calendar year — asked in February, "March" almost always means last
     * March, and answering with an empty future window would be useless.
     */
    private fun detectPeriod(
        text: String,
        today: LocalDate,
        consumed: MutableSet<String>,
    ): DateRange? {
        fun claim(phrase: String, range: DateRange): DateRange {
            consumed += phrase
            return range
        }

        when {
            text.contains("today") -> return claim("today", DateRange.today(today))
            text.contains("yesterday") -> return claim(
                "yesterday",
                DateRange.today(today.minusDays(1)),
            )
            text.contains("this week") -> return claim("this week", DateRange.weekOf(today))
            text.contains("last week") -> return claim(
                "last week",
                DateRange.weekOf(today.minusWeeks(1)),
            )
            text.contains("this month") -> return claim("this month", DateRange.monthOf(today))
            text.contains("last month") -> return claim(
                "last month",
                DateRange.monthOf(today.minusMonths(1)),
            )
            text.contains("this quarter") -> return claim("this quarter", DateRange.quarterOf(today))
            text.contains("last quarter") -> return claim(
                "last quarter",
                DateRange.quarterOf(today.minusMonths(3)),
            )
            text.contains("this year") -> return claim("this year", DateRange.yearOf(today))
            text.contains("last year") -> return claim(
                "last year",
                DateRange.yearOf(today.minusYears(1)),
            )
            text.contains("financial year") || text.contains("fy") ->
                return claim("financial year", DateRange.financialYearOf(today))
            text.contains("all time") || text.contains("ever") -> return null
        }

        RELATIVE_WINDOW.find(text)?.let { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return@let
            val unit = match.groupValues[2]
            consumed += match.value
            val start = when {
                unit.startsWith("day") -> today.minusDays(count - 1L)
                unit.startsWith("week") -> today.minusWeeks(count.toLong())
                unit.startsWith("month") -> today.minusMonths(count.toLong())
                else -> today.minusYears(count.toLong())
            }
            return DateRange(start, today)
        }

        MONTHS.forEach { (name, month) ->
            if (!text.contains(name)) return@forEach
            consumed += name
            val year = if (month <= today.monthValue) today.year else today.year - 1
            val explicitYear = YEAR.find(text)?.groupValues?.get(1)?.toIntOrNull()
            explicitYear?.let { consumed += it.toString() }
            val anchor = LocalDate.of(explicitYear ?: year, month, 1)
            return DateRange(anchor, anchor.with(TemporalAdjusters.lastDayOfMonth()))
        }

        YEAR.find(text)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            if (year in 2000..2100) {
                consumed += match.value
                return DateRange.yearOf(LocalDate.of(year, 1, 1))
            }
        }

        return null
    }

    private val RELATIVE_WINDOW = Regex("""last\s+(\d{1,3})\s+(day|days|week|weeks|month|months|year|years)""")
    private val YEAR = Regex("""\b(20\d{2})\b""")

    private val MONTHS = listOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5,
        "june" to 6, "july" to 7, "august" to 8, "september" to 9, "october" to 10,
        "november" to 11, "december" to 12,
    )

    // ---------------------------------------------------------------- subjects

    private fun detectCategory(text: String, consumed: MutableSet<String>): TxnCategory? {
        CATEGORY_WORDS.forEach { (word, category) ->
            if (text.contains(word)) {
                consumed += word
                return category
            }
        }
        return null
    }

    private val CATEGORY_WORDS: List<Pair<String, TxnCategory>> = listOf(
        "coffee" to TxnCategory.FOOD,
        "food" to TxnCategory.FOOD,
        "eating out" to TxnCategory.FOOD,
        "restaurant" to TxnCategory.FOOD,
        "dining" to TxnCategory.FOOD,
        "grocer" to TxnCategory.GROCERIES,
        "shopping" to TxnCategory.SHOPPING,
        "travel" to TxnCategory.TRAVEL,
        "flight" to TxnCategory.TRAVEL,
        "hotel" to TxnCategory.TRAVEL,
        "train" to TxnCategory.TRAVEL,
        "cab" to TxnCategory.TRANSPORT,
        "transport" to TxnCategory.TRANSPORT,
        "fuel" to TxnCategory.FUEL,
        "petrol" to TxnCategory.FUEL,
        "health" to TxnCategory.HEALTHCARE,
        "hospital" to TxnCategory.HEALTHCARE,
        "doctor" to TxnCategory.HEALTHCARE,
        "medicine" to TxnCategory.HEALTHCARE,
        "pharmacy" to TxnCategory.HEALTHCARE,
        "entertainment" to TxnCategory.ENTERTAINMENT,
        "movie" to TxnCategory.ENTERTAINMENT,
        "subscription" to TxnCategory.SUBSCRIPTIONS,
        "utilit" to TxnCategory.UTILITIES,
        "electricity" to TxnCategory.UTILITIES,
        "rent" to TxnCategory.RENT,
        "education" to TxnCategory.EDUCATION,
        "invest" to TxnCategory.INVESTMENTS,
        "insurance" to TxnCategory.INSURANCE,
        "loan" to TxnCategory.LOANS,
        "emi" to TxnCategory.LOANS,
        "tax" to TxnCategory.TAXES,
        "charity" to TxnCategory.CHARITY,
        "donation" to TxnCategory.CHARITY,
    )

    private fun detectTypes(text: String, consumed: MutableSet<String>): Set<TransactionType> {
        val types = mutableSetOf<TransactionType>()
        TYPE_WORDS.forEach { (word, type) ->
            if (text.contains(word)) {
                consumed += word
                types += type
            }
        }
        return types
    }

    private val TYPE_WORDS: List<Pair<String, TransactionType>> = listOf(
        "salary" to TransactionType.SALARY,
        "sip" to TransactionType.SIP,
        "dividend" to TransactionType.DIVIDEND,
        "cashback" to TransactionType.CASHBACK,
        "refund" to TransactionType.REFUND,
        "atm" to TransactionType.ATM_WITHDRAWAL,
        "premium" to TransactionType.INSURANCE_PREMIUM,
        "recharge" to TransactionType.RECHARGE,
        "bill" to TransactionType.BILL_PAYMENT,
        "interest" to TransactionType.INTEREST,
    )

    private fun detectDirection(
        text: String,
        intent: QueryIntent,
        types: Set<TransactionType>,
        consumed: MutableSet<String>,
    ): Direction? {
        if (text.contains("earn") || text.contains("income") || text.contains("received")) {
            consumed += "income"
            return Direction.CREDIT
        }
        if (text.contains("spend") || text.contains("spent") || text.contains("paid")) {
            consumed += "spend"
            return Direction.DEBIT
        }
        // A totalling question with no other signal is almost always about spending.
        return if (intent == QueryIntent.TOTAL && types.isEmpty()) Direction.DEBIT else null
    }

    private fun detectMerchants(
        text: String,
        knownMerchants: List<String>,
        consumed: MutableSet<String>,
    ): Set<String> {
        val matches = knownMerchants.filter { merchant ->
            val key = merchant.lowercase()
            key.length >= 3 && text.contains(key)
        }
        matches.forEach { consumed += it.lowercase() }
        return matches.toSet()
    }

    /** Recognises `₹5000`, `over 5000`, `under 200`, `between 100 and 500`. */
    private fun detectAmountBounds(
        text: String,
        consumed: MutableSet<String>,
    ): Pair<Long?, Long?> {
        BETWEEN.find(text)?.let { match ->
            consumed += match.value
            val low = Money.parse(match.groupValues[1])?.minor
            val high = Money.parse(match.groupValues[2])?.minor
            return low to high
        }
        OVER.find(text)?.let { match ->
            consumed += match.value
            return Money.parse(match.groupValues[1])?.minor to null
        }
        UNDER.find(text)?.let { match ->
            consumed += match.value
            return null to Money.parse(match.groupValues[1])?.minor
        }
        EXACT.find(text)?.let { match ->
            consumed += match.value
            val amount = Money.parse(match.groupValues[1])?.minor ?: return null to null
            // An exact figure is really "about this much"; a 1% band absorbs rounding
            // differences between what the user remembers and what the bank recorded.
            val tolerance = (amount / 100).coerceAtLeast(100)
            return (amount - tolerance) to (amount + tolerance)
        }
        return null to null
    }

    private val BETWEEN = Regex("""between\s*₹?\s*([\d,.]+)\s*(?:and|to|-)\s*₹?\s*([\d,.]+)""")
    private val OVER = Regex("""(?:over|above|more than|greater than|>)\s*₹?\s*([\d,.]+)""")
    private val UNDER = Regex("""(?:under|below|less than|<)\s*₹?\s*([\d,.]+)""")
    private val EXACT = Regex("""₹\s*([\d,.]+)""")

    // ---------------------------------------------------------------- residual

    private fun residualText(text: String, consumed: Set<String>): String {
        var remaining = text
        consumed.forEach { remaining = remaining.replace(it, " ") }
        return remaining.split(' ')
            .filter { it.isNotBlank() && it !in FILLER_WORDS && it.length > 2 }
            .joinToString(" ")
            .trim()
    }

    private fun residualTokens(text: String, consumed: Set<String>): List<String> =
        residualText(text, consumed).split(' ').filter { it.isNotBlank() }

    private val FILLER_WORDS = setOf(
        "what", "did", "i", "on", "in", "the", "a", "an", "my", "me", "of", "for",
        "was", "is", "are", "have", "has", "do", "does", "much", "many", "all",
        "show", "list", "find", "get", "give", "tell", "and", "or", "to", "from",
        "spend", "spent", "paid", "pay", "money", "rupees", "rs", "inr", "please",
        "last", "this", "past", "over", "under", "about", "around", "roughly",
    )

    // -------------------------------------------------------------- confidence

    /**
     * Confidence reflects how much of the question was accounted for, not how likely the
     * answer is to be large. An interpretation that consumed a period and a subject is
     * trustworthy; one that fell back to free-text matching is not.
     */
    private fun scoreConfidence(
        intent: QueryIntent,
        range: DateRange?,
        category: TxnCategory?,
        merchants: Set<String>,
        freeText: String,
    ): Confidence {
        var score = 0.35f
        if (intent != QueryIntent.UNKNOWN) score += 0.15f
        if (range != null) score += 0.2f
        if (category != null) score += 0.2f
        if (merchants.isNotEmpty()) score += 0.25f
        if (category == null && merchants.isEmpty() && freeText.isNotBlank()) score -= 0.2f
        return Confidence.of(score)
    }

    private fun explain(
        intent: QueryIntent,
        range: DateRange?,
        category: TxnCategory?,
        types: Set<TransactionType>,
        merchants: Set<String>,
        amounts: Pair<Long?, Long?>,
        freeText: String,
    ): String {
        val parts = mutableListOf<String>()
        parts += when (intent) {
            QueryIntent.TOTAL -> "Totalling"
            QueryIntent.LIST -> "Listing"
            QueryIntent.LATEST -> "Finding the most recent"
            QueryIntent.COUNT -> "Counting"
            QueryIntent.BREAKDOWN -> "Breaking down"
            QueryIntent.COMPARE -> "Comparing"
            QueryIntent.AVERAGE -> "Averaging"
            QueryIntent.UNKNOWN -> "Searching"
        }
        if (merchants.isNotEmpty()) parts += merchants.joinToString(", ")
        if (category != null) parts += category.displayName.lowercase()
        if (types.isNotEmpty()) parts += types.joinToString(", ") { it.name.lowercase().replace('_', ' ') }
        if (merchants.isEmpty() && category == null && types.isEmpty() && freeText.isNotBlank()) {
            parts += "anything matching \"$freeText\""
        }
        parts += if (range == null) {
            "across all time"
        } else {
            "from ${range.start} to ${range.endInclusive}"
        }
        amounts.first?.let { parts += "over ₹${it / 100}" }
        amounts.second?.let { parts += "under ₹${it / 100}" }
        return parts.joinToString(" ") + "."
    }
}
