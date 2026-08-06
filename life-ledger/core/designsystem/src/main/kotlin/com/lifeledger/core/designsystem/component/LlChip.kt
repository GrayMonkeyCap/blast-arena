package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.lifeledger.core.designsystem.theme.LlTheme

/** One selectable filter — a category, a payment method, a date preset. */
@Composable
fun LlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

/**
 * A horizontally scrolling row of [LlChip]s, the standard filter bar under a screen's
 * top app bar. [selectedId] drives single-select filtering; pass a set-backed [onSelect]
 * from the caller for multi-select instead.
 */
@Composable
fun <T> LlFilterChipRow(
    items: List<T>,
    selectedId: T?,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    iconFor: ((T) -> ImageVector?)? = null,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = LlTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(LlTheme.spacing.xs),
    ) {
        items(items) { item ->
            LlChip(
                label = labelFor(item),
                selected = item == selectedId,
                onClick = { onSelect(item) },
                leadingIcon = iconFor?.invoke(item),
            )
        }
    }
}
