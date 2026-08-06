package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The canonical merchant list: the shipped catalogue plus everything learned on device.
 *
 * [normalizedKey] is unique rather than [canonicalName] because the resolver looks merchants
 * up by the normalised form on every parsed message, and two rows normalising to the same key
 * would make that lookup ambiguous — which is exactly the bug that produces "Amazon" and
 * "AMAZON PAY" as separate merchants in the UI.
 */
@Entity(
    tableName = "merchants",
    indices = [
        Index(value = ["normalizedKey"], unique = true),
        Index(value = ["canonicalName"]),
        Index(value = ["defaultCategory"]),
        Index(value = ["lastSeenAt"]),
    ],
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedKey: String,
    val canonicalName: String,
    /** Name of `TxnCategory`. */
    val defaultCategory: String,
    val defaultSubcategory: String? = null,
    val website: String? = null,
    val logoHint: String? = null,
    /** True for catalogue rows, which the learner may enrich but must never rename. */
    val isBuiltIn: Boolean = false,
    val isSubscriptionProvider: Boolean = false,
    val isBillProvider: Boolean = false,
    val isInvestmentProvider: Boolean = false,
    val transactionCount: Int = 0,
    val firstSeenAt: Long? = null,
    val lastSeenAt: Long? = null,
)

/**
 * One raw merchant string mapped onto a [MerchantEntity].
 *
 * [hitCount] is maintained so the resolver can promote the aliases that actually earn their
 * keep and so unused fuzzy matches can be pruned without guessing.
 */
@Entity(
    tableName = "merchant_aliases",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            // An alias has no meaning without its merchant.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["normalizedAlias"], unique = true),
        Index(value = ["merchantId"]),
    ],
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedAlias: String,
    val merchantId: Long,
    val alias: String,
    /** True when the user created this mapping by correcting a transaction; such aliases
     *  outrank anything the fuzzy matcher proposes. */
    val userDefined: Boolean = false,
    val confidence: Float = 1f,
    val hitCount: Int = 0,
)
