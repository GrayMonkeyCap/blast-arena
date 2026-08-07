package com.lifeledger.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifeledger.core.common.format.DateTimeFormatters
import com.lifeledger.core.designsystem.component.LlAmountText
import com.lifeledger.core.designsystem.component.LlCategoryIcon
import com.lifeledger.core.designsystem.component.LlListItem
import com.lifeledger.core.designsystem.icon.LlIcons
import com.lifeledger.core.designsystem.theme.LlTheme
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.TimelineEvent
import com.lifeledger.core.model.TimelineEventType

/**
 * One entry on the unified life timeline — a superset of [TransactionRow] that also
 * renders the non-financial event types (OTP, delivery, booking...) [TimelineEvent] can
 * carry, each with its own icon since they have no [com.lifeledger.core.model.TxnCategory].
 */
@Composable
fun TimelineEventRow(
    event: TimelineEvent,
    modifier: Modifier = Modifier,
    onClick: ((TimelineEvent) -> Unit)? = null,
) {
    LlListItem(
        title = event.title,
        subtitle = event.subtitle ?: DateTimeFormatters.time(event.occurredAt),
        leading = { TimelineEventIcon(event) },
        trailing = {
            if (event.amount != null && event.direction != Direction.NEUTRAL) {
                LlAmountText(money = event.amount, direction = event.direction)
            }
        },
        onClick = onClick?.let { { it(event) } },
        modifier = modifier,
    )
}

@Composable
private fun TimelineEventIcon(event: TimelineEvent) {
    if (event.category != null) {
        LlCategoryIcon(category = event.category)
        return
    }
    val icon = when (event.type) {
        TimelineEventType.TRANSACTION -> LlIcons.Money
        TimelineEventType.INVESTMENT -> LlIcons.Investment
        TimelineEventType.SUBSCRIPTION_CHARGE -> LlIcons.Subscription
        TimelineEventType.BILL_DUE, TimelineEventType.BILL_PAID -> LlIcons.Bill
        TimelineEventType.DELIVERY -> LlIcons.Delivery
        TimelineEventType.BOOKING -> LlIcons.Booking
        TimelineEventType.APPOINTMENT -> LlIcons.Appointment
        TimelineEventType.OTP -> LlIcons.Otp
        TimelineEventType.INSIGHT -> LlIcons.Analytics
        TimelineEventType.MILESTONE -> LlIcons.Success
        TimelineEventType.NOTE -> LlIcons.More
    }
    val tint = LlTheme.colors.neutralGrey
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = tint.copy(alpha = 0.16f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
