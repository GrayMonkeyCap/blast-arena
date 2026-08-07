package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * IDFC FIRST Bank alerts.
 *
 * IDFC FIRST writes two families of message. Its own house style — `Your A/C XX1234 is
 * debited by INR 1,200.00 on 05-Aug-25. New Bal :INR 23,800.00` — names no counterparty at
 * all, and the honest result is a transaction with a null merchant rather than a guess
 * scraped out of the balance clause.
 *
 * The second family is ICICI's `… debited for INR X on <date>; MERCHANT credited.` template,
 * which IDFC adopted wholesale and which breaks generic merchant extraction the same way it
 * does at ICICI: the only preposition in the sentence introduces the *amount*, so the shared
 * patterns report the amount as the payee. Both banks are served by the same shared
 * refinement, which is precisely why that helper lives beside these parsers.
 */
class IdfcParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "idfc.v1",
        displayName = "IDFC FIRST Bank",
        version = 1,
        senderCodes = setOf("IDFCFB", "IDFCBK"),
        priority = 10,
        description = "IDFC FIRST Bank account, UPI and card alerts, including the shared '<payee> credited' template.",
    )

    override val bankCode = "IDFC"

    private enum class Template(val bonus: Float) {
        PAYEE_CREDITED(0.12f),
        ACCOUNT_MOVE(0.12f),
        CARD_SPEND(0.12f),
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
            BankRefinements.counterpartyBeforeCredited(body) != null -> Template.PAYEE_CREDITED
            lower.contains("card") && (lower.contains("spent") || lower.contains("transaction")) ->
                Template.CARD_SPEND
            ACCOUNT_MOVE.containsMatchIn(body) -> Template.ACCOUNT_MOVE
            lower.contains("idfc") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(draft: ParsedTransaction, body: String): ParsedTransaction? {
        BankRefinements.rejectionReason(body)?.let { return null }

        val template = template(body)

        val merchant = when (template) {
            Template.PAYEE_CREDITED -> BankRefinements.counterpartyBeforeCredited(body)
            Template.CARD_SPEND -> BankRefinements.payeeAfterDate(body)
            else -> null
        }
            ?: BankRefinements.stripTrailingNoise(draft.rawMerchant)
                ?.takeUnless { BankRefinements.isSelfReference(it, "idfc", "idfc first") }
            ?: BankRefinements.vpaCounterparty(body)

        val method = when {
            template == Template.CARD_SPEND -> cardMethod(body)
            draft.paymentMethod == PaymentMethod.UNKNOWN && draft.upiId != null -> PaymentMethod.UPI
            else -> draft.paymentMethod
        }

        val reference = draft.referenceNumber ?: BankRefinements.referenceNumber(body)

        return draft.copy(
            rawMerchant = merchant,
            paymentMethod = method,
            upiId = draft.upiId ?: BankRefinements.vpaCounterparty(body),
            referenceNumber = reference,
            transactionId = draft.transactionId ?: reference,
            balanceAfter = draft.balanceAfter?.takeIf { BankRefinements.statesRealBalance(body) },
        ).withParserFields(
            template = template.name,
            extra = BankRefinements.availableLimit(body)
                ?.let { mapOf("availableLimit" to it.minor.toString()) }
                ?: emptyMap(),
        )
    }

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
        val ACCOUNT_MOVE = Regex(
            """\ba/?c(?:ct)?\s*[A-Za-z0-9*]*\d{3,6}\s*(?:is\s+)?(?:debited|credited)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
