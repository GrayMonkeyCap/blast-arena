package com.lifeledger.core.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawHorizontalGridlines
import com.lifeledger.core.ui.chart.ChartAxis.drawXAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawYAxisLabels
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/** One band of an [LlStackedAreaChart] — e.g. one category's contribution to total spend. */
data class LlAreaSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
)

/**
 * Stacked area chart: each series' values are cumulative on top of the ones before it, so
 * the top edge of the stack is the running total — how category mix has shifted the
 * overall spend line over time. Only sensible for non-negative series; negative values are
 * clamped to zero rather than producing an inverted, unreadable stack.
 */
@Composable
fun LlStackedAreaChart(
    series: ImmutableList<LlAreaSeries>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    onPointSelected: ((pointIndex: Int) -> Unit)? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ChartDefaults.GRID_LINE_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    val pointCount = series.minOfOrNull { it.values.size } ?: 0
    val progress = remember(series) { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = ChartDefaults.drawInSpec)
    }

    Box(modifier = modifier.fillMaxWidth().height(ChartDefaults.defaultHeight)) {
        if (pointCount == 0 || series.isEmpty()) {
            LlChartEmptyState(modifier = Modifier.fillMaxWidth().height(ChartDefaults.defaultHeight))
            return@Box
        }

        // Running totals per point, clamping every contribution to non-negative so a stray
        // negative (a refund miscategorised as spend, say) can't invert the stack.
        val clamped = series.map { s -> s.values.take(pointCount).map { it.coerceAtLeast(0.0) } }
        val cumulative = (0 until pointCount).map { pointIndex ->
            var running = 0.0
            clamped.map { values -> running += values[pointIndex]; running }
        }
        val totals = cumulative.map { it.lastOrNull() ?: 0.0 }
        val range = ChartMath.valueRange(totals, includeZero = true)
        val ticks = ChartMath.niceTicks(range)
        val xPositions = ChartMath.xPositions(pointCount)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartDefaults.defaultHeight)
                .padding(start = 40.dp, bottom = if (xLabels.isEmpty()) 0.dp else 20.dp)
                .pointerInput(series, pointCount) {
                    if (onPointSelected == null || pointCount == 0) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val index = (fraction * (pointCount - 1)).roundToInt().coerceIn(0, pointCount - 1)
                        onPointSelected(index)
                    }
                },
        ) {
            drawHorizontalGridlines(
                yFractions = ticks.map { ChartMath.normalize(it, range) },
                color = gridColor,
                strokeWidthPx = px(ChartDefaults.gridLineWidth),
            )
            drawYAxisLabels(
                textMeasurer = textMeasurer,
                ticks = ticks,
                range = range,
                formatLabel = { formatAxisValue(it) },
                style = labelStyle,
                color = labelColor,
                leftInsetPx = -px(ChartDefaults.axisLabelPadding),
            )
            if (xLabels.isNotEmpty()) {
                drawXAxisLabels(
                    textMeasurer = textMeasurer,
                    labels = xLabels.take(pointCount),
                    style = labelStyle,
                    color = labelColor,
                    topPx = size.height + px(ChartDefaults.axisLabelPadding),
                )
            }

            clipRect(right = size.width * progress.value) {
                // Bands are drawn back-to-front (last series first) so each band's fill
                // sits *under* the ones stacked above it and doesn't paint over their edges.
                for (seriesIndex in series.indices.reversed()) {
                    val lowerEdge = if (seriesIndex == 0) {
                        List(pointCount) { 0.0 }
                    } else {
                        (0 until pointCount).map { cumulative[it][seriesIndex - 1] }
                    }
                    val upperEdge = (0 until pointCount).map { cumulative[it][seriesIndex] }

                    val path = Path()
                    xPositions.forEachIndexed { index, xFraction ->
                        val x = size.width * xFraction
                        val y = size.height * (1f - ChartMath.normalize(upperEdge[index], range))
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    for (index in pointCount - 1 downTo 0) {
                        val x = size.width * xPositions[index]
                        val y = size.height * (1f - ChartMath.normalize(lowerEdge[index], range))
                        path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path = path, color = series[seriesIndex].color.copy(alpha = ChartDefaults.AREA_FILL_ALPHA + 0.15f))
                }
            }
        }
    }
}
