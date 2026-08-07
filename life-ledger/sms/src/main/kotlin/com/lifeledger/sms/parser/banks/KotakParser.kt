package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * Kotak Mahindra Bank alerts.
 *
 * Kotak states the direction with a bare verb — `Sent Rs.150.00 from Kotak Bank AC X1234 to
 * swiggy@ybl`, `Received Rs.2,000.00 in your Kotak Bank AC X1234 from rajesh@okhdfcbank` —
 * rather than the "debited/credited" the rest of the industry uses. `Received` happens to be
 * in the shared credit vocabulary; `Sent` is only there in its longer `sent to` form, so the
 * direction is re-derived here from the opening clause and the semantic type re-derived from
 * that direction.
 *
 * Kotak also identifies counterparties almost exclusively by VPA. The VPA is kept as the raw
 * merchant when nothing better exists, because it is what the merchant resolver has to work
 * with — but it is recorded as [ParsedTransaction.upiId] too, which is the field the resolver
 * actually indexes.
 */
class KotakParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "kotak.v1",
        displayName = "Kotak Mahindra Bank",
        version = 1,
        senderCodes = setOf("KOTAKB", "KMBANK", "KOTAK"),
        priority = 10,
        description = "Kotak Mahindra Bank account, UPI and card alerts, including the Sent/Received UPI wording.",
    )

    override val bankCode = "KOTAK"

    private enum class Template(val bonus: Float) {
        UPI_SENT(0.12f),
        UPI_RECEIVED(0.12f),
        CARD_SPEND(0.12f),
        ACCOUNT_MOVE(0.1f),
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
            SENT.containsMatchIn(body) -> Template.UPI_SENT
            RECEIVED.containsMatchIn(body) -> Template.UPI_RECEIVED
            lower.contains("card") && lower.contains("spent") -> Template.CARD_SPEND
            lower.contains("debited") || lower.contains("credited") -> Template.ACCOUNT_MOVE
            lower.contains("kotak") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(draft: ParsedTransaction, body: String): ParsedTransaction? {
        BankRefinements.rejectionReason(body)?.let { return null }

        val template = template(body)
        val direction = BankRefinements.directionFromSentReceived(body) ?: draft.direction
        val vpa = draft.upiId ?: BankRefinements.vpaCounterparty(body)

        val merchant = BankRefinements.stripTrailingNoise(draft.rawMerchant)
            ?.takeUnless { BankRefinements.isSelfReference(it, "kotak") }
            ?: vpa

        val method = when {
            template == Template.CARD_SPEND -> cardMethod(body)
            template == Template.UPI_SENT || template == Template.UPI_RECEIVED -> PaymentMethod.UPI
            draft.paymentMethod == PaymentMethod.UNKNOWN && vpa != null -> PaymentMethod.UPI
            else -> draft.paymentMethod
        }

        val reference = draft.referenceNumber ?: BankRefinements.referenceNumber(body)

        return draft.copy(
            direction = direction,
            // Re-derived rather than copied: the generic type was decided against a
            // direction that may have been wrong for exactly these two templates.
            type = if (direction == draft.direction) draft.type else Lexicon.transactionType(body, direction),
            paymentMethod = method,
            rawMerchant = merchant,
            upiId = vpa,
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
        val SENT = Regex("""(?:^|\b)(?:amt\s+)?sent\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
        val RECEIVED = Regex("""(?:^|\b)received\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
    }
}
