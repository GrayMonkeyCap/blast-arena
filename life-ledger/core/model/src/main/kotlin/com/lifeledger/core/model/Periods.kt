package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** A closed-open date range, the unit every statistic and query is scoped by. */
data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!endInclusive.isBefore(start)) { "endInclusive must not precede start" }
    }

    val dayCount: Int get() = (endInclusive.toEpochDay() - start.toEpochDay()).toInt() + 1

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(endInclusive)

    fun startInstant(zone: ZoneId = ZoneId.systemDefault()): Instant =
        start.atStartOfDay(zone).toInstant()

    /** Exclusive upper bound, which is what SQL range predicates want. */
    fun endExclusiveInstant(zone: ZoneId = ZoneId.systemDefault()): Instant =
        endInclusive.plusDays(1).atStartOfDay(zone).toInstant()

    /** The equally sized window immediately before this one, for period-over-period deltas. */
    fun previous(): DateRange {
        val length = dayCount.toLong()
        return DateRange(start.minusDays(length), start.minusDays(1))
    }

    companion object {
        fun today(today: LocalDate = LocalDate.now()) = DateRange(today, today)

        fun weekOf(date: LocalDate = LocalDate.now()): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            return DateRange(start, start.plusDays(6))
        }

        fun monthOf(date: LocalDate = LocalDate.now()) =
            DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))

        fun quarterOf(date: LocalDate = LocalDate.now()): DateRange {
            val start = date.with(IsoFields.DAY_OF_QUARTER, 1)
            return DateRange(start, start.plusMonths(3).minusDays(1))
        }

        fun yearOf(date: LocalDate = LocalDate.now()) =
            DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))

        /** The Indian financial year (1 April – 31 March) containing [date]. */
        fun financialYearOf(date: LocalDate = LocalDate.now()): DateRange {
            val startYear = if (date.monthValue >= 4) date.year else date.year - 1
            return DateRange(LocalDate.of(startYear, 4, 1), LocalDate.of(startYear + 1, 3, 31))
        }

        fun lastDays(count: Int, today: LocalDate = LocalDate.now()) =
            DateRange(today.minusDays(count - 1L), today)

        fun of(granularity: PeriodGranularity, date: LocalDate = LocalDate.now()): DateRange =
            when (granularity) {
                PeriodGranularity.DAY -> today(date)
                PeriodGranularity.WEEK -> weekOf(date)
                PeriodGranularity.MONTH -> monthOf(date)
                PeriodGranularity.QUARTER -> quarterOf(date)
                PeriodGranularity.YEAR -> yearOf(date)
            }
    }
}
