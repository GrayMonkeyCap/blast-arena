package com.lifeledger.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifeledger.core.designsystem.theme.LlTheme

/**
 * Standard modal bottom sheet chrome — optional title, consistent padding — for the many
 * transient pickers Life Ledger uses instead of full screens (filters, date ranges, quick
 * edits). Wraps [ModalBottomSheet] so call sites don't each re-derive sheet state and
 * padding by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlBottomSheetScaffold(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = LlTheme.spacing.md, vertical = LlTheme.spacing.sm),
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(LlTheme.spacing.md))
            }
            content()
        }
    }
}
