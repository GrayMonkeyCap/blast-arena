package com.lifeledger.sms.dedupe

import com.lifeledger.core.common.text.TextSimilarity
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Merchant
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import java.security.MessageDigest
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The subset of a stored transaction [DuplicateDetector] needs to compare two candidates.
 * Defined locally, rather than reusing `core.model.Transaction`, so this module never has to
 * depend on the database layer — the caller (the pipeline, or a query against stored
 * transactions) is responsible for projecting whatever row it has into this shape.
 */
data class DuplicateCandidate(
    val id: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val occurredAtMillis: Long,
    val maskedAccount: String?,
    val merchantName: String?,
    val referenceNumber: String?,
    val transactionId: String?,
    val smsBody: String,
)

/** Result of comparing two [DuplicateCandidate]s. */
data class DuplicateScore(
    val value: Float,
    val isDuplicate: Boolean,
    val reasons: List<String>,
)

/**
 * Catches the two shapes duplicate transactions actually take in Life Ledger.
 *
 * ## Why the same real-world purchase produces more than one SMS
 *
 * A single card swipe or UPI payment routinely triggers *multiple, independent* SMS: the
 * issuing bank sends a debit alert, the card network (Visa/Mastercard/RuPay) can send its
 * own authorization message through a different sender code, and the merchant's own app
 * (Swiggy, an airline, a hotel) sends a confirmation that repeats the amount. These are not
 * bugs or resends — they are different systems, with different latencies, independently
 * describing one wallet event. That is precisely why deduplication needs two different
 * layers:
 *
 * - [fingerprint] catches the *identical* case cheaply: the same SMS parsed twice (e.g. the
 *   ingest worker re-scans the inbox), or two messages that carry the same bank
 *   reference/transaction id, where there is no ambiguity at all.
 * - [scoreDuplicate] catches the *fuzzy cross-channel* case: two SMS from different senders,
 *   with different wording and sometimes a different masked account (the bank alert masks
 *   the savings account; a card-network alert masks the card number differently), that both
 *   describe the same purchase. Because these arrive from different systems, they can be
 *   minutes or hours apart — a bank settlement can lag well behind a card network's instant
 *   authorization — so a narrow same-account window would create false negatives (a real
 *   duplicate is missed). Same-account comparisons, by contrast, are almost always the
 *   ingest worker re-processing one message or one bank sending a near-duplicate alert
 *   within seconds, so a tight window is the right default there.
 */
@Singleton
class DuplicateDetector @Inject constructor() {

    /**
     * A stable hash for exact-duplicate rejection, meant to back a unique-ish `dedupeHash`
     * column so the database can reject a second insert of the same event outright.
     *
     * When the message carries a bank reference or transaction id, that id alone decides
     * identity — two messages with the same id are the same event by definition, regardless
     * of anything else in the message. Otherwise the fingerprint falls back to a composite of
     * (amount, direction, masked account, minute-truncated timestamp, normalized merchant):
     * coarse enough that near-simultaneous re-deliveries of the exact same alert collide, but
     * specific enough that two unrelated ₹500 debits an hour apart do not.
     *
     * The result is hashed (SHA-256) rather than stored as plain text so the column has a
     * fixed, opaque shape regardless of which branch produced it.
     */
    fun fingerprint(parsed: ParsedTransaction, sms: SmsRecord): String {
        val idPart = parsed.transactionId?.trim()?.takeIf { it.isNotEmpty() }
            ?: parsed.referenceNumber?.trim()?.takeIf { it.isNotEmpty() }

        val raw = if (idPart != null) {
            "id:${Merchant.normalizeKey(idPart)}"
        } else {
            val amount = parsed.amount?.minor ?: 0L
            val account = parsed.maskedAccount?.let { Merchant.normalizeKey(it) } ?: "-"
            val minute = parsed.occurredAt.truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
            val merchant = parsed.rawMerchant?.let { Merchant.normalizeKey(it) } ?: "-"
            "amt:$amount|dir:${parsed.direction.name}|acct:$account|min:$minute|m:$merchant"
        }
        return sha256Hex(raw)
    }

    /**
     * Weighted similarity between two stored candidates, for the fuzzy cross-channel case
     * [fingerprint] cannot catch because the two messages are not textually or referentially
     * identical.
     *
     * An identical, non-blank reference or transaction id shared by both candidates is
     * decisive on its own — two different bank systems do not coincidentally reuse the same
     * reference number for two different events — and short-circuits straight to a duplicate
     * verdict. Otherwise the score blends amount equality, timestamp proximity (decaying
     * across [sameAccountWindowMillis] when both candidates share a masked account, or the
     * much wider [crossChannelWindowMillis] when they do not — see the class KDoc for why),
     * merchant-name similarity and raw-SMS similarity. A currency or direction mismatch rules
     * out a duplicate outright: a credit and a debit, or two different currencies, cannot be
     * the same wallet event no matter how similar everything else looks.
     */
    fun scoreDuplicate(
        candidate: DuplicateCandidate,
        existing: DuplicateCandidate,
        sameAccountWindowMillis: Long = DEFAULT_SAME_ACCOUNT_WINDOW_MILLIS,
        crossChannelWindowMillis: Long = DEFAULT_CROSS_CHANNEL_WINDOW_MILLIS,
    ): DuplicateScore {
        val sharedId = sharedNonBlankId(candidate, existing)
        if (sharedId != null) {
            return DuplicateScore(1f, true, listOf("identical reference/transaction id ($sharedId)"))
        }

        if (candidate.currency != existing.currency || candidate.direction != existing.direction) {
            return DuplicateScore(0f, false, listOf("different currency or direction — cannot be the same event"))
        }

        val reasons = mutableListOf<String>()
        var score = 0f

        if (candidate.amountMinor == existing.amountMinor) {
            score += WEIGHT_AMOUNT
            reasons += "same amount"
        }

        val sameAccount = candidate.maskedAccount != null &&
            candidate.maskedAccount == existing.maskedAccount
        val window = if (sameAccount) sameAccountWindowMillis else crossChannelWindowMillis
        val elapsed = abs(candidate.occurredAtMillis - existing.occurredAtMillis)
        val proximity = (1.0 - elapsed.toDouble() / window.toDouble()).coerceAtLeast(0.0)
        if (proximity > 0.0) {
            score += (WEIGHT_TIME * proximity).toFloat()
            val channel = if (sameAccount) "same account" else "cross-channel"
            reasons += "within ${elapsed}ms of each other ($channel window)"
        }

        val merchantSimilarity = TextSimilarity.merchantSimilarity(
            candidate.merchantName.orEmpty(),
            existing.merchantName.orEmpty(),
        )
        if (merchantSimilarity > 0.0) {
            score += (WEIGHT_MERCHANT * merchantSimilarity).toFloat()
            reasons += "merchant similarity %.2f".format(merchantSimilarity)
        }

        val bodySimilarity = TextSimilarity.tokenSetRatio(candidate.smsBody, existing.smsBody)
        if (bodySimilarity > 0.0) {
            score += (WEIGHT_BODY * bodySimilarity).toFloat()
            reasons += "sms body similarity %.2f".format(bodySimilarity)
        }

        val clamped = score.coerceIn(0f, 1f)
        return DuplicateScore(clamped, clamped >= DUPLICATE_THRESHOLD, reasons)
    }

    private fun sharedNonBlankId(a: DuplicateCandidate, b: DuplicateCandidate): String? {
        val refMatch = a.referenceNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { it == b.referenceNumber?.trim() }
        if (refMatch != null) return refMatch
        return a.transactionId?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { it == b.transactionId?.trim() }
    }

    private fun sha256Hex(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val WEIGHT_AMOUNT = 0.30f
        const val WEIGHT_TIME = 0.30f
        const val WEIGHT_MERCHANT = 0.25f
        const val WEIGHT_BODY = 0.15f

        /** Score at/above which two candidates are treated as the same real-world event. */
        const val DUPLICATE_THRESHOLD = 0.72f

        /** Same masked account: near-simultaneous re-alerts or re-ingestion, not a lagged second channel. */
        const val DEFAULT_SAME_ACCOUNT_WINDOW_MILLIS = 3 * 60 * 1000L

        /** Different (or absent) masked account: bank alert vs. card network vs. merchant app can lag by hours. */
        const val DEFAULT_CROSS_CHANNEL_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}
