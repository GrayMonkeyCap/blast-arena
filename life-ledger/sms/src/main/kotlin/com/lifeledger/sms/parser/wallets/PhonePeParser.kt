package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * PhonePe: UPI payments, PhonePe Wallet top-ups/spends and PhonePe-brokered bank transfers
 * all arrive from the same handful of sender codes and share one template family, so one
 * parser covers all three — [WalletSupport.resolvePaymentMethod] is what tells the UPI and
 * wallet cases apart.
 *
 * PhonePe's own transaction id (`Txn ID: Txxxxxxxxxxxx`) is the clearest signature of its
 * real template and is worth a confidence bump beyond the generic bank-parser baseline,
 * which only knows that *some* amount, direction and reference were found.
 */
class PhonePeParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "phonepe",
        displayName = "PhonePe",
        version = 1,
        senderCodes = setOf("PHONPE", "PHNPE", "PHONEPE"),
        priority = 15,
        description = "UPI, wallet and bank-linked payments made through the PhonePe app.",
    )

    override val bankCode = "PHONEPE"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val body = sms.body
        var bonus = 0f
        if (body.contains("phonepe", ignoreCase = true)) bonus += 0.05f
        if (TXN_ID.containsMatchIn(body)) bonus += 0.05f
        return bonus
    }

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction = draft.copy(
        paymentMethod = WalletSupport.resolvePaymentMethod(sms.body, draft.paymentMethod),
    )

    private companion object {
        val TXN_ID = Regex("""\bT\d{9,}\b""")
    }
}
