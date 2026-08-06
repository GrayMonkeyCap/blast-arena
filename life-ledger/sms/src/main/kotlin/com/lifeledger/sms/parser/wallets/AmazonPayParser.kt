package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Amazon Pay: UPI transactions and "Amazon Pay balance" (Amazon's own wallet) share sender
 * codes, exactly the ambiguity Paytm has — resolved the same way, through
 * [WalletSupport.resolvePaymentMethod].
 *
 * `AMAZON` is also the sender code Amazon uses for order/delivery notifications, which are
 * non-financial and belong to `com.lifeledger.sms.parser.life.DeliveryParser` instead. That
 * parser runs at a lower priority number (earlier in the pipeline) and claims
 * delivery-shaped messages first, so this parser only ever sees whatever is left over — it
 * does not need to defend against delivery messages itself.
 */
class AmazonPayParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "amazon-pay",
        displayName = "Amazon Pay",
        version = 1,
        senderCodes = setOf("AMZNPY", "AMAZON", "APAYIN"),
        priority = 15,
        description = "UPI and wallet payments made through Amazon Pay.",
    )

    override val bankCode = "AMAZONPAY"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val lower = sms.body.lowercase()
        var bonus = 0f
        if (lower.contains("amazon pay")) bonus += 0.05f
        if (lower.contains("apay")) bonus += 0.05f
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
