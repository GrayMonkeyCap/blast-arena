package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LlTheme

/** A single named action rendered as the primary button under an empty/error illustration. */
data class LlStateAction(val label: String, val onClick: () -> Unit)

/**
 * The "nothing here yet" screen — first-run states, empty search results, a filter that
 * matched nothing. Every list screen in the app should end up here rather than showing a
 * blank list, so the icon and copy are required, not optional extras.
 */
@Composable
fun LlEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: LlStateAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LlTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LlTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Button(onClick = action.onClick, modifier = Modifier.padding(top = LlTheme.spacing.xs)) {
                Text(action.label)
            }
        }
    }
}

/** Default empty-state icon for lists with no built-in concept of their own — search
 *  results, generic filters — so call sites don't have to pick one. */
val LlDefaultEmptyIcon: ImageVector get() = LlIcons.Empty
