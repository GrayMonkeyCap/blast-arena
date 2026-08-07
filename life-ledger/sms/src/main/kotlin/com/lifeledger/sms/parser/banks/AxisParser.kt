package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TransactionType
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * Axis Bank alerts.
 *
 * Axis writes the tersest alerts of the six, with fields separated by spaces and newlines
 * rather than prose, and that terseness breaks generic extraction in two specific ways.
 *
 * `Spent Card no. XX1234 INR 1,250.00 05-08-25 AMAZON Avl Lmt INR 48,750.00` puts the card
 * tail immediately before a currency word, so the shared "amount with a trailing currency"
 * pattern reads `1234 INR` as an amount — and because it appears earlier in the string than
 * the real figure, the transaction is booked for ₹1,234 instead of ₹1,250. Restricting this
 * template to currency-prefixed amounts fixes it.
 *
 * `Avl Lmt` is the second: it is a credit limit, not a balance. Anything that lands in
 * `balanceAfter` is treated downstream as the account's money, so a limit recorded there
 * would corrupt balance history and every figure derived from it. It is surfaced in
 * `extractedFields` instead.
 *
 * Axis also spells out the UPI purpose code — `UPI/P2M/…` for a merchant payment, `UPI/P2A/…`
 * for a transfer to a person — which is a far better signal than any keyword guess, and the
 * only place in these six banks where the rail states intent directly.
 */
class AxisParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "axis.v1",
        displayName = "Axis Bank",
        version = 1,
        // Sender codes are matched against an uppercased sender, so they are declared
        // uppercase; `AXISB` alone would also cover `AXISBK`, but both are listed because
        // this set doubles as documentation in Settings › Parser Management.
        senderCodes = setOf("AXISBK", "AXISB", "AXISBANK"),
        priority = 10,
        description = "Axis Bank account, UPI and card alerts, including the terse card format and Avl Lmt handling.",
    )

    override val bankCode = "AXIS"

    private enum class Template(val bonus: Float) {
        CARD_SPEND(0.12f),
        UPI_RAIL(0.12f),
        ACCOUNT_MOVE(0.1f),
        HOUSE_STYLE(0.04f),
        NONE(0f),
    }

    override fun confidenceBonus(sms: SmsRecord): Float = template(sms.body).bonus

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? = runCatching { correct(draft, sms.body, context) }.getOrElse { draft }

    private fun template(body: String): Template {
        val lower = body.lowercase()
        return when {
            SPENT_CARD.containsMatchIn(body) -> Template.CARD_SPEND
            UPI_RAIL.containsMatchIn(body) -> Template.UPI_RAIL
            ACCOUNT_MOVE.containsMatchIn(body) -> Template.ACCOUNT_MOVE
            lower.contains("axis") -> Template.HOUSE_STYLE
            else -> Template.NONE
        }
    }

    private fun correct(
        draft: ParsedTransaction,
        body: String,
        context: ParserContext,
    ): ParsedTransaction? {
        BankRefinements.rejectionReason(body)?.let { return null }

        val template = template(body)
        val limit = BankRefinements.availableLimit(body, context.defaultCurrency)
        val rail = UPI_RAIL.find(body)

        val amount = correctedAmount(draft, body, context.defaultCurrency, limit) ?: draft.amount

        val merchant = when (template) {
            Template.CARD_SPEND -> BankRefinements.stripTrailingNoise(
                CARD_MERCHANT.find(body)?.groupValues?.get(1),
            )
            Template.UPI_RAIL -> BankRefinements.stripTrailingNoise(rail?.groupValues?.get(3))
            else -> null
        }
            ?: BankRefinements.stripTrailingNoise(draft.rawMerchant)
                ?.takeUnless { BankRefinements.isSelfReference(it, "axis") }
            ?: BankRefinements.vpaCounterparty(body)

        val method = when {
            template == Template.CARD_SPEND -> cardMethod(body, limit)
            rail != null -> PaymentMethod.UPI
            else -> draft.paymentMethod
        }

        val reference = draft.referenceNumber
            ?: rail?.groupValues?.get(2)
            ?: BankRefinements.referenceNumber(body)

        val extra = buildMap {
            limit?.let { put("availableLimit", it.minor.toString()) }
            rail?.groupValues?.get(1)?.uppercase()?.let { put("upiPurpose", it) }
        }

        return draft.copy(
            amount = amount,
            type = railType(draft, rail?.groupValues?.get(1)),
            paymentMethod = method,
            rawMerchant = merchant,
            referenceNumber = reference,
            transactionId = draft.transactionId ?: reference,
            balanceAfter = draft.balanceAfter?.takeIf { BankRefinements.statesRealBalance(body) },
        ).withParserFields(template.name, extra)
    }

    /**
     * Recovers the transacted figure when the generic pass picked a number that was never
     * written as money — the card-tail-before-`INR` trap. Deliberately conservative: if the
     * draft amount does appear as a currency-prefixed figure, it is left alone, so ordinary
     * Axis messages take no risk from this correction.
     */
    private fun correctedAmount(
        draft: ParsedTransaction,
        body: String,
        currency: String,
        limit: Money?,
    ): Money? {
        val prefixed = BankRefinements.prefixedAmounts(body, currency)
        if (prefixed.isEmpty() || draft.amount in prefixed) return null
        return prefixed.firstOrNull { it != limit && it != draft.balanceAfter }
    }

    /**
     * `P2A` is Axis stating that the money went to a person's account rather than a
     * merchant, which is exactly the distinction `TRANSFER_OUT` versus `PURCHASE` exists to
     * record. Only the generic defaults are overridden — a keyword-derived type such as
     * `EMI` or `SALARY` knows more than the purpose code does.
     */
    private fun railType(draft: ParsedTransaction, purpose: String?): TransactionType {
        if (!purpose.equals("P2A", ignoreCase = true)) return draft.type
        return when (draft.type) {
            TransactionType.PURCHASE -> TransactionType.TRANSFER_OUT
            TransactionType.TRANSFER_IN -> TransactionType.TRANSFER_IN
            else -> draft.type
        }
    }

    /** Only a credit card carries a limit; Axis omits the product word on both card types. */
    private fun cardMethod(body: String, limit: Money?): PaymentMethod {
        val lower = body.lowercase()
        return when {
            lower.contains("credit card") -> PaymentMethod.CARD_CREDIT
            lower.contains("debit card") -> PaymentMethod.CARD_DEBIT
            limit != null -> PaymentMethod.CARD_CREDIT
            else -> PaymentMethod.CARD_DEBIT
        }
    }

    private companion object {
        val SPENT_CARD = Regex("""\bspent\b[^\n]{0,20}\bcard\s*(?:no\.?)?""", RegexOption.IGNORE_CASE)
        val UPI_RAIL = Regex(
            """\bUPI/(P2[MAP])/(\d{6,18})/([^\n/]{2,45})""",
            RegexOption.IGNORE_CASE,
        )
        val ACCOUNT_MOVE = Regex(
            """\b(?:debited|credited)\b[^\n]{0,20}\ba/?c\s*no\.?""",
            RegexOption.IGNORE_CASE,
        )
        val CARD_MERCHANT = Regex(
            """\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\s*(?:\r?\n)?\s*([A-Za-z0-9][A-Za-z0-9 &.'*_\-]{1,45}?)\s*(?:\r?\n)?\s*(?:avl|avbl|available)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
