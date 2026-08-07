package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * IDFC FIRST Bank alerts.
 *
 * IDFC writes the closing balance as `New Bal :INR 12,345` — a label the shared balance
 * pattern does not recognise, and one placed close enough to the transacted amount that
 * missing it risks the wrong figure being taken as the transaction.
 *
 * Its UPI alerts put the counterparty inside an `Info:` narration in the same style as
 * HDFC's core banking, so the narration has to be split rather than read as prose.
 */
class IdfcParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "idfc.v1",
        displayName = "IDFC FIRST Bank",
        version = 1,
        senderCodes = setOf("IDFCFB", "IDFCBK", "IDFBNK"),
        priority = 10,
        description = "IDFC FIRST account and UPI alerts, including the New Bal balance label.",
    )

    override val bankCode = "IDFC"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val body = sms.body.lowercase()
        return when {
            NEW_BALANCE.containsMatchIn(sms.body) -> 0.12f
            body.contains("idfc first") || body.contains("-idfc") -> 0.06f
            else -> 0f
        }
    }

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction {
        val body = sms.body
        var refined = draft

        NEW_BALANCE.find(body)?.let { match ->
            Money.parse(match.groupValues[1], context.defaultCurrency)?.let { balance ->
                refined = refined.copy(balanceAfter = balance)
                // If the generic extractor mistook the balance for the transacted amount,
                // fall back to the first amount that is not the balance.
                if (refined.amount == balance) {
                    val alternative = com.lifeledger.sms.lex.SmsPatterns
                        .amounts(body, context.defaultCurrency)
                        .firstOrNull { it != balance }
                    if (alternative != null) refined = refined.copy(amount = alternative)
                }
            }
        }

        narrationCounterparty(body)?.let { name ->
            refined = refined.copy(rawMerchant = name)
        }

        return refined
    }

    /**
     * Splits an `Info: UPI/512345678901/Payment to SWIGGY` narration and returns the
     * longest alphabetic segment, which is reliably the human-readable counterparty —
     * the other segments are rails, reference numbers and boilerplate.
     */
    private fun narrationCounterparty(body: String): String? {
        val narration = INFO_FIELD.find(body)?.groupValues?.get(1) ?: return null
        return narration.split('/', '-')
            .map { it.trim() }
            .filter { segment -> segment.count(Char::isLetter) >= 3 }
            .filterNot { segment -> segment.lowercase() in NARRATION_NOISE }
            .maxByOrNull { it.count(Char::isLetter) }
            ?.takeIf { it.length >= 2 }
    }

    private companion object {
        val NEW_BALANCE = Regex(
            """new\s+bal(?:ance)?\s*:?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val INFO_FIELD = Regex("""\binfo\s*:?\s*(.{3,80}?)(?:\.\s|$)""", RegexOption.IGNORE_CASE)

        val NARRATION_NOISE = setOf(
            "upi", "neft", "imps", "rtgs", "payment", "paymentto", "txn", "ref",
            "transfer", "collect", "mandate", "idfc", "bank",
        )
    }
}
