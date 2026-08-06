package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * A holding, tracked as a contribution ledger rather than a valuation.
 *
 * With no network there is no live NAV, so the running totals here are the sum of what was
 * actually observed going in and coming out; [manualCurrentValueMinor] is the only field a
 * human can set and is deliberately kept apart from the derived totals.
 */
@Entity(
    tableName = "investments",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["instrumentType"]),
        Index(value = ["isActive"]),
        Index(value = ["name"]),
        Index(value = ["merchantId"]),
        Index(value = ["nextExpectedAt"]),
        Index(value = ["lastInvestedAt"]),
    ],
)
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Name of `InstrumentType`. */
    val instrumentType: String,
    val folioOrAccount: String? = null,
    val provider: String? = null,
    val merchantId: Long? = null,
    val totalInvestedMinor: Long = 0,
    val totalRedeemedMinor: Long = 0,
    val currency: String = Money.INR,
    val manualCurrentValueMinor: Long? = null,
    val valueAsOf: Long? = null,
    val units: Double? = null,
    val isSip: Boolean = false,
    val sipAmountMinor: Long? = null,
    /** Name of `Recurrence`. */
    val sipRecurrence: String,
    val sipDayOfMonth: Int? = null,
    /** Epoch day. Date-only, because an expected SIP debit has no meaningful clock time. */
    val nextExpectedAt: Long? = null,
    val isActive: Boolean = true,
    val contributionCount: Int = 0,
    val firstInvestedAt: Long? = null,
    val lastInvestedAt: Long? = null,
)

/**
 * One buy/sell/dividend event attached to an [InvestmentEntity].
 *
 * [transactionId] is nullable and set null on delete because contributions can also be
 * reconstructed from statements, where no bank SMS — and therefore no transaction — exists.
 */
@Entity(
    tableName = "investment_transactions",
    foreignKeys = [
        ForeignKey(
            entity = InvestmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["investmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["investmentId", "occurredAt"]),
        Index(value = ["transactionId"]),
        Index(value = ["occurredAt"]),
        Index(value = ["kind"]),
    ],
)
data class InvestmentTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val investmentId: Long,
    val transactionId: Long? = null,
    val amountMinor: Long,
    val currency: String = Money.INR,
    /** Name of `InvestmentTransaction.Kind`. */
    val kind: String,
    val occurredAt: Long,
    val units: Double? = null,
    val navOrPrice: Double? = null,
)
