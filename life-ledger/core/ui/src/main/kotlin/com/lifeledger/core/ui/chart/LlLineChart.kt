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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawHorizontalGridlines
import com.lifeledger.core.ui.chart.ChartAxis.drawXAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawYAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawZeroBaseline
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList

/** One named line on an [LlLineChart] — spend trend, income trend, a per-category overlay. */
data class LlLineSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
)

/**
 * A multi-series line chart with an animated draw-in and an optional area fill under the
 * first series — the shape used for "spend over time" and "net worth over time".
 *
 * All series must share the same number of points (one per [xLabels] entry, or per index
 * if [xLabels] is empty); mismatched lengths degrade gracefully by truncating to the
 * shortest series rather than throwing, since a partially-loaded ViewModel state is a real
 * situation this will render during.
 */
@Composable
fun LlLineChart(
    series: ImmutableList<LlLineSeries>,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    showArea: Boolean = false,
    onPointSelected: ((seriesIndex: Int, pointIndex: Int) -> Unit)? = null,
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
        if (pointCount == 0) {
            LlChartEmptyState(modifier = Modifier.fillMaxWidth().height(ChartDefaults.defaultHeight))
            return@Box
        }

        val allValues = series.flatMap { it.values.take(pointCount) }
        val range = ChartMath.valueRange(allValues, includeZero = true)
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
                        onPointSelected(0, index)
                    }
                },
        ) {
            drawHorizontalGridlines(
                yFractions = ticks.map { ChartMath.normalize(it, range) },
                color = gridColor,
                strokeWidthPx = px(ChartDefaults.gridLineWidth),
            )
            drawZeroBaseline(range, MaterialTheme.colorScheme.outline, px(ChartDefaults.axisLineWidth))
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
                series.forEach { s ->
                    val values = s.values.take(pointCount)
                    if (values.isEmpty()) return@forEach

                    val linePath = Path()
                    val areaPath = if (showArea) Path() else null
                    values.forEachIndexed { index, value ->
                        val x = size.width * xPositions[index]
                        val y = size.height * (1f - ChartMath.normalize(value, range))
                        if (index == 0) {
                            linePath.moveTo(x, y)
                            areaPath?.moveTo(x, size.height)
                            areaPath?.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            areaPath?.lineTo(x, y)
                        }
                    }
                    if (showArea && areaPath != null) {
                        areaPath.lineTo(size.width * xPositions.last(), size.height)
                        areaPath.close()
                        drawPath(path = areaPath, color = s.color.copy(alpha = ChartDefaults.AREA_FILL_ALPHA))
                    }
                    drawPath(
                        path = linePath,
                        color = s.color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = px(ChartDefaults.lineStrokeWidth),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round,
                        ),
                    )
                    values.forEachIndexed { index, value ->
                        val x = size.width * xPositions[index]
                        val y = size.height * (1f - ChartMath.normalize(value, range))
                        drawCircle(color = s.color, radius = px(ChartDefaults.pointRadius), center = Offset(x, y))
                    }
                }
            }
        }
    }
}

/** `₹1.2L`-style compact axis label without pulling in [com.lifeledger.core.model.Money]
 *  (an axis tick is a raw major-unit double, not money in minor units). */
internal fun formatAxisValue(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return sign + when {
        abs >= 10_000_000 -> "%.1fCr".format(abs / 10_000_000.0)
        abs >= 100_000 -> "%.1fL".format(abs / 100_000.0)
        abs >= 1_000 -> "%.0fK".format(abs / 1_000.0)
        else -> "%.0f".format(abs)
    }
}
