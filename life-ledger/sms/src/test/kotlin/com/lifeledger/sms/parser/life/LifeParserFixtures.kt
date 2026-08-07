package com.lifeledger.sms.parser.life

import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import java.time.Instant
import java.time.ZoneId

/**
 * Fixture helpers shared by every test in this package.
 *
 * Kept self-contained (no dependency on fixtures from other parser packages) so this
 * package's tests can be run, read and evolved in isolation, matching how the parsers
 * themselves are meant to be developed and reviewed independently of each other.
 */
internal val FIXED_NOW: Instant = Instant.parse("2026-08-05T10:15:30Z")

internal val testContext: ParserContext = ParserContext(
    zone = ZoneId.of("Asia/Kolkata"),
    fallbackInstant = FIXED_NOW,
)

internal fun sms(
    sender: String,
    body: String,
    at: Instant = FIXED_NOW,
): SmsRecord = SmsRecord(
    id = 1,
    fingerprint = "$sender|$body|$at",
    sender = sender,
    body = body,
    receivedAt = at,
)
