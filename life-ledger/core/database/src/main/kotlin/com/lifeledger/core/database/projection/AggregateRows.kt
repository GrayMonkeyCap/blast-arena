package com.lifeledger.core.database.projection

/**
 * Row shapes for the aggregate queries.
 *
 * These are deliberately *not* the domain statistics types from `core:model`. Room maps a
 * cursor onto them by column name, so they must mirror the SQL — flat, primitive, minor units
 * and epoch numbers — while `CashFlow`, `CategoryTotal` and friends carry `Money`, `LocalDate`
 * and enums. Keeping the two apart is what lets a query be reshaped for performance without
 * the domain model following it, and it stops SQL column aliases from leaking into the UI.
 *
 * Every one of these is produced by a query that already excludes duplicates and rows the
 * user removed from statistics; see `TransactionDao`.
 */
data class CategoryTotalRow(
    /** Name of `TxnCategory`, exactly as stored. */
    val category: String,
    val totalMinor: Long,
    val count: Int,
)

data class MerchantTotalRow(
    /** Null for spend that was never resolved to a catalogue merchant. */
    val merchantId: Long?,
    val merchantName: String?,
    val totalMinor: Long,
    val count: Int,
    /** Epoch millis of the most recent transaction in the group. */
    val lastAt: Long?,
)

/**
 * One point on a time series. [periodKey] is the `strftime` bucket the row was grouped by —
 * `YYYY-MM-DD` for daily, `YYYY-MM` for monthly — computed in the device's local time, because
 * a user who spends at 1am expects that spend on the day they lived, not the UTC day.
 */
data class PeriodTotalRow(
    val periodKey: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val investedMinor: Long,
    val count: Int,
)

/**
 * Money in, money out and money invested over one window.
 *
 * [investedMinor] is a *subset* of [expenseMinor], not a third bucket beside it: a SIP debit
 * is genuinely money leaving the account, and double-subtracting it would understate what is
 * left. Callers that want "spend excluding investments" subtract the two.
 */
data class CashFlowRow(
    val incomeMinor: Long,
    val expenseMinor: Long,
    val investedMinor: Long,
    val count: Int,
)

/** One cell of the calendar heatmap. */
data class DayIntensityRow(
    val epochDay: Long,
    val totalMinor: Long,
    val count: Int,
)

/**
 * One column of the spend-distribution histogram.
 *
 * The bucket width is chosen by the caller and passed into the query, so the same SQL serves
 * "₹100 buckets over a month" and "₹5,000 buckets over a year" without a second query.
 */
data class AmountBucketRow(
    /** Inclusive lower edge of the bucket, in minor units. */
    val bucketFloorMinor: Long,
    val totalMinor: Long,
    val count: Int,
)
