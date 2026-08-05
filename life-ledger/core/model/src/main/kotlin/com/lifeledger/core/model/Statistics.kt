package com.lifeledger.core.model

import java.time.LocalDate

/** Money in, money out and what is left, over one [DateRange]. */
data class CashFlow(
    val range: DateRange,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val invested: Money = Money.ZERO,
    val transactionCount: Int = 0,
) {
    val net: Money get() = income - expense

    /** Fraction of income not spent. Null when there was no income to divide by. */
    val savingsRate: Double?
        get() = if (income.minor <= 0) null else (income.minor - expense.minor).toDouble() / income.minor

    val averageDailySpend: Money
        get() = if (range.dayCount == 0) Money.zero(expense.currency)
        else Money(expense.minor / range.dayCount, expense.currency)
}

data class CategoryTotal(
    val category: TxnCategory,
    val total: Money,
    val transactionCount: Int,
    val shareOfTotal: Double = 0.0,
)

data class MerchantTotal(
    val merchantId: Long?,
    val merchantName: String,
    val total: Money,
    val transactionCount: Int,
    val lastAt: java.time.Instant? = null,
)

/** One point on any time-series chart. */
data class PeriodTotal(
    val periodStart: LocalDate,
    val granularity: PeriodGranularity,
    val income: Money = Money.ZERO,
    val expense: Money = Money.ZERO,
    val invested: Money = Money.ZERO,
    val transactionCount: Int = 0,
) {
    val net: Money get() = income - expense
}

/** Spending intensity per day, for the calendar heatmap. */
data class DayIntensity(
    val date: LocalDate,
    val total: Money,
    val transactionCount: Int,
)

/** A run of consecutive periods satisfying some predicate — e.g. "invested 18 months running". */
data class Streak(
    val label: String,
    val length: Int,
    val granularity: PeriodGranularity,
    val start: LocalDate,
    val end: LocalDate,
    val isCurrent: Boolean,
)

/** Everything the Dashboard needs, computed in one pass. */
data class DashboardSnapshot(
    val today: CashFlow,
    val week: CashFlow,
    val month: CashFlow,
    val year: CashFlow,
    val netWorthTrackedAccounts: Money = Money.ZERO,
    val totalInvested: Money = Money.ZERO,
    val monthlySubscriptionCost: Money = Money.ZERO,
    val upcomingBills: List<Bill> = emptyList(),
    val upcomingEmis: List<Bill> = emptyList(),
    val largestExpense: Transaction? = null,
    val largestIncome: Transaction? = null,
    val topCategories: List<CategoryTotal> = emptyList(),
    val recentEvents: List<TimelineEvent> = emptyList(),
)
