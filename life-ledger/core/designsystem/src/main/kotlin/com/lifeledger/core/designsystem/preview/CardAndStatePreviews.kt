package com.lifeledger.core.designsystem.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.component.LlCard
import com.lifeledger.core.designsystem.component.LlEmptyState
import com.lifeledger.core.designsystem.component.LlErrorState
import com.lifeledger.core.designsystem.component.LlLoading
import com.lifeledger.core.designsystem.component.LlSectionHeader
import com.lifeledger.core.designsystem.component.LlStateAction
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LifeLedgerTheme

@LightDarkPreview
@Composable
private fun LlCardPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlCard(modifier = Modifier.padding(16.dp)) {
            Text("Swiggy · ₹482")
        }
    }
}

@LightDarkPreview
@Composable
private fun LlSectionHeaderPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlSectionHeader(
            title = "Recent transactions",
            subtitle = "Last 7 days",
            actionLabel = "See all",
            onActionClick = {},
        )
    }
}

@LightDarkPreview
@Composable
private fun LlEmptyStatePreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlEmptyState(
            icon = LlIcons.Empty,
            title = "No transactions yet",
            message = "Grant SMS access to start building your timeline.",
            action = LlStateAction("Grant access") {},
        )
    }
}

@LightDarkPreview
@Composable
private fun LlLoadingPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlLoading()
    }
}

@LightDarkPreview
@Composable
private fun LlErrorStatePreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlErrorState(
            title = "Couldn't read that message",
            message = "The parser didn't recognise this bank's format.",
            onRetry = {},
        )
    }
}
