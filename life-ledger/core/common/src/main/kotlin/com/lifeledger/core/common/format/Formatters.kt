package com.lifeledger.core.common.format

import com.lifeledger.core.model.Money
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Money formatting for Indian users: the lakh/crore grouping (`12,34,567`) rather than the
 * thousands grouping `java.text.NumberFormat` would give for a generic locale.
 */
object MoneyFormatter {

    fun format(money: Money, showDecimals: Boolean = false, showSign: Boolean = false): String {
        val symbol = symbolFor(money.currency)
        val negative = money.minor < 0
        val absMinor = abs(money.minor)
        val whole = absMinor / 100
        val fraction = absMinor % 100

        val grouped = if (money.currency == Money.INR) groupIndian(whole) else groupWestern(whole)
        val decimals = if (showDecimals || fraction != 0L) ".%02d".format(fraction) else ""

        val sign = when {
            negative -> "-"
            showSign -> "+"
            else -> ""
        }
        return "$sign$symbol$grouped$decimals"
    }

    /** Short form for chart axes and dense tiles: `₹1.2L`, `₹85K`, `₹4.3Cr`. */
    fun compact(money: Money): String {
        val symbol = symbolFor(money.currency)
        val negative = money.minor < 0
        val whole = abs(money.minor) / 100
        val sign = if (negative) "-" else ""
        return sign + symbol + when {
            whole >= 10_000_000 -> trim(whole / 10_000_000.0) + "Cr"
            whole >= 100_000 -> trim(whole / 100_000.0) + "L"
            whole >= 1_000 -> trim(whole / 1_000.0) + "K"
            else -> whole.toString()
        }
    }

    fun symbolFor(currency: String): String = when (currency) {
        Money.INR -> "₹"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$currency "
    }

    private fun trim(value: Double): String =
        if (value >= 100 || value % 1.0 < 0.05) value.toLong().toString() else "%.1f".format(value)

    /** Indian digit grouping: last three digits, then pairs. */
    private fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val grouped = StringBuilder()
        var count = 0
        for (i in rest.lastIndex downTo 0) {
            grouped.append(rest[i])
            count++
            if (count % 2 == 0 && i != 0) grouped.append(',')
        }
        return grouped.reverse().toString() + "," + last3
    }

    private fun groupWestern(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(",").reversed()
}

object DateTimeFormatters {

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val weekday = DateTimeFormatter.ofPattern("EEEE")
    private val clock = DateTimeFormatter.ofPattern("h:mm a")

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(instant.atZone(zone))

    fun date(date: LocalDate, includeYear: Boolean = date.year != LocalDate.now().year): String =
        if (includeYear) dayMonthYear.format(date) else dayMonth.format(date)

    fun monthYear(date: LocalDate): String = monthYear.format(date)

    fun weekday(date: LocalDate): String = weekday.format(date)

    /** `Today`, `Yesterday`, `Monday` (within a week), then a plain date. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date == today.plusDays(1) -> "Tomorrow"
        ChronoUnit.DAYS.between(date, today) in 2..6 -> weekday(date)
        else -> date(date)
    }

    /** `just now`, `12m ago`, `3h ago`, `4d ago`, then a date. */
    fun relativeTime(
        instant: Instant,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val duration = Duration.between(instant, now)
        val minutes = duration.toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> date(instant.atZone(zone).toLocalDate())
        }
    }

    /** Time-of-day bucket used to head up timeline sections. */
    fun partOfDay(time: LocalTime): String = when (time.hour) {
        in 0..4 -> "Late night"
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
}
