package com.lifeledger.sms.parser

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TransactionType
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.api.SmsParser
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.lex.SmsPatterns

/**
 * Shared machinery for bank-alert parsers.
 *
 * Indian bank SMS varies far less than it appears: nearly every alert is "amount + direction
 * + account + counterparty + reference", differing only in wording and field order. This
 * base class implements that common extraction once and gives subclasses two narrow places
 * to specialise — [bankCode] for identity and [refine] for the handful of formats that are
 * genuinely bank-specific.
 *
 * A subclass that needs to reject a message entirely returns `null` from [refine].
 */
abstract class BaseBankParser : SmsParser {

    /** Short bank identifier stored on every transaction this parser produces, e.g. `HDFC`. */
    protected abstract val bankCode: String

    /**
     * Hook for bank-specific corrections, applied after generic extraction.
     *
     * Return the transaction unchanged to accept the generic result, a modified copy to
     * correct it, or `null` to declare the message not a transaction after all.
     */
    protected open fun refine(
        draft: ParsedTransaction,
        sms: SmsRecord,
        context: ParserContext,
    ): ParsedTransaction? = draft

    /**
     * Extra confidence for a subclass that recognised its bank's exact template.
     * Added to the base score and clamped to 1.0.
     */
    protected open fun confidenceBonus(sms: SmsRecord): Float = 0f

    final override fun parse(sms: SmsRecord, context: ParserContext): ParseResult {
        val body = sms.body

        if (Lexicon.looksPromotional(body)) {
            return ParseResult.Ignored(info.id, "promotional content")
        }
        if (Lexicon.looksLikeOtp(body)) {
            return ParseResult.Ignored(info.id, "OTP-only message")
        }

        val direction = Lexicon.direction(body)
        val amount = SmsPatterns.primaryAmount(body, context.defaultCurrency)

        if (amount == null || direction == Direction.NEUTRAL) {
            // No money and no direction means there is nothing for a *bank* parser to record.
            // Non-financial life events are handled by their own dedicated parsers.
            return ParseResult.NotApplicable
        }

        val type = Lexicon.transactionType(body, direction)
        val paymentMethod = Lexicon.paymentMethod(body)
        val maskedAccount = SmsPatterns.maskedAccount(body) ?: SmsPatterns.maskedCard(body)

        val draft = ParsedTransaction(
            amount = amount,
            type = type,
            direction = direction,
            paymentMethod = paymentMethod,
            occurredAt = SmsPatterns.instantIn(body, sms.receivedAt, context.zone),
            rawMerchant = SmsPatterns.rawMerchant(body),
            maskedAccount = maskedAccount,
            bankCode = bankCode,
            upiId = SmsPatterns.upiId(body),
            balanceAfter = SmsPatterns.balance(body, context.defaultCurrency),
            referenceNumber = SmsPatterns.referenceNumber(body),
            transactionId = SmsPatterns.referenceNumber(body),
            description = SmsPatterns.infoField(body),
            instrumentType = Lexicon.instrumentType(body),
            billType = Lexicon.billType(body),
            confidence = Confidence.of(baseConfidence(amount, maskedAccount, type) + confidenceBonus(sms)),
            extractedFields = buildMap {
                maskedAccount?.let { put("account", it) }
                SmsPatterns.referenceNumber(body)?.let { put("reference", it) }
                SmsPatterns.upiId(body)?.let { put("upi", it) }
                SmsPatterns.rawMerchant(body)?.let { put("merchant", it) }
                put("direction", direction.name)
                put("bank", bankCode)
            },
        )

        val refined = refine(draft, sms, context)
            ?: return ParseResult.Ignored(info.id, "rejected by ${info.id} refinement")

        return ParseResult.Success(refined, info.id)
    }

    /**
     * Base score from how much of the message we actually understood.
     *
     * An amount alone is a weak signal; an amount tied to a known account and a specific
     * type is strong. Scores are intentionally conservative so that anything below
     * [Confidence.MIN_ACTIONABLE] surfaces for review rather than silently skewing totals.
     */
    private fun baseConfidence(
        amount: Money,
        maskedAccount: String?,
        type: TransactionType,
    ): Float {
        var score = 0.55f
        if (amount.minor > 0) score += 0.1f
        if (maskedAccount != null) score += 0.15f
        if (type != TransactionType.PURCHASE && type != TransactionType.TRANSFER_IN) score += 0.1f
        return score
    }
}
