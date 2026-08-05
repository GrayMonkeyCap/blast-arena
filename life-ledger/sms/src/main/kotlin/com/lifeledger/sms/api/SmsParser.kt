package com.lifeledger.sms.api

import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.SmsRecord

/**
 * Contract every SMS parser implements.
 *
 * A parser is a *pure function* from one message to one [ParseResult]: no I/O, no database,
 * no clock of its own. That constraint is what makes the engine testable against a corpus
 * of thousands of real messages in milliseconds, and what lets the app re-run every parser
 * over stored history when a parser is improved.
 *
 * Parsers must never throw for unrecognised input — return [ParseResult.NotApplicable]
 * instead, so the pipeline can fall through to the next candidate.
 */
interface SmsParser {

    val info: ParserInfo

    /**
     * Cheap pre-filter run before [parse]. Returning false must be conservative: a parser
     * that says "not mine" is never given the message again in this pass.
     *
     * The default implementation claims a message when the sender code matches one of the
     * parser's declared codes, or when the parser declares no codes at all (content-matched).
     */
    fun canHandle(sms: SmsRecord): Boolean {
        if (info.senderCodes.isEmpty()) return true
        val code = sms.senderCode
        return info.senderCodes.any { code.contains(it) }
    }

    fun parse(sms: SmsRecord, context: ParserContext): ParseResult
}

/**
 * Everything a parser is allowed to know about the outside world.
 *
 * Passing this in rather than injecting keeps parsers constructible in a unit test with a
 * single line, and keeps the "parsers are pure" rule honest.
 */
data class ParserContext(
    /** Zone used to turn a bank's local date/time text into an instant. */
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    /** Fallback when the message carries no timestamp of its own. */
    val fallbackInstant: java.time.Instant,
    val defaultCurrency: String = com.lifeledger.core.model.Money.INR,
)
