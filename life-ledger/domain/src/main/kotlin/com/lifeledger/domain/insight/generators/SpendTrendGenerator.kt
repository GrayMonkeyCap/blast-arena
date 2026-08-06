package com.lifeledger.domain.insight.generators

import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.Insight
import com.lifeledger.core.model.InsightKind
import com.lifeledger.core.model.InsightSeverity
import com.lifeledger.core.model.Money
import com.lifeledger.domain.insight.InsightContext
import com.lifeledger.domain.insight.InsightGenerator
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/**
 * "You spent 24% more this month than last."
 *
 * Reports the month-over-month change in total spending, and separately the largest
 * category-level swing — the headline number tells the user *that* something changed, the
 * category tells them *what*, which is the part they can act on.
 *
 * A minimum absolute threshold guards against the trap of percentage reporting: a jump from
 * ₹80 to ₹160 is +100% and completely uninteresting.
 */
class SpendTrendGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "spend-trend"
    override val minimumDaysOfData: Int = 45

    override suspend fun generate(context: InsightContext): List<Insight> {
        val current = DateRange.monthOf(context.today)
        val previous = DateRange.monthOf(context.today.minusMonths(1))

        val currentFlow = context.repositories.cashFlow(current)
        val previousFlow = context.repositories.cashFlow(previous)

        if (previousFlow.expense.minor < MIN_BASELINE_MINOR) return emptyList()

        val insights = mutableListOf<Insight>()
        val delta = currentFlow.expense.minor - previousFlow.expense.minor
        val percent = delta.toDouble() / previousFlow.expense.minor * 100

        if (abs(percent) >= MIN_PERCENT_CHANGE && abs(delta) >= MIN_ABSOLUTE_MINOR) {
            val increased = delta > 0
            insights += Insight(
                kind = InsightKind.SPEND_TREND,
                severity = if (increased) InsightSeverity.WARNING else InsightSeverity.CELEBRATION,
                title = if (increased) {
                    "Spending is up ${percent.toInt()}% this month"
                } else {
                    "Spending is down ${abs(percent).toInt()}% this month"
                },
                body = "You have spent ${currentFlow.expense.display()} so far this month, " +
                    "against ${previousFlow.expense.display()} over the whole of last month.",
                generatedAt = Instant.now(),
                periodStart = current.start,
                periodEnd = current.endInclusive,
                amount = Money(abs(delta), currentFlow.expense.currency),
                changePercent = percent,
                evidence = mapOf(
                    "This month" to currentFlow.expense.display(),
                    "Last month" to previousFlow.expense.display(),
                    "Transactions" to currentFlow.transactionCount.toString(),
                ),
                dedupeKey = "$id:month:${current.start}",
            )
        }

        insights += categorySwing(context, current, previous)
        return insights
    }

    /** The single category that moved most in absolute terms, which is the actionable one. */
    private suspend fun categorySwing(
        context: InsightContext,
        current: DateRange,
        previous: DateRange,
    ): List<Insight> {
        val currentTotals = context.repositories.categoryTotals(current).associateBy { it.category }
        val previousTotals = context.repositories.categoryTotals(previous).associateBy { it.category }

        val biggest = currentTotals.values
            .mapNotNull { total ->
                val before = previousTotals[total.category]?.total?.minor ?: return@mapNotNull null
                if (before < MIN_BASELINE_MINOR) return@mapNotNull null
                val change = total.total.minor - before
                if (abs(change) < MIN_ABSOLUTE_MINOR) return@mapNotNull null
                Triple(total, before, change)
            }
            .maxByOrNull { abs(it.third) }
            ?: return emptyList()

        val (total, before, change) = biggest
        val percent = change.toDouble() / before * 100
        if (abs(percent) < MIN_PERCENT_CHANGE) return emptyList()

        return listOf(
            Insight(
                kind = InsightKind.CATEGORY_SPIKE,
                severity = if (change > 0) InsightSeverity.NOTABLE else InsightSeverity.INFO,
                title = "${total.category.displayName} is " +
                    (if (change > 0) "up" else "down") + " ${abs(percent).toInt()}%",
                body = "${total.category.displayName} came to ${total.total.display()} this month, " +
                    "against ${Money(before, total.total.currency).display()} last month.",
                generatedAt = Instant.now(),
                periodStart = current.start,
                periodEnd = current.endInclusive,
                category = total.category,
                amount = Money(abs(change), total.total.currency),
                changePercent = percent,
                evidence = mapOf(
                    "This month" to total.total.display(),
                    "Last month" to Money(before, total.total.currency).display(),
                    "Transactions" to total.transactionCount.toString(),
                ),
                dedupeKey = "$id:category:${total.category}:${current.start}",
            ),
        )
    }

    private companion object {
        /** Below this, a month's spending is too sparse for a comparison to mean anything. */
        const val MIN_BASELINE_MINOR = 100_000L // ₹1,000
        const val MIN_ABSOLUTE_MINOR = 50_000L // ₹500
        const val MIN_PERCENT_CHANGE = 15.0
    }
}

internal fun Money.display(): String =
    com.lifeledger.core.common.format.MoneyFormatter.format(this)
