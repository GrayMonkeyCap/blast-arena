package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Catch-all for wallet top-ups and wallet spends that the sender-specific payment-app
 * parsers in this package did not claim — a wallet message from a short code like
 * `AD-MOBIKWIK` or `VM-FRECHG` never matches PhonePe/GPay/Paytm/AmazonPay's declared sender
 * codes, but it is exactly the same shape of message.
 *
 * Content-matched and run last of the payment-related parsers (priority 40) so every more
 * specific parser gets first refusal; [canHandle] still requires an explicit wallet mention
 * so this does not swallow ordinary bank alerts that happen to reach it.
 */
class WalletParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "wallet",
        displayName = "Wallet",
        version = 1,
        senderCodes = emptySet(),
        priority = 40,
        description = "Wallet top-ups and spends: Paytm Wallet, Amazon Pay balance, PhonePe Wallet, Mobikwik, Freecharge.",
    )

    override val bankCode = "WALLET"

    override fun canHandle(sms: SmsRecord): Boolean {
        val lower = sms.body.lowercase()
        return WALLET_TRIGGERS.any { lower.contains(it) }
    }

    override fun confidenceBonus(sms: SmsRecord): Float {
        val lower = sms.body.lowercase()
        return if (PROVIDERS.any { (keyword, _) -> lower.contains(keyword) }) 0.1f else 0f
    }

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction {
        val lower = sms.body.lowercase()
        val provider = PROVIDERS.firstOrNull { (keyword, _) -> lower.contains(keyword) }?.second
        return draft.copy(
            paymentMethod = WalletSupport.resolvePaymentMethod(sms.body, draft.paymentMethod),
            extractedFields = provider?.let { draft.extractedFields + ("provider" to it) }
                ?: draft.extractedFields,
        )
    }

    private companion object {
        val WALLET_TRIGGERS = listOf("wallet", "amazon pay balance", "mobikwik", "freecharge")
        val PROVIDERS = listOf(
            "paytm wallet" to "Paytm Wallet",
            "amazon pay balance" to "Amazon Pay",
            "phonepe wallet" to "PhonePe Wallet",
            "mobikwik" to "Mobikwik",
            "freecharge" to "Freecharge",
        )
    }
}
