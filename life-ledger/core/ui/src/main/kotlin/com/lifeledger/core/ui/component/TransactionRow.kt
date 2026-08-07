package com.lifeledger.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifeledger.core.common.format.DateTimeFormatters
import com.lifeledger.core.designsystem.component.LlAmountText
import com.lifeledger.core.designsystem.component.LlCategoryIcon
import com.lifeledger.core.designsystem.component.LlListItem
import com.lifeledger.core.model.Transaction

/**
 * The row every transaction list in the app is built from — timeline, search results,
 * merchant/account detail screens. A category icon anchors it visually, the merchant or
 * description carries the story, and the amount is always coloured by direction so a list
 * reads as a strip of green/red without needing to parse each number.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onClick: ((Transaction) -> Unit)? = null,
) {
    LlListItem(
        title = transaction.displayTitle,
        subtitle = transaction.paymentMethod.name.takeIf { transaction.paymentMethod.name != "UNKNOWN" }
            ?.let { "${transaction.category.displayName} · ${DateTimeFormatters.time(transaction.occurredAt)}" }
            ?: transaction.category.displayName,
        leading = { LlCategoryIcon(category = transaction.category) },
        trailing = { LlAmountText(money = transaction.amount, direction = transaction.direction) },
        onClick = onClick?.let { { it(transaction) } },
        modifier = modifier,
    )
}
