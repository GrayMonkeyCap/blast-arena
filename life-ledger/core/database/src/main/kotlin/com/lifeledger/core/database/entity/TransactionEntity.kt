package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * The central table. Everything the app shows is either a row here or an aggregate of rows
 * here, so its indices are chosen for the read patterns rather than for tidiness.
 *
 * Enum-valued columns hold the enum's `name` as TEXT instead of an ordinal: ordinals silently
 * change meaning when someone reorders an enum, and TEXT keeps the exported schema, the
 * dynamic query builder and any manual `sqlite3` session readable.
 *
 * Money is split into [amountMinor] + [currency] rather than stored as a formatted string so
 * that SUM() is exact and free of rounding.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            // Losing a merchant must not lose the spend it explains; the row keeps its
            // denormalised merchantName and simply becomes unlinked.
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SmsEntity::class,
            parentColumns = ["id"],
            childColumns = ["smsId"],
            // Inbox retention pruning must never cascade into the ledger.
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["duplicateOfId"],
            // Deleting the survivor of a duplicate pair un-hides the copy rather than
            // deleting the user's only remaining record of the event.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        // Every list, range and chart is ordered by time; this is the one index that must
        // never be dropped.
        Index(value = ["occurredAt"]),
        // Cash-flow style aggregates are equality-on-direction plus a range on time, which
        // only a composite in this order can serve end to end.
        Index(value = ["direction", "occurredAt"]),
        Index(value = ["category", "occurredAt"]),
        Index(value = ["type", "occurredAt"]),
        Index(value = ["merchantId", "occurredAt"]),
        Index(value = ["accountId", "occurredAt"]),
        Index(value = ["merchantName"]),
        Index(value = ["amountMinor"]),
        Index(value = ["dedupeHash"]),
        Index(value = ["duplicateOfId"]),
        Index(value = ["smsId"]),
        Index(value = ["paymentMethod"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountMinor: Long,
    val currency: String = Money.INR,

    /** Name of `TransactionType`. */
    val type: String,
    /** Name of `Direction`. Every non-financial type is NEUTRAL, so aggregates can filter
     *  on this column alone and still exclude OTPs and delivery notices. */
    val direction: String,
    /** Name of `TxnCategory`. */
    val category: String,
    val subcategory: String? = null,
    /** Name of `PaymentMethod`. */
    val paymentMethod: String,

    /** Epoch millis. The hottest column in the schema. */
    val occurredAt: Long,

    val merchantId: Long? = null,
    val merchantName: String? = null,
    val rawMerchant: String? = null,
    val merchantConfidence: Float = 0f,

    val accountId: Long? = null,
    val maskedAccount: String? = null,
    val bankCode: String? = null,
    val upiId: String? = null,

    val balanceAfterMinor: Long? = null,

    val referenceNumber: String? = null,
    val transactionId: String? = null,

    val description: String? = null,
    val notes: String? = null,

    val smsId: Long? = null,
    val parserId: String? = null,
    val parserConfidence: Float = 0f,
    /** Name of `SourceKind`. */
    val source: String,

    val userVerified: Boolean = false,
    val excludedFromStats: Boolean = false,

    /** Non-null on the losing side of a duplicate pair; excluded from every total. */
    val duplicateOfId: Long? = null,

    val createdAt: Long = 0,
    val updatedAt: Long = 0,

    /** Content hash of the fields that identify an event, so the deduper can look for an
     *  exact repeat with a single indexed lookup instead of a windowed scan. */
    val dedupeHash: String? = null,

    /**
     * Lowercased concatenation of every human-readable field, kept only to feed
     * `transactions_fts`. It is derived state: rebuild it with [searchBlobOf] whenever any
     * contributing column changes, otherwise search silently goes stale.
     */
    val searchBlob: String,
) {
    companion object {
        /** The single definition of what "searchable text" means for a transaction. */
        fun searchBlobOf(
            merchantName: String?,
            rawMerchant: String?,
            description: String?,
            notes: String?,
            category: String,
            type: String,
        ): String = listOfNotNull(merchantName, rawMerchant, description, notes, category, type)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .lowercase()
    }
}
