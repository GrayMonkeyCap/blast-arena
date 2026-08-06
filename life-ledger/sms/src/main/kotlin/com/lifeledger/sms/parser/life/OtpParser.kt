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
 * Claims OTP-only messages so they land on the timeline as "a code was sent" rather than
 * being left [ParseResult.NotApplicable]. [Lexicon.looksLikeOtp] already excludes messages
 * that also mention a debit/credit ("OTP for a Rs.500 debit"), so this parser only ever
 * sees bare code-delivery messages, never real transaction alerts that happen to carry a
 * verification step.
 *
 * ## Why the code itself never appears in the output
 *
 * The OTP digits are a live, single-use authentication credential at the moment the SMS
 * arrives. Life Ledger's entire premise is "your financial history, never leaving the
 * device" — extending that same on-device store to also retain a working 2FA code (in a
 * field a future export, backup, or screen-share could surface) would turn a convenience
 * feature into a security liability, for a field nobody actually needs the timeline to
 * show: the timeline only needs to know *that* a code was requested, and by whom, not
 * *what* it was. So this parser deliberately never calls [SmsPatterns.referenceNumber] or
 * [SmsPatterns.infoField] — both are built to capture "the digits near a label", which is
 * exactly the shape an OTP has — and records only the boolean fact
 * `"code_present" to "true"` in [ParsedTransaction.extractedFields].
 */
class OtpParser : SmsParser {

    override val info = ParserInfo(
        id = "otp",
        displayName = "OTP",
        version = 1,
        senderCodes = emptySet(),
        priority = 5,
        description = "One-time-password delivery messages, with the code itself redacted.",
    )

    override fun canHandle(sms: SmsRecord): Boolean = Lexicon.looksLikeOtp(sms.body)

    override fun parse(sms: SmsRecord, context: ParserContext): ParseResult {
        val body = sms.body
        if (!Lexicon.looksLikeOtp(body)) return ParseResult.NotApplicable
        if (Lexicon.looksPromotional(body)) return ParseResult.Ignored(info.id, "promotional content")

        val service = serviceName(body) ?: sms.senderCode

        return ParseResult.Success(
            ParsedTransaction(
                amount = null,
                type = TransactionType.OTP,
                direction = Direction.NEUTRAL,
                paymentMethod = PaymentMethod.UNKNOWN,
                occurredAt = SmsPatterns.instantIn(body, sms.receivedAt, context.zone),
                rawMerchant = service,
                confidence = Confidence.HIGH,
                extractedFields = mapOf("code_present" to "true"),
            ),
            info.id,
        )
    }

    /** Best-effort service/issuer name, deliberately built from text only — never digits. */
    private fun serviceName(body: String): String? {
        for (pattern in SERVICE_PATTERNS) {
            val match = pattern.find(body) ?: continue
            val candidate = match.groupValues[1].trim().trim('.', ',', '-', ' ')
            if (candidate.length in 2..40 && candidate.any(Char::isLetter)) return candidate
        }
        return null
    }

    private companion object {
        val SERVICE_PATTERNS = listOf(
            // "OTP for Amazon login", "verification code for SBI transaction is ..."
            Regex(
                """(?:otp|verification code|security code)\s+(?:for|to)\s+([A-Za-z0-9&.' \-]{2,40}?)(?:\s+(?:is|login|purchase|transaction|txn|registration|order|verification)\b|[.:,\n]|$)""",
                RegexOption.IGNORE_CASE,
            ),
            // "for HDFC NetBanking OTP" style, service named before the OTP word.
            Regex(
                """\bfor\s+([A-Za-z0-9&.' \-]{2,40}?)\s+(?:otp|verification code|login)\b""",
                RegexOption.IGNORE_CASE,
            ),
            // Trailing sender signature: "... do not share. -Flipkart"
            Regex("""-\s*([A-Za-z][A-Za-z0-9&.' \-]{1,30}?)\.?\s*$"""),
        )
    }
}
