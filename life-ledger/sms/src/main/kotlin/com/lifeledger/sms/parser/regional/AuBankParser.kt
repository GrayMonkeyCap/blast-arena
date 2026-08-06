package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * AU Small Finance Bank alerts.
 *
 * AU Bank packs the UPI reference and counterparty into a slash-delimited `Info:` field —
 * `Info: UPI/DR/302345678901/Swiggy/YESB` — rather than the `Ref <digits>` shape the shared
 * reference patterns look for. Without a literal `ref`/`rrn` keyword next to the digits,
 * [com.lifeledger.sms.lex.SmsPatterns.referenceNumber] finds nothing, so this parser reads
 * the RRN and counterparty straight out of the `Info:` field instead.
 */
class AuBankParser : BaseBankParser() {

    override val bankCode: String = "AUBL"

    override val info = ParserInfo(
        id = "au_bank",
        displayName = "AU Small Finance Bank",
        version = 1,
        senderCodes = setOf("AUBANK", "AUSFBL"),
        priority = 20,
        description = "Parses AU Small Finance Bank SMS alerts, decoding the slash-" +
            "delimited 'Info: UPI/<type>/<rrn>/<counterparty>' field.",
    )

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? {
        val match = UPI_INFO.find(sms.body) ?: return draft
        val rrn = match.groupValues[1]
        val merchant = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        return draft.copy(
            referenceNumber = rrn,
            transactionId = rrn,
            rawMerchant = merchant ?: draft.rawMerchant,
        )
    }

    override fun confidenceBonus(sms: SmsRecord): Float =
        if (SIGNATURE.containsMatchIn(sms.body)) 0.1f else 0f

    private companion object {
        // Shape: UPI/<DR|CR|...>/<rrn>/<counterparty>[/<bank code>] — the counterparty
        // segment is optional and, when present, stops at the next '/', a trailing
        // " on <date>", or end of string, so the bank-code tail never leaks into it.
        val UPI_INFO = Regex(
            """UPI/[A-Za-z]{2,4}/(\d{6,18})(?:/([A-Za-z0-9 &'.\-]{2,45}?)(?:/|\s+on\b|$))?""",
            RegexOption.IGNORE_CASE,
        )
        val SIGNATURE = Regex("""\bAU\s+Bank\b""", RegexOption.IGNORE_CASE)
    }
}
