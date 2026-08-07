package com.lifeledger.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.model.PeriodGranularity
import com.lifeledger.core.ui.R

/**
 * The `< This month >` control every period-scoped screen (dashboard, analytics,
 * transactions) uses to page through time at a fixed [PeriodGranularity]. Granularity
 * itself is chosen elsewhere (e.g. [com.lifeledger.core.designsystem.component.LlSegmentedControl]);
 * this widget only moves the window forward and back.
 */
@Composable
fun PeriodSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isNextEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = LlTheme.spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(imageVector = LlIcons.Back, contentDescription = stringResource(R.string.ll_period_previous_cd))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = LlTheme.spacing.sm),
        )
        IconButton(onClick = onNext, enabled = isNextEnabled) {
            // Reuses the shared back-arrow glyph rotated 180° so "next" doesn't need its
            // own entry in LlIcons for what is visually the same chevron.
            Icon(
                imageVector = LlIcons.Back,
                contentDescription = stringResource(R.string.ll_period_next_cd),
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

/** Localised label for a [PeriodGranularity] value, for use in filter chips and pickers. */
@Composable
fun PeriodGranularity.label(): String = when (this) {
    PeriodGranularity.DAY -> stringResource(R.string.ll_period_day)
    PeriodGranularity.WEEK -> stringResource(R.string.ll_period_week)
    PeriodGranularity.MONTH -> stringResource(R.string.ll_period_month)
    PeriodGranularity.QUARTER -> stringResource(R.string.ll_period_quarter)
    PeriodGranularity.YEAR -> stringResource(R.string.ll_period_year)
}
