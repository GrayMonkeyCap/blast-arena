package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Federal Bank alerts.
 *
 * Federal's `towards <counterparty>` shape usually matches the shared merchant patterns
 * fine, but its loan/EMI alerts write the counterparty as `towards EMI for Loan A/c
 * XX7788` — and the generic patterns' character class deliberately excludes `/` (so a
 * masked account elsewhere in a sentence can't be swallowed into a merchant name), which
 * means it can't cross the `A/c` inside this one either and the match fails outright. This
 * parser re-extracts with a class that allows `/`, so EMI-for-loan alerts still get a
 * merchant description instead of `null`.
 */
class FederalBankParser : BaseBankParser() {

    override val bankCode: String = "FDRL"

    override val info = ParserInfo(
        id = "federal_bank",
        displayName = "Federal Bank",
        version = 1,
        senderCodes = setOf("FEDBNK", "FEDERAL"),
        priority = 20,
        description = "Parses Federal Bank debit/credit SMS alerts, including the " +
            "'towards EMI for Loan A/c ...' shape the generic merchant patterns miss.",
    )

    override fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? {
        val merchant = TOWARDS_WITH_SLASH.find(sms.body)?.groupValues?.get(1)?.trim()
        return if (merchant != null) draft.copy(rawMerchant = merchant) else draft
    }

    override fun confidenceBonus(sms: SmsRecord): Float =
        if (SIGNATURE.containsMatchIn(sms.body)) 0.1f else 0f

    private companion object {
        val TOWARDS_WITH_SLASH = Regex(
            """towards\s+([A-Za-z0-9&'./\- ]{2,60}?)(?:\s+on\b|[.;,\n]|$)""",
            RegexOption.IGNORE_CASE,
        )
        val SIGNATURE = Regex("""federal\s*bank""", RegexOption.IGNORE_CASE)
    }
}
