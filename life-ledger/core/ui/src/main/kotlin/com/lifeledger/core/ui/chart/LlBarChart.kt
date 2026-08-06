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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawHorizontalGridlines
import com.lifeledger.core.ui.chart.ChartAxis.drawXAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawYAxisLabels
import com.lifeledger.core.ui.chart.ChartAxis.drawZeroBaseline
import kotlin.math.min
import kotlinx.collections.immutable.ImmutableList

/** One bar group — a month's income vs. expense, or one category's value per series. */
data class LlBarGroup(
    val label: String,
    /** One value per series, in the same order as the chart's series colours/labels. */
    val values: List<Double>,
)

/** Whether grouped bars sit side by side or stack into one column. */
enum class LlBarMode { GROUPED, STACKED }

/**
 * A grouped or stacked bar chart. The canonical use is income-vs-expense per period
 * ([LlBarMode.GROUPED] with two series), but it's general enough for any short categorical
 * comparison.
 */
@Composable
fun LlBarChart(
    groups: ImmutableList<LlBarGroup>,
    seriesColors: List<Color>,
    modifier: Modifier = Modifier,
    mode: LlBarMode = LlBarMode.GROUPED,
    onPointSelected: ((groupIndex: Int) -> Unit)? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ChartDefaults.GRID_LINE_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    val progress = remember(groups) { Animatable(0f) }
    LaunchedEffect(groups) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = ChartDefaults.drawInSpec)
    }

    Box(modifier = modifier.fillMaxWidth().height(ChartDefaults.defaultHeight)) {
        if (groups.isEmpty()) {
            LlChartEmptyState(modifier = Modifier.fillMaxWidth().height(ChartDefaults.defaultHeight))
            return@Box
        }

        val seriesCount = groups.maxOf { it.values.size }
        val allValues = when (mode) {
            LlBarMode.GROUPED -> groups.flatMap { it.values }
            LlBarMode.STACKED -> groups.map { it.values.sum() } + groups.flatMap { it.values }
        }
        val range = ChartMath.valueRange(allValues, includeZero = true)
        val ticks = ChartMath.niceTicks(range)
        val slotCenters = ChartMath.barLayout(groups.size)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartDefaults.defaultHeight)
                .padding(start = 40.dp, bottom = 20.dp)
                .pointerInput(groups) {
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
                labels = groups.map { it.label },
                style = labelStyle,
                color = labelColor,
                topPx = size.height + px(ChartDefaults.axisLabelPadding),
            )

            val zeroY = size.height * (1f - ChartMath.normalize(0.0, range))
            val slotWidth = size.width / groups.size
            val barWidth = when (mode) {
                LlBarMode.GROUPED -> (slotWidth * 0.7f) / seriesCount.coerceAtLeast(1)
                LlBarMode.STACKED -> slotWidth * 0.6f
            }

            groups.forEachIndexed { groupIndex, group ->
                val centerX = size.width * slotCenters[groupIndex]
                when (mode) {
                    LlBarMode.GROUPED -> {
                        val totalWidth = barWidth * group.values.size
                        var barX = centerX - totalWidth / 2f
                        group.values.forEachIndexed { seriesIndex, value ->
                            val color = seriesColors.getOrElse(seriesIndex) { MaterialTheme.colorScheme.primary }
                            drawBar(
                                left = barX,
                                zeroY = zeroY,
                                width = barWidth,
                                targetY = size.height * (1f - ChartMath.normalize(value, range)),
                                progress = progress.value,
                                color = color,
                            )
                            barX += barWidth
                        }
                    }
                    LlBarMode.STACKED -> {
                        // Stacked segments accumulate from the zero baseline outward by
                        // height rather than by absolute y, so each segment sits flush on
                        // top of the previous one regardless of sign.
                        var stackEdgeY = zeroY
                        group.values.forEachIndexed { seriesIndex, value ->
                            val color = seriesColors.getOrElse(seriesIndex) { MaterialTheme.colorScheme.primary }
                            val valueY = size.height * (1f - ChartMath.normalize(value, range))
                            val segmentHeight = kotlin.math.abs(zeroY - valueY)
                            val direction = if (valueY <= zeroY) -1f else 1f
                            val top = stackEdgeY + direction * segmentHeight
                            drawBar(
                                left = centerX - barWidth / 2f,
                                zeroY = stackEdgeY,
                                width = barWidth,
                                targetY = top,
                                progress = progress.value,
                                color = color,
                            )
                            stackEdgeY = top
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBar(
    left: Float,
    zeroY: Float,
    width: Float,
    targetY: Float,
    progress: Float,
    color: Color,
) {
    val animatedY = zeroY + (targetY - zeroY) * progress
    val top = min(animatedY, zeroY)
    val height = kotlin.math.abs(zeroY - animatedY)
    if (height <= 0f) return
    val radius = min(px(ChartDefaults.barCornerRadius), height / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
    )
}
