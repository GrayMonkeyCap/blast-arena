package com.lifeledger.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.lifeledger.core.common.format.MoneyFormatter
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.designsystem.theme.numeric
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money

/**
 * Renders a [Money] amount with the direction-appropriate colour and tabular numerals so a
 * column of amounts lines up — the one place every screen should reach for money text
 * instead of a plain `Text(MoneyFormatter.format(...))`.
 */
@Composable
fun LlAmountText(
    money: Money,
    modifier: Modifier = Modifier,
    direction: Direction = if (money.isNegative) Direction.DEBIT else Direction.CREDIT,
    compact: Boolean = false,
    showSign: Boolean = false,
) {
    val colors = LlTheme.colors
    val color = when (direction) {
        Direction.CREDIT -> colors.incomeGreen
        Direction.DEBIT -> colors.expenseRed
        Direction.NEUTRAL -> LocalContentColor.current
    }
    val text = if (compact) MoneyFormatter.compact(money) else MoneyFormatter.format(money, showSign = showSign)
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.numeric,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
