package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Yes Bank alerts.
 *
 * Yes Bank names the UPI counterparty as `to VPA <id>` rather than the plain `to <name>`
 * the shared merchant patterns are tuned for, so the literal `VPA` label leaks into
 * [ParsedTransaction.rawMerchant] as `"VPA rahul@ybl"` unless stripped here. Everything
 * else in Yes Bank's alerts — amount, direction, account, UPI reference — the generic
 * extraction in [BaseBankParser] already gets right, so this parser stays small.
 */
class YesBankParser : BaseBankParser() {

    override val bankCode: String = "YESB"

    override val info = ParserInfo(
        id = "yes_bank",
        displayName = "Yes Bank",
        version = 1,
        senderCodes = setOf("YESBNK", "YESBK"),
        priority = 20,
        description = "Parses Yes Bank debit/credit SMS alerts, UPI transfers included.",
    )

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? {
        val counterparty = VPA_COUNTERPARTY.find(sms.body)?.groupValues?.get(1)
        return if (counterparty != null) draft.copy(rawMerchant = counterparty) else draft
    }

    override fun confidenceBonus(sms: SmsRecord): Float =
        if (SIGNATURE.containsMatchIn(sms.body)) 0.1f else 0f

    private companion object {
        /** Strips the `VPA` label so the merchant field holds just the payee's UPI id. */
        val VPA_COUNTERPARTY = Regex("""\bVPA\s+([A-Za-z0-9._\-]+@[A-Za-z]{2,20})""", RegexOption.IGNORE_CASE)
        val SIGNATURE = Regex("""yes\s*bank""", RegexOption.IGNORE_CASE)
    }
}
