package com.lifeledger.sms.parser.life

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TransactionType
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.api.SmsParser
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.lex.SmsPatterns

/**
 * Doctor, clinic, lab/diagnostic and vaccination-slot appointments.
 *
 * Deliberately narrower than [BookingParser]'s travel vocabulary so the two never compete
 * for the same message: this one only fires on clinical wording (doctor, clinic, lab,
 * diagnostic, vaccination), which travel confirmations never use.
 */
class AppointmentParser : SmsParser {

    override val info = ParserInfo(
        id = "appointment",
        displayName = "Appointment",
        version = 1,
        senderCodes = emptySet(),
        priority = 6,
        description = "Doctor, clinic, lab/diagnostic and vaccination appointment confirmations.",
    )

    override fun canHandle(sms: SmsRecord): Boolean = KeywordTable.anyMatch(sms.body, APPOINTMENT_WORDS)

    override fun parse(sms: SmsRecord, context: ParserContext): ParseResult {
        val body = sms.body
        if (Lexicon.looksPromotional(body)) return ParseResult.Ignored(info.id, "promotional content")
        if (!KeywordTable.anyMatch(body, APPOINTMENT_WORDS)) return ParseResult.NotApplicable

        val provider = doctorName(body) ?: clinicAt(body) ?: KeywordTable.firstMatch(body, PROVIDERS)
        val time = SmsPatterns.timeIn(body)

        // A bare mention of "doctor" or "clinic" in passing prose is too weak on its own;
        // require either a named provider or an explicit appointment time before claiming it.
        if (provider == null && time == null) return ParseResult.NotApplicable

        var confidence = 0.5f
        if (provider != null) confidence += 0.2f
        if (time != null) confidence += 0.15f

        return ParseResult.Success(
            ParsedTransaction(
                amount = null,
                type = TransactionType.APPOINTMENT,
                direction = Direction.NEUTRAL,
                paymentMethod = PaymentMethod.UNKNOWN,
                occurredAt = SmsPatterns.instantIn(body, sms.receivedAt, context.zone),
                rawMerchant = provider,
                confidence = Confidence.of(confidence),
                extractedFields = buildMap {
                    provider?.let { put("provider", it) }
                    time?.let { put("appointment_time", it.toString()) }
                },
            ),
            info.id,
        )
    }

    private fun doctorName(body: String): String? =
        DOCTOR.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..40 }

    private fun clinicAt(body: String): String? =
        CLINIC_AT.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 2..40 }

    private companion object {
        val APPOINTMENT_WORDS = listOf(
            "appointment", "consultation", "doctor", "dr.", "clinic", "opd",
            "lab test", "diagnostic", "sample collection", "vaccination", "vaccine slot",
            "cowin", "slot booked for", "your appointment",
        )

        val PROVIDERS = listOf(
            "apollo" to "Apollo",
            "fortis" to "Fortis",
            "max healthcare" to "Max Healthcare",
            "manipal" to "Manipal Hospitals",
            "dr lal pathlabs" to "Dr Lal PathLabs",
            "thyrocare" to "Thyrocare",
            "practo" to "Practo",
            "1mg" to "1mg",
            "cowin" to "CoWIN",
        )

        // "Dr. Mehta at Apollo Clinic" -> "Mehta". Stops at the first of a small set of
        // words that reliably end a name in these templates, or at ordinary punctuation.
        val DOCTOR = Regex(
            """\bDr\.?\s+([A-Za-z][A-Za-z ]{1,35}?)(?:\s+(?:at|on|for|is)\b|[.,;\n]|$)""",
            RegexOption.IGNORE_CASE,
        )

        // "at Apollo Clinic," / "at Thyrocare is" -> the clinic/lab name. Requires a letter
        // as the first captured character so it skips "at 11:00 AM" style time phrases.
        val CLINIC_AT = Regex(
            """\bat\s+([A-Za-z][A-Za-z &'\-]{1,35}?)(?:\s+(?:is|on|for)\b|[.,;\n]|$)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
