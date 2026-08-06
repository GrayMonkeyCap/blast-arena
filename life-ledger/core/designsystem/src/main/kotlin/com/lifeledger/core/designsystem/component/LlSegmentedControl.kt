package com.lifeledger.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Single-choice segmented control — [com.lifeledger.core.model.PeriodGranularity] pickers,
 * income/expense/net toggles, list/grid switches. Thin wrapper over M3's segmented row so
 * every screen gets the same spacing and label style for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LlSegmentedControl(
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(labelFor(option)) },
            )
        }
    }
}
