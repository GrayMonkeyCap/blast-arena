package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * A locally computed observation.
 *
 * [dedupeKey] is unique because generators are re-run on a schedule: without it, "you spent
 * 30% more on food in March" would be re-inserted every run and the feed would become a pile
 * of identical cards. Upserting on the key refreshes the numbers and — crucially — preserves
 * [isDismissed], so a finding the user has waved away stays away.
 */
@Entity(
    tableName = "insights",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["isDismissed", "generatedAt"]),
        Index(value = ["generatedAt"]),
        Index(value = ["kind"]),
        Index(value = ["isPinned"]),
        Index(value = ["merchantId"]),
    ],
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dedupeKey: String,
    /** Name of `InsightKind`. */
    val kind: String,
    /** Name of `InsightSeverity`. */
    val severity: String,
    val title: String,
    val body: String,
    val generatedAt: Long,
    /** Epoch day. */
    val periodStart: Long? = null,
    /** Epoch day. */
    val periodEnd: Long? = null,
    /** Name of `TxnCategory`. */
    val category: String? = null,
    val merchantId: Long? = null,
    val amountMinor: Long? = null,
    val currency: String = Money.INR,
    val changePercent: Double? = null,
    /** The numbers behind the sentence, so the detail sheet can show its working. */
    val evidence: Map<String, String> = emptyMap(),
    val relatedTransactionIds: List<Long> = emptyList(),
    val isDismissed: Boolean = false,
    val isPinned: Boolean = false,
)
