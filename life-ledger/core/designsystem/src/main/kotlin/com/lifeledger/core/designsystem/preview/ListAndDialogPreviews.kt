package com.lifeledger.core.designsystem.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.component.LlAmountText
import com.lifeledger.core.designsystem.component.LlCategoryIcon
import com.lifeledger.core.designsystem.component.LlConfirmDialog
import com.lifeledger.core.designsystem.component.LlListItem
import com.lifeledger.core.designsystem.component.LlPullToRefreshBox
import com.lifeledger.core.designsystem.theme.LifeLedgerTheme
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.TxnCategory

@LightDarkPreview
@Composable
private fun LlListItemPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlListItem(
            title = "Swiggy",
            subtitle = "Food & Dining · UPI",
            leading = { LlCategoryIcon(category = TxnCategory.FOOD) },
            trailing = { LlAmountText(money = Money.ofMajor(-482)) },
        )
    }
}

@LightDarkPreview
@Composable
private fun LlConfirmDialogPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlConfirmDialog(
            title = "Delete transaction?",
            message = "This can't be undone.",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
        )
    }
}

@LightDarkPreview
@Composable
private fun LlPullToRefreshBoxPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlPullToRefreshBox(
            isRefreshing = false,
            onRefresh = {},
            modifier = Modifier.fillMaxWidth().height(120.dp),
        ) {
            LlListItem(title = "Pull down to refresh")
        }
    }
}
