package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import java.time.Instant
import java.time.ZoneId

/**
 * Shared test fixtures for this package's parser suites.
 *
 * Kept local to `parser.regional` on purpose: the bank-parser and UPI/wallet-parser suites
 * are being built by other workers in parallel in different packages, and depending on
 * their fixtures would create a build-order coupling none of us need. A fixed zone and
 * instant make every test deterministic regardless of where or when the suite runs.
 */

val FIXTURE_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
val FIXTURE_RECEIVED_AT: Instant = Instant.parse("2026-08-05T10:15:30Z")

/** Builds a minimal, valid [SmsRecord] for a parser test. */
fun sms(sender: String, body: String, at: Instant = FIXTURE_RECEIVED_AT): SmsRecord =
    SmsRecord(
        fingerprint = "$sender|${body.hashCode()}|${at.epochSecond}",
        sender = sender,
        body = body,
        receivedAt = at,
    )

/** The context every parser test in this package runs against. */
val testContext: ParserContext = ParserContext(
    zone = FIXTURE_ZONE,
    fallbackInstant = FIXTURE_RECEIVED_AT,
)

/** Unwraps a [ParseResult], failing with a readable message when it wasn't a [ParseResult.Success]. */
fun ParseResult.asSuccess(): ParsedTransaction {
    check(this is ParseResult.Success) { "expected ParseResult.Success but was $this" }
    return transaction
}
