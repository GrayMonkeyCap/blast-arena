package com.lifeledger.core.model

import java.time.Instant

/**
 * The structured object every SMS parser returns.
 *
 * This is intentionally a *flat, nullable-heavy* shape: parsers extract whatever the
 * message actually contains and leave the rest null. Enrichment (merchant resolution,
 * categorisation, account linking) happens in later pipeline stages, so parsers stay
 * small, pure and independently testable.
 */
data class ParsedTransaction(
    val amount: Money?,
    val type: TransactionType,
    val direction: Direction = type.direction,
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,
    val occurredAt: Instant,
    /** Merchant exactly as written in the message. */
    val rawMerchant: String? = null,
    val maskedAccount: String? = null,
    val bankCode: String? = null,
    val upiId: String? = null,
    val balanceAfter: Money? = null,
    val referenceNumber: String? = null,
    val transactionId: String? = null,
    val description: String? = null,
    val category: TxnCategory? = null,
    val instrumentType: InstrumentType? = null,
    val billType: BillType? = null,
    val confidence: Confidence = Confidence.MEDIUM,
    /** Named capture groups the parser matched, surfaced verbatim in Parser Logs. */
    val extractedFields: Map<String, String> = emptyMap(),
)

/** Outcome of running one message through one parser. */
sealed interface ParseResult {
    /** The parser recognised the message and produced a transaction. */
    data class Success(val transaction: ParsedTransaction, val parserId: String) : ParseResult

    /** The message is genuinely not a transaction (promo, greeting, delivery-only). */
    data class Ignored(val parserId: String, val reason: String) : ParseResult

    /** This parser does not handle this message; the pipeline should try the next one. */
    data object NotApplicable : ParseResult

    /** The parser matched but could not extract required fields, or threw. */
    data class Failure(val parserId: String, val reason: String, val cause: Throwable? = null) :
        ParseResult
}

/** Static description of a parser, shown in Settings › Parser Management. */
data class ParserInfo(
    val id: String,
    val displayName: String,
    val version: Int,
    /** Sender codes this parser claims, e.g. `HDFCBK`, `HDFCBN`. Empty means content-matched. */
    val senderCodes: Set<String>,
    val priority: Int,
    val enabled: Boolean = true,
    val description: String = "",
)

/** One row in the parser audit log. Bounded and purgeable; never leaves the device. */
data class ParseLogEntry(
    val id: Long = 0,
    val smsId: Long,
    val parserId: String?,
    val outcome: Outcome,
    val reason: String? = null,
    val durationMicros: Long = 0,
    val confidence: Confidence = Confidence.NONE,
    val at: Instant,
    val senderSnippet: String = "",
    val bodySnippet: String = "",
) {
    enum class Outcome { SUCCESS, IGNORED, UNMATCHED, FAILURE, DUPLICATE }
}
