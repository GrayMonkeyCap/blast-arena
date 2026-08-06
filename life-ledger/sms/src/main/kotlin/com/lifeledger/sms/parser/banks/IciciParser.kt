package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * ICICI Bank alerts.
 *
 * ICICI's signature debit template is the one Indian format that defeats generic merchant
 * extraction outright:
 *
 * `ICICI Bank Acct XX123 debited for Rs 500.00 on 05-Aug-25; SWIGGY credited. UPI:5123…`
 *
 * The payee sits *after* the amount, introduced by nothing at all, while the only
 * preposition in sight — `for` — introduces the amount. The shared patterns therefore
 * report "Rs 500.00" as the merchant, which is not merely wrong but actively poisonous: it
 * creates a new merchant for every distinct amount the user ever pays. The card template
 * fails the same way for a different reason, latching onto the anti-fraud footer ("To
 * dispute, call…") because ICICI writes the merchant after the date rather than after `at`.
 *
 * Sender codes deliberately exclude `IPRUMF`: that is ICICI Prudential Mutual Fund, a
 * different institution whose statements this parser would misread as bank debits.
 */
class IciciParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "icici.v1",
        displayName = "ICICI Bank",
        version = 1,
        senderCodes = setOf("ICICIB", "ICICIT"),
        priority = 10,
        description = "ICICI Bank account, UPI and card alerts; excludes ICICI Prudential (IPRUMF) senders.",
    )

    override val bankCode = "ICICI"

    private enum class Template(val bonus: Float) {
        DEBIT_CREDITED(0.12f),
        ACCT_CREDITED(0.12f),
        CARD_SPEND(0.12f),
        ATM(0.1f),
        HOUSE_STYLE(0.04f),
        NONE(0f),
    }

    override fun confidenceBonus(sms: SmsRecord): Float = template(sms.body).bonus

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? = runCatching { correct(draft, sms.body) }.getOrElse { draft }

    private fun template(body: String): Template {
        val lower = body.lowercase()
        return when {
            DEBITED_FOR.containsMatchIn(body) -> Template.DEBIT_CREDITED
            lower.contains("atm") && lower.contains("debited") -> Template.ATM
            CREDITED_WITH.containsMatchIn(body) -> Template.ACCT_CREDITED
            lower.contains("card") && (lower.contains("spent") || lower.contains("transaction of")) ->
                Template.CARD_SPEND
            lower.contains("icici") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(draft: ParsedTransaction, body: String): ParsedTransaction? {
        BankRefinements.rejectionReason(body)?.let { return null }

        val template = template(body)
        val merchant = when (template) {
            Template.DEBIT_CREDITED -> BankRefinements.counterpartyBeforeCredited(body)
            Template.CARD_SPEND -> BankRefinements.payeeAfterDate(body)
            else -> null
        }
            ?: BankRefinements.stripTrailingNoise(draft.rawMerchant)
                ?.takeUnless { BankRefinements.isSelfReference(it, "icici") }
            ?: BankRefinements.vpaCounterparty(body)

        val method = when {
            template == Template.CARD_SPEND -> cardMethod(body)
            else -> draft.paymentMethod
        }

        val limit = BankRefinements.availableLimit(body)
        val reference = draft.referenceNumber ?: BankRefinements.referenceNumber(body)

        return draft.copy(
            rawMerchant = merchant,
            paymentMethod = method,
            referenceNumber = reference,
            transactionId = draft.transactionId ?: reference,
            // A credit limit is headroom, not money in an account: keeping it out of
            // balanceAfter is what stops a card alert from rewriting the account's history.
            balanceAfter = draft.balanceAfter?.takeIf { BankRefinements.statesRealBalance(body) },
        ).withParserFields(
            template = template.name,
            extra = limit?.let { mapOf("availableLimit" to it.minor.toString()) } ?: emptyMap(),
        )
    }

    /**
     * ICICI writes "ICICI Bank Card XX1234" for both products, so the limit line is the
     * discriminator: only a credit card has one.
     */
    private fun cardMethod(body: String): PaymentMethod {
        val lower = body.lowercase()
        return when {
            lower.contains("credit card") -> PaymentMethod.CARD_CREDIT
            lower.contains("debit card") -> PaymentMethod.CARD_DEBIT
            BankRefinements.availableLimit(body) != null -> PaymentMethod.CARD_CREDIT
            else -> PaymentMethod.CARD_DEBIT
        }
    }

    private companion object {
        val DEBITED_FOR = Regex(
            """\b(?:debited|debit)\s+for\s+(?:rs\.?|inr|₹)""",
            RegexOption.IGNORE_CASE,
        )
        val CREDITED_WITH = Regex(
            """\b(?:is\s+)?credited\s+with\s+(?:rs\.?|inr|₹)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
