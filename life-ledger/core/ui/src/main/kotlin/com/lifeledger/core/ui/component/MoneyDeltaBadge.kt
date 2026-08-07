package com.lifeledger.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifeledger.core.common.format.MoneyFormatter
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.designsystem.theme.numeric
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money

/**
 * A small pill showing a signed amount — day totals, period-over-period deltas on a stat
 * card, a subscription's price change. Distinct from [com.lifeledger.core.designsystem.component.LlAmountText]
 * in that it always carries its own tinted background, for use where the amount needs to
 * stand apart from surrounding body text rather than sit inline with it.
 */
@Composable
fun MoneyDeltaBadge(
    money: Money,
    modifier: Modifier = Modifier,
    direction: Direction = if (money.isNegative) Direction.DEBIT else Direction.CREDIT,
    compact: Boolean = true,
) {
    val colors = LlTheme.colors
    val (background, content) = when (direction) {
        Direction.CREDIT -> colors.incomeGreen.copy(alpha = 0.14f) to colors.incomeGreen
        Direction.DEBIT -> colors.expenseRed.copy(alpha = 0.14f) to colors.expenseRed
        Direction.NEUTRAL -> colors.neutralGrey.copy(alpha = 0.14f) to colors.neutralGrey
    }
    val text = if (compact) MoneyFormatter.compact(money) else MoneyFormatter.format(money, showSign = true)

    Text(
        text = text,
        modifier = modifier
            .background(color = background, shape = RoundedCornerShape(50))
            .padding(horizontal = LlTheme.spacing.xs, vertical = LlTheme.spacing.xxs),
        color = content,
        style = MaterialTheme.typography.numeric.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
    )
}
