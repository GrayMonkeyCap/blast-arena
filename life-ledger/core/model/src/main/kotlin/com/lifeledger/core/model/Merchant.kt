package com.lifeledger.core.model

import java.time.Instant

/**
 * A canonical merchant, plus every raw string that has been seen to mean it.
 *
 * The alias list is what turns `AMZN*MKTPLACE`, `AMAZON PAY INDIA` and `AMAZON SELLER`
 * into one "Amazon". Aliases accumulate from the built-in catalogue, from fuzzy matches
 * the resolver accepts, and from the user's own corrections.
 */
data class Merchant(
    val id: Long = 0,
    val canonicalName: String,
    /** Lowercased, punctuation-stripped key used for exact lookups. */
    val normalizedKey: String = normalizeKey(canonicalName),
    val defaultCategory: TxnCategory = TxnCategory.UNCATEGORIZED,
    val defaultSubcategory: String? = null,
    val website: String? = null,
    val logoHint: String? = null,
    /** True when the merchant came from the shipped catalogue rather than being learned. */
    val isBuiltIn: Boolean = false,
    val isSubscriptionProvider: Boolean = false,
    val isBillProvider: Boolean = false,
    val isInvestmentProvider: Boolean = false,
    val transactionCount: Int = 0,
    val firstSeenAt: Instant? = null,
    val lastSeenAt: Instant? = null,
) {
    companion object {
        /** Strips case, punctuation and filler so `AMZN*MKTP IN` and `amzn mktp in` collide. */
        fun normalizeKey(raw: String): String = raw
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}

/** One raw merchant string mapped to a [Merchant]. */
data class MerchantAlias(
    val id: Long = 0,
    val merchantId: Long,
    val alias: String,
    val normalizedAlias: String = Merchant.normalizeKey(alias),
    /** True when the user created this mapping by correcting a transaction. */
    val userDefined: Boolean = false,
    val confidence: Confidence = Confidence.CERTAIN,
    val hitCount: Int = 0,
)
