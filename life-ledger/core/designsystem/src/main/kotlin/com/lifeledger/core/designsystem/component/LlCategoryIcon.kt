package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.designsystem.theme.categoricalColorFor
import com.lifeledger.core.model.TxnCategory

/**
 * A tinted circle carrying a [TxnCategory]'s icon — the small visual anchor every
 * transaction row, category legend and filter chip uses to identify a category at a
 * glance without reading its label.
 */
@Composable
fun LlCategoryIcon(
    category: TxnCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val tint = categoricalColorFor(category.name, LlTheme.colors.categorical)
    Box(
        modifier = modifier
            .size(size)
            .background(color = tint.copy(alpha = 0.16f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = LlIcons.forCategory(category),
            contentDescription = category.displayName,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
