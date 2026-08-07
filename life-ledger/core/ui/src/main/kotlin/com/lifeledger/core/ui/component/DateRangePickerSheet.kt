package com.lifeledger.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lifeledger.core.designsystem.component.LlBottomSheetScaffold
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.model.DateRange
import com.lifeledger.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A bottom-sheet-hosted date range picker for the many "custom range" filters across
 * transactions/analytics/search. Wraps M3's [DateRangePicker] — which owns UTC-millis
 * epoch state — behind Life Ledger's [DateRange] so call sites never touch epoch millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerSheet(
    initialRange: DateRange?,
    onDismissRequest: () -> Unit,
    onRangeSelected: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.start?.toEpochMillis(),
        initialSelectedEndDateMillis = initialRange?.endInclusive?.toEpochMillis(),
    )

    LlBottomSheetScaffold(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.ll_date_range_title),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LlTheme.spacing.md),
        ) {
            DateRangePicker(state = state, modifier = Modifier.fillMaxWidth())

            val startMillis = state.selectedStartDateMillis
            val endMillis = state.selectedEndDateMillis
            Button(
                onClick = {
                    val start = startMillis?.toLocalDate()
                    val end = (endMillis ?: startMillis)?.toLocalDate()
                    if (start != null && end != null) {
                        onRangeSelected(DateRange(start, end))
                    }
                },
                enabled = startMillis != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ll_date_range_apply))
            }
        }
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
