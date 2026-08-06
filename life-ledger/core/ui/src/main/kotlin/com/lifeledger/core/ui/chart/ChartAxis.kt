package com.lifeledger.core.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp

/**
 * Shared axis/gridline/label drawing so every chart's `Canvas` block reads as data-to-pixel
 * mapping plus a couple of calls into this file, instead of every chart re-implementing
 * horizontal rules and rotated labels slightly differently.
 */
object ChartAxis {

    /** Horizontal gridlines at each of [ticks] (already mapped to `0f..1f` y-fractions). */
    fun DrawScope.drawHorizontalGridlines(
        yFractions: List<Float>,
        color: Color,
        strokeWidthPx: Float,
    ) {
        yFractions.forEach { fraction ->
            val y = size.height * (1f - fraction)
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidthPx,
            )
        }
    }

    /** A single axis label drawn left-aligned at [position], vertically centred on it. */
    fun DrawScope.drawAxisLabel(
        textMeasurer: TextMeasurer,
        text: String,
        position: Offset,
        style: TextStyle,
        color: Color,
    ) {
        val layout: TextLayoutResult = textMeasurer.measure(text = text, style = style.copy(color = color))
        val topLeft = Offset(
            x = position.x,
            y = position.y - layout.size.height / 2f,
        )
        drawText(textLayoutResult = layout, topLeft = topLeft)
    }

    /** A label horizontally *and* vertically centred on [position] — donut/ring totals. */
    fun DrawScope.drawCenteredLabel(
        textMeasurer: TextMeasurer,
        text: String,
        position: Offset,
        style: TextStyle,
        color: Color,
    ) {
        val layout: TextLayoutResult = textMeasurer.measure(text = text, style = style.copy(color = color))
        val topLeft = Offset(
            x = position.x - layout.size.width / 2f,
            y = position.y - layout.size.height / 2f,
        )
        drawText(textLayoutResult = layout, topLeft = topLeft)
    }

    /** Y-axis labels for [ticks], right-aligned just inside the chart's left edge. */
    fun DrawScope.drawYAxisLabels(
        textMeasurer: TextMeasurer,
        ticks: List<Double>,
        range: ChartMath.ValueRange,
        formatLabel: (Double) -> String,
        style: TextStyle,
        color: Color,
        leftInsetPx: Float,
    ) {
        ticks.forEach { tick ->
            val fraction = ChartMath.normalize(tick, range)
            val y = size.height * (1f - fraction)
            val label = formatLabel(tick)
            val layout = textMeasurer.measure(text = label, style = style.copy(color = color))
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x = leftInsetPx - layout.size.width, y = y - layout.size.height / 2f),
            )
        }
    }

    /** X-axis category labels, evenly spaced per [ChartMath.xPositions]. */
    fun DrawScope.drawXAxisLabels(
        textMeasurer: TextMeasurer,
        labels: List<String>,
        style: TextStyle,
        color: Color,
        topPx: Float,
    ) {
        val positions = ChartMath.xPositions(labels.size)
        labels.forEachIndexed { index, label ->
            val layout = textMeasurer.measure(text = label, style = style.copy(color = color))
            val x = (size.width * positions[index] - layout.size.width / 2f)
                .coerceIn(0f, size.width - layout.size.width)
            drawText(textLayoutResult = layout, topLeft = Offset(x = x, y = topPx))
        }
    }

    /** The zero baseline, drawn heavier than ordinary gridlines when the value range spans zero. */
    fun DrawScope.drawZeroBaseline(range: ChartMath.ValueRange, color: Color, strokeWidthPx: Float) {
        if (range.min >= 0.0 || range.max <= 0.0) return
        val fraction = ChartMath.normalize(0.0, range)
        val y = size.height * (1f - fraction)
        drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidthPx)
    }
}

/** Converts a [Dp] stroke/line width to raw pixels inside a [DrawScope] without repeating
 *  `.toPx()` at every call site across eight chart files. */
fun DrawScope.px(dp: Dp): Float = dp.toPx()
