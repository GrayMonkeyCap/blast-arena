package com.lifeledger.core.ui.chart

import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizing, stroke and animation tokens shared by every chart in this package, so a line
 * chart and a bar chart on the same dashboard read as one visual system rather than each
 * inventing its own stroke width and corner radius.
 */
object ChartDefaults {
    val minHeight: Dp = 160.dp
    val defaultHeight: Dp = 220.dp
    val sparklineHeight: Dp = 40.dp

    val axisLabelPadding: Dp = 4.dp
    val axisLineWidth: Dp = 1.dp
    val gridLineWidth: Dp = 1.dp

    val lineStrokeWidth: Dp = 2.5.dp
    val pointRadius: Dp = 4.dp
    val pointRadiusSelected: Dp = 6.dp

    val barCornerRadius: Dp = 4.dp
    val donutThickness: Dp = 28.dp
    val progressRingThickness: Dp = 10.dp

    val heatmapCellSize: Dp = 12.dp
    val heatmapCellGap: Dp = 3.dp
    val heatmapCellCorner: Dp = 2.dp

    /** Fraction of full opacity used for area/donut fills so gridlines stay legible under them. */
    const val AREA_FILL_ALPHA = 0.18f
    const val GRID_LINE_ALPHA = 0.35f
    const val DIMMED_ALPHA = 0.35f

    /** Every chart's first-composition draw-in uses this duration so a dashboard full of
     *  different chart types animates in as one coordinated moment, not a scatter of speeds. */
    const val DRAW_IN_DURATION_MS = 700
    val drawInSpec = tween<Float>(durationMillis = DRAW_IN_DURATION_MS)
}
