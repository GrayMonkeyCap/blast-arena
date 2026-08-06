package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.Money

/**
 * A bank account, card or wallet inferred from the masked identifiers banks put in SMS.
 *
 * Identity is (bank, masked number, type) because that triple is everything a bank actually
 * discloses; the unique index makes account discovery idempotent, so re-parsing the inbox
 * cannot fan one account out into a dozen. SQLite treats NULLs as distinct in unique indices,
 * which is the behaviour we want: an account whose bank or number could not be read is not
 * "the same unknown account" as another one.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["bankCode", "maskedNumber", "type"], unique = true),
        Index(value = ["type"]),
        Index(value = ["isArchived"]),
        Index(value = ["lastSeenAt"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    /** Name of `AccountType`. */
    val type: String,
    val bankCode: String? = null,
    val bankName: String? = null,
    /** Always the masked form, e.g. `XX4521`; full numbers are never stored. */
    val maskedNumber: String? = null,
    val currency: String = Money.INR,
    val lastKnownBalanceMinor: Long? = null,
    val balanceAsOf: Long? = null,
    val creditLimitMinor: Long? = null,
    val isArchived: Boolean = false,
    val colorSeed: Int = 0,
    val transactionCount: Int = 0,
    val firstSeenAt: Long? = null,
    val lastSeenAt: Long? = null,
)
