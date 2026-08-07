package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import javax.inject.Inject

/**
 * Kotak Mahindra Bank alerts.
 *
 * Kotak states direction with `Sent` and `Received` rather than `debited` and `credited`.
 * The shared lexicon knows neither word — deliberately, because "sent" appears in plenty of
 * non-financial messages — so without this refinement every Kotak UPI alert would be
 * rejected for having no direction at all.
 *
 * Kotak also names the counterparty as a bare VPA with no label, and glues its account
 * number to the word `AC` (`Kotak Bank AC X1234`).
 */
class KotakParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "kotak.v1",
        displayName = "Kotak Mahindra Bank",
        version = 1,
        senderCodes = setOf("KOTAKB", "KMBANK", "KOTAK", "KMBLTD"),
        priority = 10,
        description = "Kotak account and UPI alerts, including the Sent/Received direction wording.",
    )

    override val bankCode = "KOTAK"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val body = sms.body.lowercase()
        return when {
            SENT_FROM.containsMatchIn(sms.body) || RECEIVED_IN.containsMatchIn(sms.body) -> 0.12f
            body.contains("kotak bank") || body.contains("-kotak") -> 0.06f
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

        when {
            SENT_FROM.containsMatchIn(body) -> refined = refined.copy(direction = Direction.DEBIT)
            RECEIVED_IN.containsMatchIn(body) -> refined = refined.copy(direction = Direction.CREDIT)
        }

        KOTAK_ACCOUNT.find(body)?.let { match ->
            refined = refined.copy(maskedAccount = "XX${match.groupValues[1]}")
        }

        counterparty(body)?.let { name ->
            refined = refined.copy(rawMerchant = name)
        }

        // A bare VPA in the counterparty slot is unambiguous evidence of the rail, and
        // Kotak's UPI alerts never say "UPI" in prose.
        if (refined.upiId != null) {
            refined = refined.copy(paymentMethod = PaymentMethod.UPI)
        }

        return refined
    }

    /**
     * Reads whatever sits after `to` / `from`, stopping at the date or reference clause.
     * Preferring the VPA when one is present avoids reporting a display name that changes
     * between messages for the same payee.
     */
    private fun counterparty(body: String): String? {
        COUNTERPARTY.find(body)?.let { match ->
            val candidate = match.groupValues[1].trim().trim('.', ',', '-')
            if (candidate.length >= 2 && candidate.any(Char::isLetter)) return candidate
        }
        return null
    }

    private companion object {
        val SENT_FROM = Regex("""\bsent\s+(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
        val RECEIVED_IN = Regex("""\breceived\s+(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
        val KOTAK_ACCOUNT = Regex("""\bac\s*x*(\d{3,6})""", RegexOption.IGNORE_CASE)
        val COUNTERPARTY = Regex(
            """\b(?:to|from)\s+(.{2,60}?)(?:\s+on\b|\s+upi\b|\s+ref\b|[.;\n]|$)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
