package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * State Bank of India alerts, including SBI Card.
 *
 * SBI's quirks are structural rather than lexical. It dates its alerts `05Aug25` with no
 * separators, so any *other* date in the same message — "Next EMI due 05/09/25" is a common
 * trailer — wins the shared date scan and moves the transaction to the wrong day. It
 * introduces UPI payers with `by`, a preposition the shared merchant patterns ignore
 * because it normally names the rail ("by NEFT", "by ATM WDL"). And SBI Card, a separate
 * issuer sharing the `SBICRD` sender space, describes spends in yet another wording.
 *
 * The one thing this parser deliberately does *not* touch is the semantic type of SBI's
 * "transfer to" wording: SBI uses it for merchant payments and person-to-person transfers
 * alike, so re-labelling it would trade one wrong answer for another.
 */
class SbiParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "sbi.v1",
        displayName = "State Bank of India",
        version = 1,
        senderCodes = setOf("SBIINB", "SBIPSG", "SBICRD", "ATMSBI", "SBIUPI", "CBSSBI"),
        priority = 10,
        description = "SBI account, UPI, ATM and SBI Card alerts, including the separator-less DDMMMYY date format.",
    )

    override val bankCode = "SBI"

    private enum class Template(val bonus: Float) {
        ACCOUNT_DEBIT(0.12f),
        ACCOUNT_CREDIT(0.12f),
        UPI_CREDIT(0.12f),
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
    ): ParsedTransaction? = runCatching { correct(draft, sms, context) }.getOrElse { draft }

    private fun template(body: String): Template {
        val lower = body.lowercase()
        return when {
            lower.contains("credit card") && lower.contains("spent") -> Template.CARD_SPEND
            lower.contains("atm") && (lower.contains("debited") || lower.contains("withdrawn")) -> Template.ATM
            CREDITED_BY_VPA.containsMatchIn(body) -> Template.UPI_CREDIT
            DEBITED_BY.containsMatchIn(body) -> Template.ACCOUNT_DEBIT
            CREDITED_BY.containsMatchIn(body) -> Template.ACCOUNT_CREDIT
            lower.contains("sbi") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? {
        BankRefinements.rejectionReason(sms.body)?.let { return null }

        val body = sms.body
        val template = template(body)

        val merchant = BankRefinements.stripTrailingNoise(draft.rawMerchant)
            ?.takeUnless { BankRefinements.isSelfReference(it, "sbi", "state bank") }
            ?: BankRefinements.vpaCounterparty(body)

        val method = when {
            template == Template.CARD_SPEND -> PaymentMethod.CARD_CREDIT
            draft.paymentMethod == PaymentMethod.UNKNOWN && draft.upiId != null -> PaymentMethod.UPI
            else -> draft.paymentMethod
        }

        val reference = draft.referenceNumber ?: BankRefinements.referenceNumber(body)

        return draft.copy(
            occurredAt = compactDated(draft, sms, context),
            rawMerchant = merchant,
            upiId = draft.upiId ?: BankRefinements.vpaCounterparty(body),
            paymentMethod = method,
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

    /**
     * Re-dates the event from SBI's compact `05Aug25`.
     *
     * The shared date scan tries the `d/m/y` numeric shape across the whole body before the
     * named-month shape, so a numeric date anywhere in the trailer outranks the real one.
     * Preferring the compact date is safe here precisely because it is the form SBI attaches
     * to the event itself.
     */
    private fun compactDated(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): java.time.Instant {
        val referenceYear = sms.receivedAt.atZone(context.zone).year
        val compact = BankRefinements.parseCompactDate(sms.body, referenceYear) ?: return draft.occurredAt
        return BankRefinements.withDate(draft.occurredAt, compact, context.zone)
    }

    private companion object {
        val DEBITED_BY = Regex("""\bdebited\s+by\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
        val CREDITED_BY = Regex("""\bcredited\s+(?:by|with)\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
        val CREDITED_BY_VPA = Regex(
            """\bcredited\b[^\n]{0,40}?\bby\s+[a-zA-Z0-9._\-]{2,64}@[a-zA-Z]{2,20}""",
            RegexOption.IGNORE_CASE,
        )
    }
}
