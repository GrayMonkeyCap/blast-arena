package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.PaymentMethod

/**
 * Small helper shared by every parser in this package.
 *
 * Payment-app messages routinely name their own wallet product ("Paytm Wallet", "Amazon
 * Pay balance") *and* carry a UPI reference in the same body, because the same app offers
 * both rails. [com.lifeledger.sms.lex.Lexicon.paymentMethod] checks UPI ahead of WALLET,
 * which is the right precedence for a bank alert but the wrong one here: when a provider
 * explicitly names its wallet product, that is the more specific of the two signals and
 * must win. Every parser in this package runs its [PaymentMethod] through
 * [resolvePaymentMethod] rather than trusting the generic lexicon result directly.
 */
internal object WalletSupport {

    private val WALLET_MARKERS = listOf("wallet", "amazon pay balance", "mobikwik", "freecharge")

    /** [generic] is whatever [com.lifeledger.sms.lex.Lexicon.paymentMethod] already decided. */
    fun resolvePaymentMethod(body: String, generic: PaymentMethod): PaymentMethod {
        val lower = body.lowercase()
        return if (WALLET_MARKERS.any { lower.contains(it) }) PaymentMethod.WALLET else generic
    }
}
