package com.lifeledger.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.lifeledger.core.model.DayIntensity
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

/**
 * A GitHub-style year-of-squares calendar: one column per week, one row per weekday, cell
 * shade driven by [DayIntensity.total] — the "how consistently do I spend/save" view that
 * a line chart can't show as immediately.
 */
@Composable
fun LlHeatmapCalendar(
    days: List<DayIntensity>,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onPointSelected: ((DayIntensity) -> Unit)? = null,
) {
    if (days.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(ChartDefaults.minHeight)) {
            LlChartEmptyState()
        }
        return
    }

    val byDate = days.associateBy { it.date }
    val start = days.minOf { it.date }
    val end = days.maxOf { it.date }
    // Weeks run Monday-first and the grid always starts on the Monday on/before [start] so
    // every column lines up regardless of what weekday the data happens to begin on.
    val gridStart = start.minusDays(((start.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7L) % 7L)
    val totalDays = ChronoUnit.DAYS.between(gridStart, end).toInt() + 1
    val weekCount = (totalDays + 6) / 7

    val maxIntensity = days.maxOf { it.total.asDouble.let { v -> if (v < 0) -v else v } }.coerceAtLeast(1.0)

    val cellSize = ChartDefaults.heatmapCellSize
    val gap = ChartDefaults.heatmapCellGap
    val corner = ChartDefaults.heatmapCellCorner

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((cellSize + gap) * 7)
            .pointerInput(days) {
                if (onPointSelected == null) return@pointerInput
                detectTapGestures { offset ->
                    val cellPx = px(cellSize) + px(gap)
                    val week = (offset.x / cellPx).toInt()
                    val weekday = (offset.y / cellPx).toInt()
                    if (week !in 0 until weekCount || weekday !in 0..6) return@detectTapGestures
                    val date = gridStart.plusDays(week * 7L + weekday)
                    byDate[date]?.let(onPointSelected)
                }
            },
    ) {
        val cellPx = px(cellSize)
        val gapPx = px(gap)
        val cornerPx = px(corner)
        val step = cellPx + gapPx

        for (week in 0 until weekCount) {
            for (weekday in 0..6) {
                val date = gridStart.plusDays(week * 7L + weekday)
                if (date.isBefore(start) || date.isAfter(end)) continue

                val intensity = byDate[date]
                val fraction = intensity?.let {
                    (kotlin.math.abs(it.total.asDouble) / maxIntensity).coerceIn(0.0, 1.0)
                } ?: 0.0
                val color = if (intensity == null || fraction == 0.0) {
                    emptyColor
                } else {
                    // Blend rather than just varying alpha, so the lightest "some spend"
                    // cell is still readably different from a truly empty day.
                    lerp(emptyColor, baseColor, (0.25 + fraction * 0.75).toFloat())
                }

                drawRoundRect(
                    color = color,
                    topLeft = Offset(week * step, weekday * step),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }
        }
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color = androidx.compose.ui.graphics.lerp(start, stop, fraction)
