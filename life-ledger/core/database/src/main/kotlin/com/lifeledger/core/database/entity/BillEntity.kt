package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * A recurring bill and its next due date.
 *
 * [dueDateIsEstimated] is stored rather than inferred because the UI must not present a date
 * derived from the median payment gap with the same confidence as one the biller actually
 * stated — the distinction is invisible once both are just dates.
 */
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["status", "dueDate"]),
        Index(value = ["dueDate"]),
        Index(value = ["type"]),
        Index(value = ["merchantId"]),
        Index(value = ["accountId"]),
    ],
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Name of `BillType`. */
    val type: String,
    val merchantId: Long? = null,
    val accountId: Long? = null,
    val consumerNumber: String? = null,
    val lastAmountMinor: Long? = null,
    val averageAmountMinor: Long? = null,
    val currency: String = Money.INR,
    /** Name of `Recurrence`. */
    val recurrence: String,
    val lastPaidAt: Long? = null,
    /** Epoch day. */
    val dueDate: Long? = null,
    val dueDateIsEstimated: Boolean = true,
    val amountDueMinor: Long? = null,
    /** Name of `Bill.Status`. */
    val status: String,
    val paymentCount: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val confidence: Float = 0.6f,
)
