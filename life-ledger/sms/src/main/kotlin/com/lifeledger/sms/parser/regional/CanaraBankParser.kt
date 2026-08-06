package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Canara Bank alerts.
 *
 * Canara's UPI debit template names the counterparty *after* `credited to`, immediately
 * followed by a parenthesised UPI reference — `...credited to Amazon Pay (UPI Ref no
 * 302345678901)-Canara Bank` — even though the message as a whole is a debit from the
 * user's account. The shared merchant patterns in [com.lifeledger.sms.lex.SmsPatterns]
 * never consume `(`, so on this exact shape they fail outright rather than mis-capture:
 * this parser recovers the merchant directly instead of leaving it null.
 *
 * `senderCodes` deliberately excludes anything that could collide with SBI's core-banking
 * sender (`CBSSBI`) — Canara's own codes never share that prefix, so no extra filtering is
 * needed beyond the normal `canHandle` containment check.
 */
class CanaraBankParser : BaseBankParser() {

    override val bankCode: String = "CNRB"

    override val info = ParserInfo(
        id = "canara_bank",
        displayName = "Canara Bank",
        version = 1,
        senderCodes = setOf("CANBNK", "CANARA"),
        priority = 20,
        description = "Parses Canara Bank debit/credit SMS alerts, including the " +
            "'credited to X (UPI Ref ...)' counterparty shape used even on debits.",
    )

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? {
        val merchant = CREDITED_TO.find(sms.body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return if (merchant != null) draft.copy(rawMerchant = merchant) else draft
    }

    override fun confidenceBonus(sms: SmsRecord): Float =
        if (SIGNATURE.containsMatchIn(sms.body)) 0.1f else 0f

    private companion object {
        val CREDITED_TO = Regex(
            """credited\s+to\s+([A-Za-z0-9&'.\- ]{2,45}?)\s*\(""",
            RegexOption.IGNORE_CASE,
        )
        val SIGNATURE = Regex("""canara""", RegexOption.IGNORE_CASE)
    }
}
