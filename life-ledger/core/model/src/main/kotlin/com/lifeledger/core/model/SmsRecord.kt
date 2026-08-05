package com.lifeledger.core.model

import java.time.Instant

/**
 * A single SMS as it exists on the device, plus Life Ledger's processing bookkeeping.
 *
 * The raw body is retained verbatim: it is the ground truth every parse can be replayed
 * against when a parser is improved, and it never leaves the device.
 */
data class SmsRecord(
    val id: Long = 0,
    /** Stable identity derived from sender + body + timestamp; used to avoid re-import. */
    val fingerprint: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val threadId: Long? = null,
    /** Row id in the system SMS provider, when this record came from the device inbox. */
    val providerId: Long? = null,
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val processedAt: Instant? = null,
    /** Which parser claimed this message, if any. */
    val parserId: String? = null,
    val source: SourceKind = SourceKind.SMS,

    ) {
    /** The bank/service short code portion of an Indian sender id, e.g. `HDFCBK` in `AD-HDFCBK-S`. */
    val senderCode: String
        get() = sender.uppercase()
            .split('-')
            .maxByOrNull { it.count(Char::isLetter) }
            ?.filter { it.isLetterOrDigit() }
            ?: sender.uppercase()

    enum class ProcessingStatus {
        /** Stored, not yet run through the engine. */
        PENDING,

        /** A parser produced a transaction. */
        PARSED,

        /** Recognised as a real message but deliberately not financial (promo, OTP-only). */
        IGNORED,

        /** Every parser declined it. Kept for future parser improvements. */
        UNMATCHED,

        /** A parser threw; the error is recorded in the parse log. */
        FAILED,
    }
}
