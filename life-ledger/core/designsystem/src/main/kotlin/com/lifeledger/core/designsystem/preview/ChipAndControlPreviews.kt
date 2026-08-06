package com.lifeledger.core.designsystem.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.designsystem.component.LlChip
import com.lifeledger.core.designsystem.component.LlFilterChipRow
import com.lifeledger.core.designsystem.component.LlSearchBar
import com.lifeledger.core.designsystem.component.LlSegmentedControl
import com.lifeledger.core.designsystem.theme.LifeLedgerTheme
import com.lifeledger.core.model.PeriodGranularity

@LightDarkPreview
@Composable
private fun LlChipPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        var selected by remember { mutableStateOf(true) }
        LlChip(label = "Food & Dining", selected = selected, onClick = { selected = !selected }, modifier = Modifier.padding(16.dp))
    }
}

@LightDarkPreview
@Composable
private fun LlFilterChipRowPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        var selected by remember { mutableStateOf(PeriodGranularity.MONTH) }
        LlFilterChipRow(
            items = PeriodGranularity.entries,
            selectedId = selected,
            labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            onSelect = { selected = it },
        )
    }
}

@LightDarkPreview
@Composable
private fun LlSegmentedControlPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        var selected by remember { mutableStateOf("Expense") }
        LlSegmentedControl(
            options = listOf("Income", "Expense", "Net"),
            selected = selected,
            labelFor = { it },
            onSelect = { selected = it },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@LightDarkPreview
@Composable
private fun LlSearchBarPreview() {
    LifeLedgerTheme(dynamicColor = false) {
        var query by remember { mutableStateOf("swiggy") }
        LlSearchBar(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(16.dp))
    }
}
