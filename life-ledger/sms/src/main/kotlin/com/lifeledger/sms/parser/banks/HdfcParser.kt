package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * HDFC Bank alerts.
 *
 * HDFC runs more distinct alert templates than any other Indian issuer because its UPI,
 * card and core-banking systems each speak their own dialect. Three of them need help that
 * the shared extraction cannot give:
 *
 *  - the UPI push (`Sent Rs.X From HDFC Bank A/C xx1234 To NAME`) names the payee after
 *    `To` and the *source* account after `From`, the mirror image of what the generic
 *    `from <name>` pattern assumes;
 *  - the core-banking form (`UPDATE: INR X debited from HDFC Bank XX1234 … Info: UPI/…`)
 *    names no counterparty in prose at all — it is buried in the narration — while the
 *    generic pattern happily reports "HDFC Bank XX1234", the user's own account, as the
 *    merchant;
 *  - card spends (`Spent Rs.X On HDFC Bank Card xx1234 At MERCHANT`) say nothing about the
 *    rail, so the payment method has to be inferred from the card wording.
 */
class HdfcParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "hdfc.v1",
        displayName = "HDFC Bank",
        version = 1,
        senderCodes = setOf("HDFCBK", "HDFCBN", "HDFCB"),
        priority = 10,
        description = "HDFC Bank account, UPI and card alerts, including the Info:-narration core-banking format.",
    )

    override val bankCode = "HDFC"

    /**
     * The HDFC templates worth recognising by name.
     *
     * A template hit is a much stronger claim than "this message mentions money", so it
     * carries a confidence bonus large enough to push a fully-extracted alert into
     * [com.lifeledger.core.model.Confidence.Band.CERTAIN]; [HOUSE_STYLE] only says the
     * message is plausibly HDFC's, which is worth a nudge and no more.
     */
    private enum class Template(val bonus: Float) {
        UPI_PUSH(0.12f),
        CARD_SPEND(0.12f),
        INFO_UPDATE(0.12f),
        VPA_CREDIT(0.12f),
        DEPOSIT(0.1f),
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
            SENT_FROM_HDFC.containsMatchIn(body) -> Template.UPI_PUSH
            lower.contains("spent") && lower.contains("card") &&
                (lower.contains("hdfc") || SPENT_ON_CARD.containsMatchIn(body)) -> Template.CARD_SPEND
            BankRefinements.infoFieldFull(body) != null &&
                (lower.contains("debited") || lower.contains("credited")) -> Template.INFO_UPDATE
            lower.contains("by vpa") || CREDITED_TO_ACCOUNT.containsMatchIn(body) -> Template.VPA_CREDIT
            lower.contains("deposited in hdfc") -> Template.DEPOSIT
            lower.contains("hdfc") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(draft: ParsedTransaction, body: String): ParsedTransaction? {
        BankRefinements.rejectionReason(body)?.let { return null }

        val template = template(body)
        var result = when (template) {
            Template.UPI_PUSH -> upiPush(draft, body)
            Template.CARD_SPEND -> cardSpend(draft, body)
            Template.INFO_UPDATE -> infoUpdate(draft, body)
            Template.VPA_CREDIT, Template.DEPOSIT -> incoming(draft, body)
            Template.HOUSE_STYLE, Template.NONE -> draft
        }

        // Every template shares the same two hazards: a counterparty that is really HDFC
        // itself, and a reference the shared pattern could not reach.
        val merchant = BankRefinements.stripTrailingNoise(result.rawMerchant)
            ?.takeUnless { BankRefinements.isSelfReference(it, "hdfc", "hdfc bank") }
        val reference = result.referenceNumber ?: BankRefinements.referenceNumber(body)
        result = result.copy(
            rawMerchant = merchant,
            referenceNumber = reference,
            transactionId = result.transactionId ?: reference,
            description = BankRefinements.infoFieldFull(body) ?: result.description,
        )

        return result.withParserFields(template.name, limitFields(body))
    }

    /**
     * `Sent Rs.79.00 From HDFC Bank A/C xx1234 To ZOMATO On 05/08/25 Ref 512345678901`.
     *
     * "Sent" is HDFC's own verb for a UPI push and carries no ambiguity, so the direction is
     * asserted outright and the semantic type re-derived from it rather than inherited from
     * whatever the generic pass concluded.
     */
    private fun upiPush(draft: ParsedTransaction, body: String): ParsedTransaction {
        val direction = BankRefinements.directionFromSentReceived(body) ?: draft.direction
        val payee = BankRefinements.stripTrailingNoise(SENT_TO.find(body)?.groupValues?.get(1))
        return draft.copy(
            direction = direction,
            type = Lexicon.transactionType(body, direction),
            paymentMethod = PaymentMethod.UPI,
            rawMerchant = payee ?: draft.rawMerchant,
            referenceNumber = draft.referenceNumber ?: BankRefinements.referenceNumber(body),
        )
    }

    /**
     * `Spent Rs.1,250.00 On HDFC Bank Card xx1234 At AMAZON On 05-08-25`.
     *
     * HDFC names the product explicitly on credit-card alerts ("Credit Card") and omits the
     * word entirely on debit-card ones, so a bare "Card" is a debit card. Getting this wrong
     * in either direction is expensive: a credit-card spend booked to the savings account
     * double-counts when the bill is paid.
     */
    private fun cardSpend(draft: ParsedTransaction, body: String): ParsedTransaction {
        val lower = body.lowercase()
        val method = when {
            lower.contains("credit card") -> PaymentMethod.CARD_CREDIT
            lower.contains("debit card") -> PaymentMethod.CARD_DEBIT
            else -> PaymentMethod.CARD_DEBIT
        }
        val merchant = BankRefinements.stripTrailingNoise(SPENT_AT.find(body)?.groupValues?.get(1))
        return draft.copy(
            direction = Direction.DEBIT,
            paymentMethod = method,
            rawMerchant = merchant ?: draft.rawMerchant,
        )
    }

    /**
     * `UPDATE: INR 1,500.00 debited from HDFC Bank XX1234 on 05-AUG-25. Info: UPI/DR/…/SWIGGY/…`
     *
     * The prose names only the user's own account, so the merchant and the RRN both have to
     * come out of the narration — and the generic merchant, whatever it found, is wrong by
     * construction here and is dropped rather than kept as a fallback.
     */
    private fun infoUpdate(draft: ParsedTransaction, body: String): ParsedTransaction {
        val narration = BankRefinements.infoFieldFull(body)
        return draft.copy(
            rawMerchant = BankRefinements.merchantFromUpiInfoField(narration),
            referenceNumber = draft.referenceNumber
                ?: BankRefinements.referenceNumber(body)
                ?: BankRefinements.referenceFromNarration(narration),
            description = narration ?: draft.description,
        )
    }

    /** Credits, where the payer is a VPA or a narration and HDFC states the RRN in prose. */
    private fun incoming(draft: ParsedTransaction, body: String): ParsedTransaction {
        val narration = BankRefinements.infoFieldFull(body)
        val payer = draft.upiId
            ?: BankRefinements.vpaCounterparty(body)
            ?: BankRefinements.merchantFromUpiInfoField(narration)
        return draft.copy(
            rawMerchant = BankRefinements.stripTrailingNoise(draft.rawMerchant) ?: payer,
            upiId = draft.upiId ?: BankRefinements.vpaCounterparty(body),
            referenceNumber = draft.referenceNumber ?: BankRefinements.referenceNumber(body),
        )
    }

    /** Surfaces a card's available limit without ever letting it masquerade as a balance. */
    private fun limitFields(body: String): Map<String, String> {
        val limit = BankRefinements.availableLimit(body) ?: return emptyMap()
        return mapOf("availableLimit" to limit.minor.toString())
    }

    private companion object {
        val SENT_FROM_HDFC = Regex(
            """(?:^|\b)(?:amt\s+)?sent\s*(?:rs\.?|inr|₹)""",
            RegexOption.IGNORE_CASE,
        )
        val SENT_TO = Regex(
            """\bto\s+([A-Za-z0-9][A-Za-z0-9 &.'@_\-]{1,45}?)\s*(?:\r?\n|\s+on\b|[.;,]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val SPENT_ON_CARD = Regex("""\bspent\b[^\n]{0,40}\bcard\b""", RegexOption.IGNORE_CASE)
        val SPENT_AT = Regex(
            """\bat\s+([A-Za-z0-9][A-Za-z0-9 &.'*_\-]{1,45}?)\s*(?:\r?\n|\s+on\b|[.;,]|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )
        val CREDITED_TO_ACCOUNT = Regex(
            """\bcredited\s+to\s+your\s+(?:account|a/?c)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
