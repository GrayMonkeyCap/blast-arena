package com.lifeledger.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lifeledger.core.common.format.DateTimeFormatters
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.TimelineDay

/**
 * The sticky-header row above each day's events in the timeline: `Today`/`Yesterday`/a
 * weekday for recency, plus the day's net so a scroller can spot a big day without opening
 * it. Designed to sit in [androidx.compose.foundation.lazy.LazyListScope.stickyHeader].
 */
@Composable
fun TimelineDayHeader(
    day: TimelineDay,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LlTheme.spacing.md, vertical = LlTheme.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateTimeFormatters.relativeDay(day.date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!day.net.isZero) {
            MoneyDeltaBadge(
                money = day.net,
                direction = if (day.net.isNegative) Direction.DEBIT else Direction.CREDIT,
            )
        }
    }
}
