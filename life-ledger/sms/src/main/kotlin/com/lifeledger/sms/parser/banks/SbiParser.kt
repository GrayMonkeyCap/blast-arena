package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * State Bank of India alerts.
 *
 * SBI is the highest-volume issuer in the country and also the most idiosyncratic:
 *
 *  - dates are written with no separators at all (`05Aug26`), which the shared date
 *    patterns cannot see, so the event silently falls back to the SMS receipt time;
 *  - the account is written `A/cX1234` with a single `X` and no space, defeating the
 *    generic masked-account pattern that expects at least two mask characters;
 *  - the payee follows `transfer to`, a phrase no other bank uses, and SBI writes
 *    `debited by Rs.X` rather than the near-universal `Rs.X debited`;
 *  - SBI Card sends from a different short code with a completely different template.
 */
class SbiParser @Inject constructor() : BaseBankParser() {

    override val info = ParserInfo(
        id = "sbi.v1",
        displayName = "State Bank of India",
        version = 1,
        senderCodes = setOf("SBIINB", "SBIPSG", "SBICRD", "ATMSBI", "SBIUPI", "CBSSBI", "SBIBNK"),
        priority = 10,
        description = "SBI account, UPI, ATM and SBI Card alerts, including the separator-free DDMMMYY date format.",
    )

    override val bankCode = "SBI"

    override fun confidenceBonus(sms: SmsRecord): Float {
        val body = sms.body.lowercase()
        return when {
            body.contains("transfer to") && COMPACT_DATE.containsMatchIn(sms.body) -> 0.12f
            body.contains("sbi credit card") || body.contains("sbi card") -> 0.12f
            body.contains("dear sbi user") || body.contains("-sbi") -> 0.08f
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

        SBI_ACCOUNT.find(body)?.let { match ->
            refined = refined.copy(maskedAccount = "XX${match.groupValues[1]}")
        }

        transferPayee(body)?.let { payee ->
            refined = refined.copy(rawMerchant = payee)
        }

        // SBI Card spends never mention a rail, and the base lexicon would leave them
        // UNKNOWN; the card wording is the only signal there is.
        if (body.contains("credit card", ignoreCase = true)) {
            refined = refined.copy(paymentMethod = PaymentMethod.CARD_CREDIT)
            CARD_MERCHANT.find(body)?.let { match ->
                refined = refined.copy(rawMerchant = match.groupValues[1].trim())
            }
        }

        compactDate(body, context)?.let { instant ->
            refined = refined.copy(occurredAt = instant)
        }

        // "debited by" reads as a debit to a human and to nobody else; the shared lexicon
        // scores "credited to" later in the same sentence and can pick the wrong side.
        if (DEBITED_BY.containsMatchIn(body)) {
            refined = refined.copy(direction = Direction.DEBIT)
        }

        return refined
    }

    /**
     * SBI writes the payee after `transfer to`, and terminates it with `Ref No`, a full
     * stop, or the end of the message. The trailing `-SBI` signature has to be stripped
     * explicitly because it is glued to the payee in several templates.
     */
    private fun transferPayee(body: String): String? =
        TRANSFER_TO.find(body)?.groupValues?.get(1)
            ?.substringBefore("Ref")
            ?.substringBefore("-SBI")
            ?.trim()
            ?.trim('.', ',', '-')
            ?.takeIf { it.length >= 2 && it.any(Char::isLetter) }

    /**
     * Parses SBI's `05Aug26` / `05Aug2026` date. Two-digit years are read as 20xx: the app
     * has no meaningful history before 2000 and SMS retention rarely exceeds a decade.
     */
    private fun compactDate(body: String, context: ParserContext): java.time.Instant? {
        val match = COMPACT_DATE.find(body) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = MONTHS[match.groupValues[2].lowercase()] ?: return null
        val rawYear = match.groupValues[3].toIntOrNull() ?: return null
        val year = if (rawYear < 100) 2000 + rawYear else rawYear

        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val time = com.lifeledger.sms.lex.SmsPatterns.timeIn(body)
            ?: context.fallbackInstant.atZone(context.zone).toLocalTime()
        return LocalDateTime.of(date, time).atZone(context.zone).toInstant()
    }

    private companion object {
        val SBI_ACCOUNT = Regex("""a/?c\s*x+(\d{3,6})""", RegexOption.IGNORE_CASE)
        val TRANSFER_TO = Regex("""transfer\s+to\s+(.{2,60}?)(?:\s+ref\b|[.;\n]|$)""", RegexOption.IGNORE_CASE)
        val CARD_MERCHANT = Regex("""\bat\s+([A-Za-z0-9&@'.\- ]{2,45}?)(?:\s+on\b|[.;,\n]|$)""", RegexOption.IGNORE_CASE)
        val COMPACT_DATE = Regex("""\b(\d{1,2})([A-Za-z]{3})(\d{2,4})\b""")
        val DEBITED_BY = Regex("""debited\s+by""", RegexOption.IGNORE_CASE)

        val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )
    }
}
