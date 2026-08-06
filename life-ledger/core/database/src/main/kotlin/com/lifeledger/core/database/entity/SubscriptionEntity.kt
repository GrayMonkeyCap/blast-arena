package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * A recurring charge the detector found by spotting a repeating merchant/amount rhythm.
 *
 * [previousAmountMinor] and [priceChangedAt] exist so a price rise is a first-class fact
 * rather than something the user has to notice; the detector overwrites [amountMinor] and
 * moves the old value here when the cadence survives an amount change.
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["status", "nextExpectedAt"]),
        Index(value = ["merchantId"]),
        Index(value = ["nextExpectedAt"]),
        Index(value = ["name"]),
        Index(value = ["category"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val merchantId: Long? = null,
    val amountMinor: Long,
    val currency: String = Money.INR,
    /** Name of `Recurrence`. */
    val recurrence: String,
    /** Name of `TxnCategory`. */
    val category: String,
    val firstChargedAt: Long? = null,
    val lastChargedAt: Long? = null,
    /** Epoch day. */
    val nextExpectedAt: Long? = null,
    val chargeCount: Int = 0,
    /** Name of `Subscription.Status`. */
    val status: String,
    val confidence: Float = 0.6f,
    /** Once true the detector stops second-guessing this row. */
    val userConfirmed: Boolean = false,
    val notes: String? = null,
    val previousAmountMinor: Long? = null,
    val priceChangedAt: Long? = null,
)
