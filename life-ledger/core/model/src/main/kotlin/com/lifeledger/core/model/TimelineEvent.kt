package com.lifeledger.core.model

import java.time.Instant

/**
 * One entry in the unified life timeline.
 *
 * Timeline events are deliberately decoupled from transactions: the SMS engine is only the
 * first data source, and later sources (email, receipts, health, calendar) will write into
 * this same stream without any downstream change. [sourceKind] plus [sourceId] identify
 * where an event came from.
 */
data class TimelineEvent(
    val id: Long = 0,
    val type: TimelineEventType,
    val occurredAt: Instant,
    val title: String,
    val subtitle: String? = null,
    val amount: Money? = null,
    val direction: Direction = Direction.NEUTRAL,
    val category: TxnCategory? = null,
    val merchantName: String? = null,
    val iconHint: String? = null,
    val sourceKind: SourceKind = SourceKind.SMS,
    /** Row id within the owning table for [sourceKind] — e.g. a transaction id. */
    val sourceId: Long? = null,
    val transactionId: Long? = null,
    val isPinned: Boolean = false,
    val tagIds: List<Long> = emptyList(),
)

/** A timeline slice already grouped by day, which is how every timeline UI consumes it. */
data class TimelineDay(
    val date: java.time.LocalDate,
    val events: List<TimelineEvent>,
    val totalIn: Money,
    val totalOut: Money,
) {
    val net: Money get() = totalIn - totalOut
}
