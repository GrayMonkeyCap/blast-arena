package com.lifeledger.domain.insight.generators

import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.Insight
import com.lifeledger.core.model.InsightKind
import com.lifeledger.core.model.InsightSeverity
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.Recurrence
import com.lifeledger.core.model.Subscription
import com.lifeledger.domain.insight.InsightContext
import com.lifeledger.domain.insight.InsightGenerator
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * "You are paying ₹2,480 a month for 9 subscriptions."
 *
 * Also surfaces the two things people actually want to know about subscriptions: which ones
 * quietly changed price, and which ones have stopped being charged (usually meaning a
 * cancellation the user forgot they made — or a failed payment they did not notice).
 */
class SubscriptionInsightGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "subscriptions"
    override val minimumDaysOfData: Int = 60

    override suspend fun generate(context: InsightContext): List<Insight> {
        val subscriptions = context.repositories.subscriptions()
        if (subscriptions.isEmpty()) return emptyList()

        val insights = mutableListOf<Insight>()
        val active = subscriptions.filter { it.status == Subscription.Status.ACTIVE }

        if (active.isNotEmpty()) {
            val monthly = Money(active.sumOf { it.monthlyCost.minor })
            insights += Insight(
                kind = InsightKind.RECURRING_DETECTED,
                severity = InsightSeverity.INFO,
                title = "${active.size} active subscriptions cost ${monthly.display()} a month",
                body = "That is ${Money(monthly.minor * 12).display()} a year. " +
                    "The largest is ${active.maxBy { it.monthlyCost.minor }.name}.",
                generatedAt = Instant.now(),
                amount = monthly,
                evidence = active
                    .sortedByDescending { it.monthlyCost.minor }
                    .take(EVIDENCE_LIMIT)
                    .associate { it.name to it.monthlyCost.display() },
                dedupeKey = "$id:total:${context.today.withDayOfMonth(1)}",
            )
        }

        subscriptions
            .filter { it.previousAmount != null && it.priceChangedAt != null }
            .forEach { subscription ->
                val before = subscription.previousAmount ?: return@forEach
                if (before.minor == 0L) return@forEach
                val percent = (subscription.amount.minor - before.minor).toDouble() / before.minor * 100
                insights += Insight(
                    kind = InsightKind.SUBSCRIPTION_PRICE_CHANGE,
                    severity = if (percent > 0) InsightSeverity.WARNING else InsightSeverity.INFO,
                    title = "${subscription.name} changed price",
                    body = "It went from ${before.display()} to ${subscription.amount.display()} " +
                        "per ${subscription.recurrence.name.lowercase()} cycle.",
                    generatedAt = Instant.now(),
                    amount = subscription.amount,
                    changePercent = percent,
                    evidence = mapOf(
                        "Was" to before.display(),
                        "Now" to subscription.amount.display(),
                    ),
                    dedupeKey = "$id:price:${subscription.id}:${subscription.amount.minor}",
                )
            }

        subscriptions
            .filter { it.status == Subscription.Status.ACTIVE && it.recurrence.isRecurring }
            .filter { subscription ->
                val last = subscription.lastChargedAt ?: return@filter false
                val elapsed = ChronoUnit.DAYS.between(last, Instant.now())
                elapsed > subscription.recurrence.approxDays * LAPSE_MULTIPLIER
            }
            .forEach { subscription ->
                insights += Insight(
                    kind = InsightKind.UNUSED_SUBSCRIPTION,
                    severity = InsightSeverity.NOTABLE,
                    title = "${subscription.name} has not been charged recently",
                    body = "The last charge was ${subscription.amount.display()}. " +
                        "If you cancelled it, marking it cancelled keeps your monthly total honest.",
                    generatedAt = Instant.now(),
                    amount = subscription.amount,
                    dedupeKey = "$id:lapsed:${subscription.id}",
                )
            }

        return insights
    }

    private companion object {
        const val EVIDENCE_LIMIT = 6

        /** A cycle and a half with no charge is the point at which "late" becomes "gone". */
        const val LAPSE_MULTIPLIER = 1.5
    }
}

/**
 * "You have invested every month for 18 months."
 *
 * Investment consistency is the one financial habit worth celebrating explicitly, because
 * the behaviour it reinforces — continuing through a bad month — is exactly the one people
 * abandon. The generator counts consecutive calendar months containing at least one
 * investment outflow.
 */
class InvestmentStreakGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "investment-streak"
    override val minimumDaysOfData: Int = 90

    override suspend fun generate(context: InsightContext): List<Insight> {
        val investments = context.repositories.investments()
        if (investments.isEmpty()) return emptyList()

        var streak = 0
        var cursor = context.today.withDayOfMonth(1)
        var total = 0L

        while (streak < MAX_MONTHS_SCANNED) {
            val month = DateRange(cursor, cursor.plusMonths(1).minusDays(1))
            val invested = context.repositories.cashFlow(month).invested
            if (invested.minor <= 0) break
            total += invested.minor
            streak++
            cursor = cursor.minusMonths(1)
        }

        if (streak < MIN_STREAK) return emptyList()

        return listOf(
            Insight(
                kind = InsightKind.INVESTMENT_STREAK,
                severity = InsightSeverity.CELEBRATION,
                title = "You have invested every month for $streak months",
                body = "That comes to ${Money(total).display()} put away over that stretch, " +
                    "across ${investments.count { it.isActive }} active holdings.",
                generatedAt = Instant.now(),
                amount = Money(total),
                evidence = mapOf(
                    "Streak" to "$streak months",
                    "Total invested" to Money(total).display(),
                    "Average per month" to Money(total / streak).display(),
                ),
                dedupeKey = "$id:${context.today.withDayOfMonth(1)}",
            ),
        )
    }

    private companion object {
        const val MIN_STREAK = 3
        const val MAX_MONTHS_SCANNED = 120
    }
}

/**
 * "Your electricity bill went up 32%."
 *
 * Bills are the category where a change is most likely to be a real-world signal — a new
 * appliance, a tariff revision, a leak — rather than a lifestyle choice, so they get their
 * own generator with a lower threshold than general category drift.
 */
class BillChangeGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "bill-change"
    override val minimumDaysOfData: Int = 90

    override suspend fun generate(context: InsightContext): List<Insight> =
        context.repositories.bills()
            .filter { it.paymentCount >= MIN_PAYMENTS }
            .mapNotNull { bill ->
                val last = bill.lastAmount ?: return@mapNotNull null
                val average = bill.averageAmount ?: return@mapNotNull null
                if (average.minor < MIN_BASELINE_MINOR) return@mapNotNull null

                val percent = (last.minor - average.minor).toDouble() / average.minor * 100
                if (percent < MIN_PERCENT_INCREASE) return@mapNotNull null

                Insight(
                    kind = InsightKind.BILL_INCREASE,
                    severity = InsightSeverity.WARNING,
                    title = "${bill.name} is ${percent.toInt()}% above its usual amount",
                    body = "The latest bill was ${last.display()}, against a typical " +
                        "${average.display()} across ${bill.paymentCount} payments.",
                    generatedAt = Instant.now(),
                    amount = last,
                    changePercent = percent,
                    evidence = mapOf(
                        "Latest" to last.display(),
                        "Typical" to average.display(),
                        "Payments seen" to bill.paymentCount.toString(),
                    ),
                    dedupeKey = "$id:${bill.id}:${last.minor}",
                )
            }

    private companion object {
        const val MIN_PAYMENTS = 3
        const val MIN_BASELINE_MINOR = 20_000L // ₹200
        const val MIN_PERCENT_INCREASE = 25.0
    }
}

/**
 * "Your salary arrives on the last working day."
 *
 * Recognising the rhythm of income is what turns a ledger into something predictive: once
 * the pattern is known, the app can tell the user how many days of runway they have rather
 * than only what they have already spent.
 */
class IncomePatternGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "income-pattern"
    override val minimumDaysOfData: Int = 120

    override suspend fun generate(context: InsightContext): List<Insight> {
        val range = DateRange(context.today.minusMonths(LOOKBACK_MONTHS), context.today)
        val salaries = context.repositories.transactions(range)
            .filter { it.type == com.lifeledger.core.model.TransactionType.SALARY }
        if (salaries.size < MIN_OCCURRENCES) return emptyList()

        val days = salaries.map { it.localDate(context.zone).dayOfMonth }
        val median = days.sorted()[days.size / 2]
        val consistent = days.count { kotlin.math.abs(it - median) <= DAY_TOLERANCE }
        if (consistent.toDouble() / days.size < MIN_CONSISTENCY) return emptyList()

        val descriptor = when {
            median >= 28 -> "at the very end of the month"
            median <= 3 -> "in the first days of the month"
            else -> "around the ${median}th"
        }

        val average = Money(salaries.sumOf { it.amount.minor } / salaries.size)

        return listOf(
            Insight(
                kind = InsightKind.INCOME_PATTERN,
                severity = InsightSeverity.INFO,
                title = "Your salary arrives $descriptor",
                body = "Across ${salaries.size} months it has averaged ${average.display()}, " +
                    "landing within $DAY_TOLERANCE days of the ${median}th every time.",
                generatedAt = Instant.now(),
                amount = average,
                evidence = mapOf(
                    "Occurrences" to salaries.size.toString(),
                    "Typical day" to median.toString(),
                    "Average amount" to average.display(),
                ),
                dedupeKey = "$id:${context.today.withDayOfMonth(1)}",
            ),
        )
    }

    private companion object {
        const val LOOKBACK_MONTHS = 12L
        const val MIN_OCCURRENCES = 4
        const val DAY_TOLERANCE = 3
        const val MIN_CONSISTENCY = 0.7
    }
}

/**
 * "You kept 38% of what you earned this month."
 *
 * Savings rate is reported rather than judged. There is no target, no score and no nudge —
 * the app's job is to show the user their own number, not to have an opinion about it.
 */
class SavingsRateGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "savings-rate"
    override val minimumDaysOfData: Int = 60

    override suspend fun generate(context: InsightContext): List<Insight> {
        val month = DateRange.monthOf(context.today.minusMonths(1))
        val flow = context.repositories.cashFlow(month)
        val rate = flow.savingsRate ?: return emptyList()
        if (flow.income.minor < MIN_INCOME_MINOR) return emptyList()

        return listOf(
            Insight(
                kind = InsightKind.SAVINGS_RATE,
                severity = if (rate >= 0) InsightSeverity.INFO else InsightSeverity.WARNING,
                title = "You kept ${(rate * 100).toInt()}% of what you earned last month",
                body = "Income was ${flow.income.display()} and spending ${flow.expense.display()}, " +
                    "leaving ${flow.net.display()}.",
                generatedAt = Instant.now(),
                periodStart = month.start,
                periodEnd = month.endInclusive,
                amount = flow.net,
                changePercent = rate * 100,
                evidence = mapOf(
                    "Income" to flow.income.display(),
                    "Spending" to flow.expense.display(),
                    "Invested" to flow.invested.display(),
                    "Average per day" to flow.averageDailySpend.display(),
                ),
                dedupeKey = "$id:${month.start}",
            ),
        )
    }

    private companion object {
        const val MIN_INCOME_MINOR = 100_000L // ₹1,000
    }
}

/** Flags a single transaction far larger than the user's own norm. */
class LargeTransactionGenerator @Inject constructor() : InsightGenerator {

    override val id: String = "large-transaction"
    override val minimumDaysOfData: Int = 30

    override suspend fun generate(context: InsightContext): List<Insight> {
        val recent = DateRange(context.today.minusDays(RECENT_DAYS), context.today)
        val baseline = DateRange(context.today.minusMonths(BASELINE_MONTHS), context.today)

        val history = context.repositories.transactions(baseline).filter { it.isExpense }
        if (history.size < MIN_HISTORY) return emptyList()

        val sorted = history.map { it.amount.minor }.sorted()
        val p95 = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)]

        return context.repositories.transactions(recent)
            .filter { it.isExpense && it.amount.minor > p95 && it.amount.minor > MIN_ABSOLUTE_MINOR }
            .map { transaction ->
                Insight(
                    kind = InsightKind.LARGE_TRANSACTION,
                    severity = InsightSeverity.NOTABLE,
                    title = "${transaction.amount.display()} at ${transaction.displayTitle}",
                    body = "That is larger than 95% of your spending over the last " +
                        "$BASELINE_MONTHS months.",
                    generatedAt = Instant.now(),
                    amount = transaction.amount,
                    category = transaction.category,
                    relatedTransactionIds = listOf(transaction.id),
                    evidence = mapOf(
                        "This transaction" to transaction.amount.display(),
                        "Your 95th percentile" to Money(p95).display(),
                    ),
                    dedupeKey = "$id:${transaction.id}",
                )
            }
    }

    private companion object {
        const val RECENT_DAYS = 7L
        const val BASELINE_MONTHS = 6L
        const val MIN_HISTORY = 40
        const val MIN_ABSOLUTE_MINOR = 500_000L // ₹5,000
    }
}
