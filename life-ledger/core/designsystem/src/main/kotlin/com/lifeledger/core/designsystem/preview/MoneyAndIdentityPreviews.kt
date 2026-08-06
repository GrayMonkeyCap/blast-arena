package com.lifeledger.core.designsystem.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.component.DeltaSentiment
import com.lifeledger.core.designsystem.component.LlAmountText
import com.lifeledger.core.designsystem.component.LlCategoryIcon
import com.lifeledger.core.designsystem.component.LlMerchantAvatar
import com.lifeledger.core.designsystem.component.LlStatTile
import com.lifeledger.core.designsystem.theme.LifeLedgerTheme
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.TxnCategory

@LightDarkPreview
@Composable
private fun LlAmountTextPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        Row(modifier = Modifier.padding(16.dp)) {
            LlAmountText(money = Money.ofMajor(-482))
            LlAmountText(money = Money.ofMajor(50000), modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@LightDarkPreview
@Composable
private fun LlCategoryIconPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        Row(modifier = Modifier.padding(16.dp)) {
            LlCategoryIcon(category = TxnCategory.FOOD)
            LlCategoryIcon(category = TxnCategory.INVESTMENTS, modifier = Modifier.padding(start = 8.dp))
            LlCategoryIcon(category = TxnCategory.TRAVEL, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@LightDarkPreview
@Composable
private fun LlMerchantAvatarPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        Row(modifier = Modifier.padding(16.dp)) {
            LlMerchantAvatar(name = "Amazon Pay")
            LlMerchantAvatar(name = "Swiggy", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@LightDarkPreview
@Composable
private fun LlStatTilePreview() {
    LifeLedgerTheme(dynamicColor = false) {
        LlStatTile(
            label = "This month",
            value = "₹42,180",
            delta = "12% vs last month",
            deltaSentiment = DeltaSentiment.NEGATIVE,
            modifier = Modifier.padding(16.dp),
        )
    }
}
