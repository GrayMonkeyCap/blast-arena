package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * One entry in the unified life timeline.
 *
 * This table is deliberately a denormalised projection rather than a view over transactions:
 * SMS is only the first source, and email, receipts, calendar and health events will write
 * here without any downstream change. The cost is that display fields are copied; the benefit
 * is that the timeline reads one index and never unions across feature tables.
 */
@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            // A timeline entry describing a deleted transaction would be a lie; rows that
            // are not about a transaction hold NULL here and are untouched.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["type", "occurredAt"]),
        Index(value = ["transactionId"]),
        Index(value = ["sourceKind", "sourceId"]),
        Index(value = ["isPinned"]),
        Index(value = ["category"]),
        Index(value = ["merchantName"]),
    ],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Name of `TimelineEventType`. */
    val type: String,
    val occurredAt: Long,
    val title: String,
    val subtitle: String? = null,
    val amountMinor: Long? = null,
    val currency: String = Money.INR,
    /** Name of `Direction`. */
    val direction: String,
    /** Name of `TxnCategory`, or null for events that carry no spending meaning. */
    val category: String? = null,
    val merchantName: String? = null,
    val iconHint: String? = null,
    /** Name of `SourceKind`. */
    val sourceKind: String,
    /** Row id within the table that owns [sourceKind]. */
    val sourceId: Long? = null,
    val transactionId: Long? = null,
    val isPinned: Boolean = false,
)
