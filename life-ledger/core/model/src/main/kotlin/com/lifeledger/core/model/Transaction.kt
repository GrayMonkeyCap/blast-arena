package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The central record of Life Ledger: one thing that happened, with money attached.
 *
 * Everything downstream — timeline, dashboards, statistics, insights, search — reads
 * transactions. Non-financial events (OTP, delivery) are represented with a zero [amount]
 * and a [TransactionType] whose `isFinancial` is false.
 */
data class Transaction(
    val id: Long = 0,
    val amount: Money,
    val type: TransactionType,
    val direction: Direction = type.direction,
    val category: TxnCategory = TxnCategory.UNCATEGORIZED,
    val subcategory: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,

    val occurredAt: Instant,

    val merchantId: Long? = null,
    /** Merchant name as displayed — already normalized by the merchant resolver. */
    val merchantName: String? = null,
    /** The merchant string exactly as it appeared in the SMS, kept for auditing. */
    val rawMerchant: String? = null,
    val merchantConfidence: Confidence = Confidence.NONE,

    val accountId: Long? = null,
    val maskedAccount: String? = null,
    val bankCode: String? = null,
    val upiId: String? = null,

    /** Account balance the bank reported in the same message, when present. */
    val balanceAfter: Money? = null,

    val referenceNumber: String? = null,
    val transactionId: String? = null,

    val description: String? = null,
    val notes: String? = null,

    val smsId: Long? = null,
    val parserId: String? = null,
    val parserConfidence: Confidence = Confidence.NONE,
    val source: SourceKind = SourceKind.SMS,

    /** Set when the user has overridden any auto-derived field; auto-rules stop touching it. */
    val userVerified: Boolean = false,
    val excludedFromStats: Boolean = false,

    /** Populated when this row is a confirmed duplicate of another; hidden from all totals. */
    val duplicateOfId: Long? = null,

    val tagIds: List<Long> = emptyList(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val isDuplicate: Boolean get() = duplicateOfId != null
    val isIncome: Boolean get() = type.isIncome
    val isExpense: Boolean get() = type.isExpense
    val countsTowardStats: Boolean get() = !isDuplicate && !excludedFromStats && type.isFinancial

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        occurredAt.atZone(zone).toLocalDate()

    /** What the timeline and lists show when there is no better label. */
    val displayTitle: String
        get() = merchantName
            ?: description
            ?: rawMerchant
            ?: type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
