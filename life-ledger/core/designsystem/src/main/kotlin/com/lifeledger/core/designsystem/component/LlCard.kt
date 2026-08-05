package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.lifeledger.core.designsystem.theme.Elevation
import com.lifeledger.core.designsystem.theme.LlTheme

/**
 * The one surface every grouped block of content in Life Ledger sits on — transactions
 * lists, stat tiles, insight cards. A thin wrapper over [Surface] rather than M3's `Card`
 * so tap ripple, elevation and corner radius stay consistent without every call site
 * repeating `CardDefaults`.
 */
@Composable
fun LlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tonal: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(LlTheme.spacing.md),
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val containerColor = if (tonal) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        color = containerColor,
        tonalElevation = if (tonal) Elevation.level0 else Elevation.level1,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
