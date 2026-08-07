package com.lifeledger.core.ui.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * A minimal, axis-free trend line for tight spaces — a stat tile's "last 30 days", a
 * subscription row's charge history. No gridlines, no labels, no interaction: if a screen
 * needs any of those it should reach for [LlLineChart] instead.
 */
@Composable
fun LlSparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    showLastPointDot: Boolean = true,
) {
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = ChartDefaults.drawInSpec)
    }

    Canvas(modifier = modifier.fillMaxWidth().height(ChartDefaults.sparklineHeight)) {
        if (values.size < 2) {
            // A flat dot rather than nothing: a single-point history is still "we have data".
            if (values.size == 1) {
                drawCircle(color = color, radius = px(ChartDefaults.pointRadius), center = Offset(size.width / 2f, size.height / 2f))
            }
            return@Canvas
        }

        val range = ChartMath.valueRange(values, includeZero = false)
        val xPositions = ChartMath.xPositions(values.size)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * xPositions[index]
            val y = size.height * (1f - ChartMath.normalize(value, range))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        clipRect(right = size.width * progress.value) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = px(ChartDefaults.lineStrokeWidth) * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        if (showLastPointDot && progress.value > 0.99f) {
            val lastX = size.width * xPositions.last()
            val lastY = size.height * (1f - ChartMath.normalize(values.last(), range))
            drawCircle(color = color, radius = px(ChartDefaults.pointRadius) * 0.8f, center = Offset(lastX, lastY))
        }
    }
}
