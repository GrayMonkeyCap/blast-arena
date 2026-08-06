package com.lifeledger.core.ui.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lifeledger.core.ui.R

/**
 * The "not enough data yet" placeholder every chart in this package falls back to instead
 * of drawing an empty or divide-by-zero canvas — a brand-new install has no transactions
 * on day one, and this is what the dashboard shows until it does.
 */
@Composable
internal fun LlChartEmptyState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.ll_chart_no_data),
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
