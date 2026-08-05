package com.lifeledger.core.model

import java.time.Instant

/**
 * A bank account, card or wallet, discovered from the masked identifiers banks put in SMS.
 *
 * Life Ledger never knows the full account number — only the last digits the bank chose to
 * disclose — which is exactly enough to group transactions per account.
 */
data class Account(
    val id: Long = 0,
    val displayName: String,
    val type: AccountType,
    val bankCode: String? = null,
    val bankName: String? = null,
    /** e.g. `XX4521`. Always the masked form; full numbers are never stored. */
    val maskedNumber: String? = null,
    val currency: String = Money.INR,
    /** Latest balance the bank reported, with the time it was reported. */
    val lastKnownBalance: Money? = null,
    val balanceAsOf: Instant? = null,
    val creditLimit: Money? = null,
    val isArchived: Boolean = false,
    val colorSeed: Int = 0,
    val transactionCount: Int = 0,
    val firstSeenAt: Instant? = null,
    val lastSeenAt: Instant? = null,
)
