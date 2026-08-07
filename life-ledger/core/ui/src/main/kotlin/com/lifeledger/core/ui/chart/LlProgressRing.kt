package com.lifeledger.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeledger.core.ui.chart.ChartAxis.drawCenteredLabel
import kotlin.math.min

/**
 * A single-value progress ring — a budget's "spent so far", a savings goal's completion.
 * [progress] is clamped to `0f..1.5f` internally so an over-budget value still reads as
 * "very full" rather than wrapping back to a near-empty ring.
 */
@Composable
fun LlProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = ChartDefaults.defaultHeight,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    overflowColor: Color = MaterialTheme.colorScheme.error,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val clamped = progress.coerceIn(0f, 1.5f)
    val animated by animateFloatAsState(targetValue = clamped, animationSpec = ChartDefaults.drawInSpec, label = "progressRing")
    val textMeasurer = rememberTextMeasurer()
    val ringColor = if (clamped > 1f) overflowColor else color

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = px(ChartDefaults.progressRingThickness)
            val diameter = min(this.size.width, this.size.height) - strokePx
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val sweep = 360f * min(animated, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            // Overshoot past 100% draws a second, inset ring rather than a second lap of
            // the same track, so "150% of budget" is visually distinct from "50%".
            if (animated > 1f) {
                val overshootStroke = strokePx * 0.5f
                val overshootInset = strokePx * 0.75f
                drawArc(
                    color = overflowColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (min(animated, 1.5f) - 1f) * 2f,
                    useCenter = false,
                    topLeft = Offset(topLeft.x + overshootInset, topLeft.y + overshootInset),
                    size = Size(diameter - overshootInset * 2, diameter - overshootInset * 2),
                    style = Stroke(width = overshootStroke, cap = StrokeCap.Round),
                )
            }

            if (centerValue != null) {
                drawCenteredLabel(
                    textMeasurer = textMeasurer,
                    text = centerValue,
                    position = Offset(this.size.width / 2f, this.size.height / 2f - (if (centerLabel != null) 10.dp.toPx() else 0f)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (centerLabel != null) {
                drawCenteredLabel(
                    textMeasurer = textMeasurer,
                    text = centerLabel,
                    position = Offset(this.size.width / 2f, this.size.height / 2f + 14.dp.toPx()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
