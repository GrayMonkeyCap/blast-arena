package com.lifeledger.sms.parser.wallets

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TransactionType
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Credit-card alerts from *any* issuer: spends, limit notices and statement/bill-due
 * reminders.
 *
 * Bank-specific parsers claim their own sender codes first; this one is content-matched
 * (empty [ParserInfo.senderCodes]) and priority 30, so it only fires when nothing more
 * specific wanted the message — [canHandle] still requires an explicit credit-card signal
 * so it does not swallow ordinary debit-card or bank-transfer alerts that reach it.
 *
 * Two corrections it makes beyond the generic bank-parser extraction:
 * - "Avl Lmt" / "Available limit" is a *credit limit*, not a balance. Recording it in
 *   [ParsedTransaction.balanceAfter] would make the timeline show a spend as though it left
 *   a shrinking pool of the user's own money, which is wrong for a credit facility — it goes
 *   into [ParsedTransaction.extractedFields] as `available_limit` instead.
 * - Statement/bill-due messages state a figure that has *not yet moved* — it is a future
 *   obligation, not a transaction. [BaseBankParser] cannot tell the two apart on its own: it
 *   requires a non-neutral [Direction] before it will even build a draft, which in practice
 *   means the message also has to contain debit/credit vocabulary (real templates usually
 *   qualify — "unpaid dues will be auto-debited" — because most issuers mention their
 *   auto-debit mandate in the same reminder). When the statement shape is recognised, this
 *   parser rewrites the draft to [TransactionType.BILL_DUE] with `amount = null`, surfacing
 *   the total/minimum due as fields instead of as a movement of money.
 */
class CreditCardParser : BaseBankParser() {

    override val info = ParserInfo(
        id = "credit-card",
        displayName = "Credit Card",
        version = 1,
        senderCodes = emptySet(),
        priority = 30,
        description = "Credit card spends, limit notices and statement/bill-due reminders from any issuer.",
    )

    override val bankCode = "CARD"

    override fun canHandle(sms: SmsRecord): Boolean {
        val lower = sms.body.lowercase()
        if (CREDIT_CARD_MARKERS.any { lower.contains(it) }) return true
        return CARD_WORD.containsMatchIn(lower) && LIMIT_WORD.containsMatchIn(lower)
    }

    override fun confidenceBonus(sms: SmsRecord): Float {
        val lower = sms.body.lowercase()
        var bonus = 0f
        if (CREDIT_CARD_MARKERS.any { lower.contains(it) }) bonus += 0.1f
        if (LIMIT_WORD.containsMatchIn(lower)) bonus += 0.05f
        if (STATEMENT_MARKERS.any { lower.contains(it) }) bonus += 0.05f
        return bonus
    }

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction {
        val body = sms.body
        var result = draft.copy(paymentMethod = PaymentMethod.CARD_CREDIT)

        AVAILABLE_LIMIT.find(body)?.groupValues?.get(1)?.trim()?.let { limit ->
            result = result.copy(
                balanceAfter = null,
                extractedFields = result.extractedFields + ("available_limit" to limit),
            )
        }

        if (STATEMENT_MARKERS.any { body.lowercase().contains(it) }) {
            val fields = buildMap {
                putAll(result.extractedFields)
                TOTAL_DUE.find(body)?.groupValues?.get(1)?.trim()?.let { put("total_due", it) }
                MIN_DUE.find(body)?.groupValues?.get(1)?.trim()?.let { put("min_due", it) }
            }
            result = result.copy(
                amount = null,
                type = TransactionType.BILL_DUE,
                direction = Direction.NEUTRAL,
                balanceAfter = null,
                extractedFields = fields,
            )
        }

        return result
    }

    private companion object {
        val CREDIT_CARD_MARKERS = listOf("credit card", "creditcard", "credit crd")
        val CARD_WORD = Regex("""\bcard\b""", RegexOption.IGNORE_CASE)
        val LIMIT_WORD = Regex("""avl\.?\s*lmt|available\s*limit""", RegexOption.IGNORE_CASE)
        val STATEMENT_MARKERS = listOf(
            "statement generated", "statement is generated", "total amt due", "total amount due",
            "minimum amount due", "min amount due", "min due",
        )
        val AVAILABLE_LIMIT = Regex(
            """(?:avl\.?\s*lmt|available\s*limit|avl\s*limit)\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val TOTAL_DUE = Regex(
            """total\s*am(?:oun)?t\.?\s*due\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val MIN_DUE = Regex(
            """min(?:imum)?\.?\s*am(?:oun)?t\.?\s*due\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
