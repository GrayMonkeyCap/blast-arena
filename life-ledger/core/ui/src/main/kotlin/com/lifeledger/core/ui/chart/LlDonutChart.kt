package com.lifeledger.core.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawCenteredLabel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.collections.immutable.ImmutableList

/** One wedge of an [LlDonutChart] — a category and its share of the whole. */
data class LlDonutSlice(
    val label: String,
    val value: Double,
    val color: Color,
)

/**
 * A donut breakdown (category spend by share) with the total drawn in the centre — the
 * shape "Where did my money go this month" uses. Slices animate in as a clockwise sweep
 * from 12 o'clock, matching [LlProgressRing]'s convention so the two read as one family.
 */
@Composable
fun LlDonutChart(
    slices: ImmutableList<LlDonutSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    centerValue: String? = null,
    onPointSelected: ((sliceIndex: Int) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = ChartDefaults.drawInSpec)
    }

    val total = slices.sumOf { it.value }
    val positiveSlices = slices.filter { it.value > 0.0 }

    Box(
        modifier = modifier.size(ChartDefaults.defaultHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (positiveSlices.isEmpty() || total <= 0.0) {
            LlChartEmptyState()
            return@Box
        }

        Canvas(
            modifier = Modifier
                .size(ChartDefaults.defaultHeight)
                .pointerInput(slices) {
                    if (onPointSelected == null) return@pointerInput
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val radius = hypot(dx, dy)
                        val outerRadius = min(size.width, size.height) / 2f
                        val innerRadius = outerRadius - px(ChartDefaults.donutThickness)
                        if (radius < innerRadius || radius > outerRadius) return@detectTapGestures

                        // atan2 is 0° at 3 o'clock going counter-clockwise; slices are drawn
                        // clockwise from 12 o'clock, so rotate into that frame before matching.
                        val angleFromThreeOClock = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                        val angleFromTwelveClockwise = ((angleFromThreeOClock + 90.0) + 360.0) % 360.0

                        var cursor = 0.0
                        val index = positiveSlices.indexOfFirst { slice ->
                            val sweep = ChartMath.sweepAngle(slice.value, total)
                            val hit = angleFromTwelveClockwise in cursor..(cursor + sweep)
                            cursor += sweep
                            hit
                        }
                        if (index >= 0) onPointSelected(index)
                    }
                },
        ) {
            val strokePx = px(ChartDefaults.donutThickness)
            val diameter = min(size.width, size.height) - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            positiveSlices.forEach { slice ->
                val sweep = ChartMath.sweepAngle(slice.value, total) * progress.value
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                )
                startAngle += ChartMath.sweepAngle(slice.value, total)
            }

            if (centerValue != null) {
                drawCenteredLabel(
                    textMeasurer = textMeasurer,
                    text = centerValue,
                    position = Offset(size.width / 2f, size.height / 2f - (if (centerLabel != null) 10.dp.toPx() else 0f)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (centerLabel != null) {
                drawCenteredLabel(
                    textMeasurer = textMeasurer,
                    text = centerLabel,
                    position = Offset(size.width / 2f, size.height / 2f + 14.dp.toPx()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
