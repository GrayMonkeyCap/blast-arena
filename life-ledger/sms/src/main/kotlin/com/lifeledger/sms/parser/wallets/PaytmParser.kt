package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Paytm sends two genuinely different message families from overlapping sender codes:
 * ordinary UPI transfers through Paytm Payments Bank (PPBL), and Paytm Wallet
 * top-ups/spends. [WalletSupport.resolvePaymentMethod] is what tells them apart — the
 * word "wallet" is the one reliable discriminator across Paytm's own templates.
 */
class PaytmParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "paytm",
        displayName = "Paytm",
        version = 1,
        senderCodes = setOf("PAYTMB", "PAYTM", "PYTMPB"),
        priority = 15,
        description = "UPI and wallet payments made through Paytm / Paytm Payments Bank.",
    )

    override val bankCode = "PAYTM"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val lower = sms.body.lowercase()
        var bonus = 0f
        if (lower.contains("paytm")) bonus += 0.05f
        if (lower.contains("ppbl") || lower.contains("paytm payments bank")) bonus += 0.05f
        return bonus
    }

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction = draft.copy(
        paymentMethod = WalletSupport.resolvePaymentMethod(sms.body, draft.paymentMethod),
    )
}
