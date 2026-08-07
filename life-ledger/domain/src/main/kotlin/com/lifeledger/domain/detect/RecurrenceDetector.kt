package com.lifeledger.domain.detect

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.Recurrence
import com.lifeledger.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Finds repeating payments in transaction history.
 *
 * This is the engine behind both subscription detection and bill tracking, because they are
 * the same problem seen from two angles: a series of payments to one counterparty at a
 * roughly constant interval. Subscriptions additionally hold a roughly constant *amount*;
 * bills vary in amount but keep their rhythm. The detector reports both signals separately
 * so each caller can apply its own rule.
 *
 * The approach is deliberately statistical rather than pattern-matched. Real recurrences
 * are messy — weekend shifts, holiday delays, a bank posting a day late — so the detector
 * works from the *median* gap and measures how tightly the observations cluster around it,
 * rather than demanding exact intervals that real data never produces.
 */
@Singleton
class RecurrenceDetector @Inject constructor() {

    /**
     * Groups [transactions] by counterparty and returns every series that looks recurring.
     *
     * [minOccurrences] of three is the smallest number that can distinguish a rhythm from a
     * coincidence: two payments define an interval, the third confirms it.
     */
    fun detect(
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
        minOccurrences: Int = 3,
    ): List<RecurringSeries> = transactions
        .filter { it.isExpense && it.merchantName != null }
        .groupBy { it.merchantName!! }
        .mapNotNull { (merchant, rows) ->
            analyse(merchant, rows.sortedBy { it.occurredAt }, zone, minOccurrences)
        }
        .sortedByDescending { it.confidence.value }

    private fun analyse(
        merchant: String,
        occurrences: List<Transaction>,
        zone: ZoneId,
        minOccurrences: Int,
    ): RecurringSeries? {
        if (occurrences.size < minOccurrences) return null

        val gaps = occurrences.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(
                a.occurredAt.atZone(zone).toLocalDate(),
                b.occurredAt.atZone(zone).toLocalDate(),
            )
        }.filter { it > 0 }

        if (gaps.size < minOccurrences - 1) return null

        val medianGap = gaps.sorted()[gaps.size / 2]
        if (medianGap < MIN_GAP_DAYS || medianGap > MAX_GAP_DAYS) return null

        val recurrence = recurrenceFor(medianGap)
        if (recurrence == Recurrence.IRREGULAR) return null

        // How tightly the gaps cluster on the median, as a 0..1 score. A monthly series is
        // allowed proportionally more drift than a weekly one, since "the 3rd of the month"
        // legitimately moves by a couple of days.
        val tolerance = (medianGap * GAP_TOLERANCE_FRACTION).coerceAtLeast(MIN_TOLERANCE_DAYS)
        val onRhythm = gaps.count { abs(it - medianGap) <= tolerance }
        val rhythmScore = onRhythm.toDouble() / gaps.size
        if (rhythmScore < MIN_RHYTHM_SCORE) return null

        val amounts = occurrences.map { it.amount.minor }
        val medianAmount = amounts.sorted()[amounts.size / 2]
        val amountTolerance = (medianAmount * AMOUNT_TOLERANCE_FRACTION).toLong()
            .coerceAtLeast(MIN_AMOUNT_TOLERANCE_MINOR)
        val steadyAmounts = amounts.count { abs(it - medianAmount) <= amountTolerance }
        val amountScore = steadyAmounts.toDouble() / amounts.size

        val last = occurrences.last()
        val lastDate = last.occurredAt.atZone(zone).toLocalDate()

        return RecurringSeries(
            merchantName = merchant,
            merchantId = last.merchantId,
            occurrences = occurrences,
            recurrence = recurrence,
            medianGapDays = medianGap.toInt(),
            typicalAmount = Money(medianAmount, last.amount.currency),
            latestAmount = last.amount,
            firstSeenAt = occurrences.first().occurredAt,
            lastSeenAt = last.occurredAt,
            nextExpectedAt = lastDate.plusDays(medianGap),
            rhythmScore = rhythmScore,
            amountStabilityScore = amountScore,
            confidence = scoreConfidence(occurrences.size, rhythmScore, amountScore),
            priceChange = detectPriceChange(occurrences, medianAmount, amountTolerance),
        )
    }

    /**
     * Maps an observed gap onto the nearest named cadence, but only when it is genuinely
     * close. A 45-day gap is not "monthly with drift", it is irregular, and treating it as
     * monthly would produce a due-date reminder that is always wrong.
     */
    private fun recurrenceFor(days: Long): Recurrence {
        val candidates = listOf(
            Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.FORTNIGHTLY, Recurrence.MONTHLY,
            Recurrence.BIMONTHLY, Recurrence.QUARTERLY, Recurrence.HALF_YEARLY, Recurrence.YEARLY,
        )
        val best = candidates.minByOrNull { abs(it.approxDays - days) } ?: return Recurrence.IRREGULAR
        val drift = abs(best.approxDays - days).toDouble() / best.approxDays
        return if (drift <= CADENCE_MATCH_TOLERANCE) best else Recurrence.IRREGULAR
    }

    /**
     * Detects a step change in amount: the series settling at a new level rather than one
     * odd payment. Requires the new level to hold for at least two occurrences, which is
     * what separates a price rise from a one-off overage charge.
     */
    private fun detectPriceChange(
        occurrences: List<Transaction>,
        medianAmount: Long,
        tolerance: Long,
    ): PriceChange? {
        if (occurrences.size < 4) return null
        val recent = occurrences.takeLast(2).map { it.amount.minor }
        if (recent.any { abs(it - recent.first()) > tolerance }) return null
        if (abs(recent.first() - medianAmount) <= tolerance) return null

        val earlier = occurrences.dropLast(2).map { it.amount.minor }
        val earlierMedian = earlier.sorted()[earlier.size / 2]
        if (abs(recent.first() - earlierMedian) <= tolerance) return null

        val currency = occurrences.last().amount.currency
        return PriceChange(
            from = Money(earlierMedian, currency),
            to = Money(recent.first(), currency),
            changedAt = occurrences[occurrences.size - 2].occurredAt,
        )
    }

    /**
     * Confidence rises with the number of observations and with how regular they are.
     * Amount stability contributes less than rhythm because a bill is a genuine recurrence
     * even though its amount moves every month.
     */
    private fun scoreConfidence(
        occurrences: Int,
        rhythmScore: Double,
        amountScore: Double,
    ): Confidence {
        val volume = ((occurrences - 2).coerceAtMost(6) / 6.0) * 0.3
        return Confidence.of((0.2 + volume + rhythmScore * 0.4 + amountScore * 0.2).toFloat())
    }

    private companion object {
        const val MIN_GAP_DAYS = 5L
        const val MAX_GAP_DAYS = 400L
        const val GAP_TOLERANCE_FRACTION = 0.2
        const val MIN_TOLERANCE_DAYS = 2.0
        const val MIN_RHYTHM_SCORE = 0.6
        const val AMOUNT_TOLERANCE_FRACTION = 0.05
        const val MIN_AMOUNT_TOLERANCE_MINOR = 500L // ₹5
        const val CADENCE_MATCH_TOLERANCE = 0.25
    }
}

/** A repeating payment series and everything derived from it. */
data class RecurringSeries(
    val merchantName: String,
    val merchantId: Long?,
    val occurrences: List<Transaction>,
    val recurrence: Recurrence,
    val medianGapDays: Int,
    /** Representative amount — the median, which ignores one-off spikes. */
    val typicalAmount: Money,
    val latestAmount: Money,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val nextExpectedAt: LocalDate,
    /** How tightly the intervals cluster, 0..1. */
    val rhythmScore: Double,
    /** How constant the amount is, 0..1. High means subscription-like. */
    val amountStabilityScore: Double,
    val confidence: Confidence,
    val priceChange: PriceChange?,
) {
    /**
     * A steady amount on a steady rhythm is a subscription; a moving amount on a steady
     * rhythm is a bill. The threshold is deliberately high — misfiling a bill as a
     * subscription would put a wrong figure in the user's monthly subscription total.
     */
    val looksLikeSubscription: Boolean
        get() = amountStabilityScore >= 0.8 && recurrence.isRecurring

    val looksLikeBill: Boolean
        get() = !looksLikeSubscription && recurrence.isRecurring && rhythmScore >= 0.7
}

data class PriceChange(
    val from: Money,
    val to: Money,
    val changedAt: Instant,
)
