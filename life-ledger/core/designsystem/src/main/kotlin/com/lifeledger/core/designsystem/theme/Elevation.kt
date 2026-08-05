package com.lifeledger.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Life Ledger keeps elevation shallow on purpose — the paper/ink palette already implies
 * depth through colour, so heavy shadows would look like a generic Material app again.
 */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp
}
