package com.lifeledger.sms.parser.banks

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.api.SmsParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Shared scaffolding for the bank-parser corpus.
 *
 * Parsers are pure functions, so these tests need no Robolectric, no Android runtime and no
 * clock: one fixed receipt instant and one zone make every date assertion exact, which
 * matters because several refinements exist specifically to correct the date.
 */

internal val TEST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** 5 Aug 2025, 21:30 IST — the receipt time every fixture message defaults to. */
internal val RECEIVED_AT: Instant = LocalDateTime.of(2025, 8, 5, 21, 30).atZone(TEST_ZONE).toInstant()

internal val ctx = ParserContext(zone = TEST_ZONE, fallbackInstant = RECEIVED_AT)

internal fun sms(sender: String, body: String, at: Instant = RECEIVED_AT): SmsRecord =
    SmsRecord(
        fingerprint = "$sender|${body.hashCode()}|${at.toEpochMilli()}",
        sender = sender,
        body = body,
        receivedAt = at,
    )

/**
 * What the engine is expected to do with a corpus message.
 *
 * [BLOCKED_BY_BASE_LEXICON] is not a category of message — it is a category of *defect*.
 * `BaseBankParser.parse` returns `NotApplicable` before `refine` is ever called when
 * `Lexicon.direction` reads NEUTRAL, and the shared debit vocabulary only knows "sent" in
 * its `sent to` form. HDFC's and Kotak's most common UPI alerts lead with a bare "Sent", so
 * they cannot reach the refinement that would classify them, even though that refinement is
 * written and correct. These cases are pinned rather than quietly excluded: the moment the
 * shared lexicon learns the verb, the pin fails and points at the tests to promote.
 */
internal enum class Expect { FINANCIAL, IGNORED, NOT_APPLICABLE, BLOCKED_BY_BASE_LEXICON }

/** One corpus entry: a real-world message shape and what should become of it. */
internal data class Case(
    val label: String,
    val sender: String,
    val body: String,
    val expect: Expect,
)

internal fun SmsParser.parse(case: Case): ParseResult = parse(sms(case.sender, case.body), ctx)

internal fun SmsParser.parse(sender: String, body: String): ParseResult =
    parse(sms(sender, body), ctx)

/** The transaction, with a readable failure when the parser declined the message instead. */
internal fun ParseResult.transaction(): ParsedTransaction {
    assertThat(this).isInstanceOf(ParseResult.Success::class.java)
    return (this as ParseResult.Success).transaction
}

internal fun ParseResult.ignoredReason(): String {
    assertThat(this).isInstanceOf(ParseResult.Ignored::class.java)
    return (this as ParseResult.Ignored).reason
}

internal fun ParsedTransaction.localDate(): LocalDate = occurredAt.atZone(TEST_ZONE).toLocalDate()

/** Every message in the six-bank corpus, in parser order. */
internal val ALL_BANK_CASES: List<Pair<SmsParser, List<Case>>>
    get() = listOf(
        HdfcParser() to HdfcSamples.corpus,
        IciciParser() to IciciSamples.corpus,
        SbiParser() to SbiSamples.corpus,
        AxisParser() to AxisSamples.corpus,
        KotakParser() to KotakSamples.corpus,
        IdfcParser() to IdfcSamples.corpus,
    )
