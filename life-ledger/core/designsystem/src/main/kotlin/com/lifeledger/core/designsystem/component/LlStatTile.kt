package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LlTheme

/** Direction of a [LlStatTile] delta, decoupled from [com.lifeledger.core.model.Direction]
 *  because a delta being "up" isn't always good news (e.g. spend going up is bad). */
enum class DeltaSentiment { POSITIVE, NEGATIVE, NEUTRAL }

/** A single labelled number with an optional period-over-period delta — the building block
 *  of every dashboard summary row ("This month", "Savings rate", "Net worth"). */
@Composable
fun LlStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaSentiment: DeltaSentiment = DeltaSentiment.NEUTRAL,
) {
    LlCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(LlTheme.spacing.xxs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (delta != null) {
                val colors = LlTheme.colors
                val (icon, tint) = when (deltaSentiment) {
                    DeltaSentiment.POSITIVE -> LlIcons.Income to colors.incomeGreen
                    DeltaSentiment.NEGATIVE -> LlIcons.Expense to colors.expenseRed
                    DeltaSentiment.NEUTRAL -> null to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = delta,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}
