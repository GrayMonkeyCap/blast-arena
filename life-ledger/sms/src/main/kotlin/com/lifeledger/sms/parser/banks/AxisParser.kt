package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * Axis Bank alerts.
 *
 * The correction that matters most here is `Avl Lmt`. Axis card alerts end with the
 * *available credit limit*, in exactly the position and phrasing other banks use for an
 * account balance. Recording it as a balance would be actively wrong: the dashboard would
 * report a card's remaining spending headroom as money the user has.
 *
 * Axis also writes card spends as `Spent Card no. XX1234 INR 450 05-08-26 MERCHANT` — a
 * bare sequence with no prepositions, which none of the shared merchant patterns can read.
 */
class AxisParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "axis.v1",
        displayName = "Axis Bank",
        version = 1,
        senderCodes = setOf("AXISBK", "AXISB", "AXISBK", "AXIBNK"),
        priority = 10,
        description = "Axis Bank account, UPI and card alerts, including the Avl Lmt credit-limit form.",
    )

    override val bankCode = "AXIS"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val body = sms.body.lowercase()
        return when {
            SPENT_CARD.containsMatchIn(sms.body) -> 0.12f
            body.contains("upi/p2m") || body.contains("upi/p2a") -> 0.1f
            body.contains("axis bank") || body.contains("-axis") -> 0.06f
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

        AVAILABLE_LIMIT.find(body)?.let { match ->
            val limit = Money.parse(match.groupValues[1], context.defaultCurrency)
            refined = refined.copy(
                // Explicitly clear it: the shared balance pattern matches "Avl Lmt" too.
                balanceAfter = null,
                paymentMethod = PaymentMethod.CARD_CREDIT,
                extractedFields = refined.extractedFields +
                    mapOf("availableLimit" to (limit?.minor?.toString() ?: match.groupValues[1])),
            )
        }

        SPENT_CARD.find(body)?.let { match ->
            refined = refined.copy(
                maskedAccount = "XX${match.groupValues[1]}",
                paymentMethod = PaymentMethod.CARD_CREDIT,
            )
            trailingMerchant(body)?.let { refined = refined.copy(rawMerchant = it) }
        }

        // Axis encodes the counterparty in the UPI narration rather than in prose.
        UPI_NARRATION.find(body)?.let { match ->
            val payee = match.groupValues[1].trim().trim('.', '-')
            if (payee.length >= 2 && payee.any(Char::isLetter)) {
                refined = refined.copy(
                    rawMerchant = refined.rawMerchant ?: payee,
                    paymentMethod = PaymentMethod.UPI,
                )
            }
        }

        return refined
    }

    /**
     * In the bare `Spent Card no. XX1234 INR 450 05-08-26 STARBUCKS` form the merchant is
     * simply whatever follows the date, so it is read positionally rather than by keyword.
     */
    private fun trailingMerchant(body: String): String? =
        AFTER_DATE.find(body)?.groupValues?.get(1)
            ?.substringBefore("Avl")
            ?.substringBefore("SMS")
            ?.trim()
            ?.trim('.', ',', '-')
            ?.takeIf { it.length >= 2 && it.any(Char::isLetter) }

    private companion object {
        val AVAILABLE_LIMIT = Regex(
            """avl(?:\.|ailable)?\s*(?:lmt|limit)\s*:?\s*(?:inr|rs\.?|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val SPENT_CARD = Regex(
            """spent\s+card\s+no\.?\s*(?:x+|\*+)?\s*(\d{4})""",
            RegexOption.IGNORE_CASE,
        )
        val AFTER_DATE = Regex(
            """\d{1,2}[-/]\d{1,2}[-/]\d{2,4}\s+(.{2,45}?)(?:\s+avl|[.;\n]|$)""",
            RegexOption.IGNORE_CASE,
        )
        val UPI_NARRATION = Regex(
            """upi/(?:p2m|p2a)/\d+/([A-Za-z0-9 &.'\-]{2,45})""",
            RegexOption.IGNORE_CASE,
        )
    }
}
