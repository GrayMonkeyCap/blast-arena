package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.lex.SmsPatterns
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Content-matched fallback for banks that don't have a dedicated parser yet.
 *
 * Runs last ([ParserInfo.priority] `900`) and claims no sender codes, so every message no
 * higher-priority parser wanted reaches it. That position makes it the highest-risk parser
 * in the pipeline: a wrong guess here silently fabricates a transaction for a bank nobody
 * has actually verified the SMS shape of. It is deliberately conservative — it only accepts
 * a message that has a parseable amount, a clear debit/credit verb, *and* at least one
 * corroborating identifier (masked account, UPI id, or reference number) — and refuses
 * everything else so the message is left `UNMATCHED` for a future dedicated parser rather
 * than becoming a wrong entry on someone's timeline.
 *
 * That refusal has to live in [canHandle], not [parse]. [BaseBankParser.parse] is `final`
 * and, once entered, can only resolve to [com.lifeledger.core.model.ParseResult.Success] or
 * [com.lifeledger.core.model.ParseResult.Ignored] via the [refine] hook — never
 * [com.lifeledger.core.model.ParseResult.NotApplicable]. `Ignored` is the wrong shape for
 * "we don't know": it tells the rest of the app this was deliberately non-financial (a
 * promo, a bare OTP), which is a claim this parser has no basis for making about an
 * ambiguous message. [canHandle] is the extension point [com.lifeledger.sms.api.SmsParser]
 * defines for exactly this — "not mine, try someone else, or leave it unclaimed" — and a
 * declined `canHandle` and a `NotApplicable` parse are the same event from the pipeline's
 * point of view: this parser did not claim the message.
 */
class GenericBankParser : BaseBankParser() {

    override val bankCode: String = "UNKNOWN"

    override val info = ParserInfo(
        id = "generic_fallback",
        displayName = "Generic Bank Alert (fallback)",
        version = 1,
        senderCodes = emptySet(),
        priority = 900,
        description = "Conservative content-matched fallback for banks without a " +
            "dedicated parser. Only claims a message it can corroborate with an " +
            "account, UPI id or reference number.",
    )

    /**
     * The real refusal gate — see the class doc for why it lives here rather than in
     * [parse]. A message needs an amount, a direction-bearing verb, and at least one
     * concrete identifier before this parser will touch it.
     */
    override fun canHandle(sms: SmsRecord): Boolean {
        val body = sms.body
        val hasAmount = SmsPatterns.primaryAmount(body) != null
        val hasVerb = Lexicon.direction(body) != Direction.NEUTRAL
        if (!hasAmount || !hasVerb) return false
        return SmsPatterns.maskedAccount(body) != null ||
            SmsPatterns.upiId(body) != null ||
            SmsPatterns.referenceNumber(body) != null
    }

    /**
     * Defence in depth for the unsupported case of [parse] being invoked without the
     * [canHandle] gate first (e.g. a future caller that skips it): refuse the same way
     * rather than risk fabricating a transaction. This can only surface as `Ignored` once
     * inside `parse` — see the class doc — so this is a fallback, not the primary contract.
     */
    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? = if (canHandle(sms)) draft else null

    // No bank identity to reward: every message this parser sees is, by definition, one it
    // could not attribute to a known sender.
    override fun confidenceBonus(sms: SmsRecord): Float = 0f
}
