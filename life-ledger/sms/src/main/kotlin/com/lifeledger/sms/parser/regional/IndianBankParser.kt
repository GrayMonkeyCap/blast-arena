package com.lifeledger.sms.parser.regional

import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.parser.BaseBankParser

/**
 * Indian Bank alerts.
 *
 * Indian Bank's alerts are the plainest shape in this family — `Rs.X debited/credited
 * from/to your a/c XXNNNN on DD-MM-YYYY. Bal Rs.Y -Indian Bank` — with no counterparty
 * field at all in the majority of real messages, so the generic extraction needs no
 * correction here beyond recognising the bank's own signature for the confidence bonus.
 *
 * `senderCodes` includes `ALLBNK`: Allahabad Bank was merged into Indian Bank in 2020 and
 * its legacy sender ids are still seen on live SIMs years later, so alerts through that
 * header are routed here rather than being left unmatched.
 */
class IndianBankParser : BaseBankParser() {

    override val bankCode: String = "IDIB"

    override val info = ParserInfo(
        id = "indian_bank",
        displayName = "Indian Bank",
        version = 1,
        senderCodes = setOf("INDBNK", "INDIAN", "ALLBNK"),
        priority = 20,
        description = "Parses Indian Bank debit/credit SMS alerts, including legacy " +
            "Allahabad Bank (ALLBNK) headers routed here since the 2020 merger.",
    )

    override fun confidenceBonus(sms: SmsRecord): Float =
        if (SIGNATURE.containsMatchIn(sms.body)) 0.1f else 0f

    private companion object {
        val SIGNATURE = Regex("""indian\s*bank""", RegexOption.IGNORE_CASE)
    }
}
