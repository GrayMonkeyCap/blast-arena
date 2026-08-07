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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawHorizontalGridlines
import com.lifeledger.core.ui.chart.ChartAxis.drawXAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawYAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawZeroBaseline
import kotlin.math.min
import kotlinx.collections.immutable.ImmutableList

/** One step of an [LlWaterfallChart] — a cash-flow contributor such as "Salary" (+) or
 *  "Rent" (-). [isTotal] renders it as a full-height anchor bar (opening/closing balance)
 *  rather than a floating delta bar. */
data class LlWaterfallStep(
    val label: String,
    val delta: Double,
    val isTotal: Boolean = false,
)

/**
 * A cash-flow waterfall: each bar floats from the running total before it to the running
 * total after it, coloured by whether that step added or removed money — "how did I get
 * from last month's balance to this month's".
 */
@Composable
fun LlWaterfallChart(
    steps: ImmutableList<LlWaterfallStep>,
    modifier: Modifier = Modifier,
    positiveColor: Color = Color.Unspecified,
    negativeColor: Color = Color.Unspecified,
    totalColor: Color = Color.Unspecified,
    onPointSelected: ((stepIndex: Int) -> Unit)? = null,
) {
    val resolvedPositive = positiveColor.takeOrElse { MaterialTheme.colorScheme.primary }
    val resolvedNegative = negativeColor.takeOrElse { MaterialTheme.colorScheme.error }
    val resolvedTotal = totalColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ChartDefaults.GRID_LINE_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    val progress = remember(steps) { Animatable(0f) }
    LaunchedEffect(steps) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = ChartDefaults.drawInSpec)
    }

    Box(modifier = modifier.fillMaxWidth().height(ChartDefaults.defaultHeight)) {
        if (steps.isEmpty()) {
            LlChartEmptyState(modifier = Modifier.fillMaxWidth().height(ChartDefaults.defaultHeight))
            return@Box
        }

        val effectiveValues = mutableListOf<Double>()
        var running = 0.0
        val bounds = steps.map { step ->
            val startValue = if (step.isTotal) 0.0 else running
            val endValue = if (step.isTotal) step.delta else running + step.delta
            running = endValue
            effectiveValues += startValue
            effectiveValues += endValue
            startValue to endValue
        }
        val range = ChartMath.valueRange(effectiveValues, includeZero = true)
        val ticks = ChartMath.niceTicks(range)
        val slotCenters = ChartMath.barLayout(steps.size)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartDefaults.defaultHeight)
                .padding(start = 40.dp, bottom = 20.dp)
                .pointerInput(steps) {
                    if (onPointSelected == null) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val nearest = slotCenters.indices.minByOrNull { kotlin.math.abs(slotCenters[it] - fraction) }
                        nearest?.let(onPointSelected)
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
            drawXAxisLabels(
                textMeasurer = textMeasurer,
                labels = steps.map { it.label },
                style = labelStyle,
                color = labelColor,
                topPx = size.height + px(ChartDefaults.axisLabelPadding),
            )

            val slotWidth = size.width / steps.size
            val barWidth = slotWidth * 0.55f

            steps.forEachIndexed { index, step ->
                val (startValue, endValue) = bounds[index]
                val color = when {
                    step.isTotal -> resolvedTotal
                    step.delta >= 0.0 -> resolvedPositive
                    else -> resolvedNegative
                }
                val startY = size.height * (1f - ChartMath.normalize(startValue, range))
                val endY = size.height * (1f - ChartMath.normalize(endValue, range))
                val centerX = size.width * slotCenters[index]
                drawWaterfallBar(
                    left = centerX - barWidth / 2f,
                    width = barWidth,
                    fromY = startY,
                    toY = endY,
                    progress = progress.value,
                    color = color,
                )

                // A thin connector to the next bar's start makes the "running total"
                // narrative readable without a label on every single step.
                if (index < steps.lastIndex) {
                    val nextCenterX = size.width * slotCenters[index + 1]
                    drawLine(
                        color = gridColor,
                        start = Offset(centerX + barWidth / 2f, endY),
                        end = Offset(nextCenterX - barWidth / 2f, endY),
                        strokeWidth = px(ChartDefaults.gridLineWidth),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawWaterfallBar(
    left: Float,
    width: Float,
    fromY: Float,
    toY: Float,
    progress: Float,
    color: Color,
) {
    val animatedToY = fromY + (toY - fromY) * progress
    val top = min(fromY, animatedToY)
    val height = kotlin.math.abs(fromY - animatedToY)
    if (height <= 0f) return
    val radius = min(px(ChartDefaults.barCornerRadius), height / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
    )
}
