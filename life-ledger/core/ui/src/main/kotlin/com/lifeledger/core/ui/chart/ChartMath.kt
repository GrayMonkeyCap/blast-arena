package com.lifeledger.core.ui.chart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure geometry for every chart in [com.lifeledger.core.ui.chart] — no `Canvas`, no
 * Compose types, so scaling and tick selection can be unit-tested without an Android
 * runtime and reused identically across line/bar/donut/waterfall drawing code.
 *
 * Every function here is defensive about the inputs a real chart sees in the wild: an
 * empty series, a single point, and a series that is every zero (a brand-new account with
 * no spend yet) are all first-class cases, not edge cases that crash the canvas.
 */
object ChartMath {

    /** A closed value range with a floor at [min] and ceiling at [max], guaranteed `max >= min`. */
    data class ValueRange(val min: Double, val max: Double) {
        val span: Double get() = max - min
    }

    /**
     * The [ValueRange] a chart should scale to for [values]: pads a single-value or
     * all-equal series so it doesn't collapse to a zero-height line, and always includes
     * zero when [includeZero] is set (bar/area charts read oddly if their baseline floats).
     */
    fun valueRange(values: List<Double>, includeZero: Boolean = true): ValueRange {
        if (values.isEmpty()) return ValueRange(0.0, 1.0)

        var lo = values.min()
        var hi = values.max()
        if (includeZero) {
            lo = min(lo, 0.0)
            hi = max(hi, 0.0)
        }
        if (lo == hi) {
            // A flat series (all zero, or a single repeated value) still needs headroom to
            // draw a visible line/bar rather than a degenerate point at the axis.
            val pad = if (lo == 0.0) 1.0 else abs(lo) * 0.1
            lo -= pad
            hi += pad
        }
        return ValueRange(lo, hi)
    }

    /** Maps [value] within [range] to a `0f..1f` fraction of the drawable height/width. */
    fun normalize(value: Double, range: ValueRange): Float {
        if (range.span == 0.0) return 0f
        return ((value - range.min) / range.span).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Evenly spaced "nice" axis tick values covering [range] — the classic 1/2/5 × 10^n
     * algorithm, so a y-axis reads `0, 25, 50, 75, 100` instead of `0, 23.7, 47.4, ...`.
     * Always returns at least two ticks (the range's own bounds) even for a zero-span range.
     */
    fun niceTicks(range: ValueRange, targetCount: Int = 5): List<Double> {
        val count = max(2, targetCount)
        if (range.span <= 0.0) return listOf(range.min, range.min + 1.0)

        val roughStep = range.span / (count - 1)
        val niceStep = niceNumber(roughStep, roundUp = true)
        val niceMin = floor(range.min / niceStep) * niceStep
        val niceMax = ceil(range.max / niceStep) * niceStep

        val ticks = mutableListOf<Double>()
        var tick = niceMin
        // Guard against float drift producing an unbounded loop for a pathological step.
        var iterations = 0
        while (tick <= niceMax + niceStep * 0.5 && iterations < 1000) {
            ticks += roundToStep(tick, niceStep)
            tick += niceStep
            iterations++
        }
        return ticks
    }

    /** Rounds [value] to the nearest "nice" 1/2/5 × 10^n number, per Heckbert's algorithm. */
    private fun niceNumber(value: Double, roundUp: Boolean): Double {
        if (value <= 0.0) return 1.0
        val exponent = floor(log10(value))
        val fraction = value / 10.0.pow(exponent)

        val niceFraction = if (roundUp) {
            when {
                fraction <= 1.0 -> 1.0
                fraction <= 2.0 -> 2.0
                fraction <= 5.0 -> 5.0
                else -> 10.0
            }
        } else {
            when {
                fraction < 1.5 -> 1.0
                fraction < 3.0 -> 2.0
                fraction < 7.0 -> 5.0
                else -> 10.0
            }
        }
        return niceFraction * 10.0.pow(exponent)
    }

    private fun log10(value: Double) = ln(value) / ln(10.0)

    private fun roundToStep(value: Double, step: Double): Double {
        if (step == 0.0) return value
        // Kill float dust (e.g. 0.30000000000000004) so labels format cleanly.
        val decimals = max(0, -floor(log10(step)).toInt() + 2)
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToInt() / factor
    }

    /**
     * Even x-positions (as `0f..1f` fractions) for [count] points along an axis. A single
     * point centres itself rather than dividing by zero.
     */
    fun xPositions(count: Int): List<Float> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(0.5f)
        return (0 until count).map { it.toFloat() / (count - 1) }
    }

    /**
     * Bar centre + half-width (both as `0f..1f` fractions of the drawable width) for
     * [count] bars sharing one axis, with [groupSize] bars sharing each category slot
     * (`groupSize > 1` for a grouped bar chart) and [gapFraction] of each slot left as
     * breathing room between categories.
     */
    fun barLayout(count: Int, groupSize: Int = 1, gapFraction: Float = 0.3f): List<Float> {
        if (count <= 0) return emptyList()
        val slotWidth = 1f / count
        val gap = slotWidth * gapFraction.coerceIn(0f, 0.9f)
        val usableWidth = slotWidth - gap
        return (0 until count).map { index -> index * slotWidth + gap / 2f + usableWidth / 2f }
    }

    /** Sweep angle in degrees for one donut/pie slice given its share of [total]. */
    fun sweepAngle(value: Double, total: Double): Float {
        if (total <= 0.0 || value <= 0.0) return 0f
        return (360.0 * (value / total)).toFloat()
    }

    /**
     * Running-total bar positions for a waterfall chart: each entry's `(start, end)` pair
     * in raw value units, ready to be normalized with [normalize] against the range of all
     * cumulative points. Handles an empty list by returning no bars rather than dividing
     * by a zero total.
     */
    fun waterfallSteps(values: List<Double>): List<Pair<Double, Double>> {
        var running = 0.0
        return values.map { delta ->
            val start = running
            running += delta
            start to running
        }
    }
}
