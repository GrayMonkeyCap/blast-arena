package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Google Pay: almost exclusively UPI in India — it has no wallet product of its own, so
 * unlike PhonePe/Paytm/Amazon Pay the "wallet wins over UPI" rule in [WalletSupport] rarely
 * changes anything here. It is still run through the same resolver for consistency and for
 * the rare merchant-cashback message that names a "Google Pay" branded reward wallet.
 */
class GooglePayParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "google-pay",
        displayName = "Google Pay",
        version = 1,
        senderCodes = setOf("GOOGLE", "GPAY", "GOOGPY"),
        priority = 15,
        description = "UPI payments made through Google Pay.",
    )

    override val bankCode = "GPAY"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val lower = sms.body.lowercase()
        var bonus = 0f
        if (lower.contains("google pay") || lower.contains("gpay")) bonus += 0.05f
        if (lower.contains("upi")) bonus += 0.05f
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
