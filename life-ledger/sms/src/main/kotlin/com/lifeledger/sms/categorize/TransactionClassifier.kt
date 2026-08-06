package com.lifeledger.sms.categorize

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.sms.merchant.MerchantResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of classifying one transaction.
 *
 * [reason] is surfaced verbatim in Developer Mode — it exists so a user (or the developer
 * debugging a wrong category) can see *why* a category was chosen without reading code, so
 * every branch in [TransactionClassifier] must produce a sentence, not a code.
 */
data class ClassificationResult(
    val category: TxnCategory,
    val subcategory: String?,
    val confidence: Confidence,
    val reason: String,
)

/**
 * Decides the spending category for a parsed transaction.
 *
 * Signals are tried in a fixed priority order, each one strictly more trustworthy than the
 * next:
 *
 * 1. **Explicit category on the [ParsedTransaction]** — a handful of bank-specific parsers
 *    can read the category straight out of structured statement data; when a parser already
 *    knows, guessing again would only introduce risk.
 * 2. **Merchant catalogue's default category** — resolving `rawMerchant` through
 *    [MerchantResolver] and trusting its category, because the catalogue is hand-curated and
 *    "who did I pay" is usually a stronger signal than "what did the message say".
 * 3. **[CategoryRules] keyword hit on the raw SMS body** — used when the merchant is unknown
 *    or absent (a plain bank transfer, an ATM narration): the message text itself is the
 *    next-best evidence.
 * 4. **Derivation from [TransactionType]** — when neither the merchant nor the wording says
 *    anything domain-specific, the *kind* of transaction the parser already identified
 *    (SIP, EMI, salary credit...) still implies a category more often than not.
 * 5. **[TxnCategory.UNCATEGORIZED]** — the honest fallback. Life Ledger never invents a
 *    category it has no evidence for; an uncategorized transaction is a prompt for the user
 *    to correct it, which is how the catalogue and rules improve over time.
 */
@Singleton
class TransactionClassifier @Inject constructor(
    private val merchantResolver: MerchantResolver,
) {

    fun classify(parsed: ParsedTransaction, smsBody: String): ClassificationResult {
        parsed.category?.let { explicit ->
            return ClassificationResult(
                category = explicit,
                subcategory = null,
                confidence = Confidence.CERTAIN,
                reason = "The parser read an explicit category directly from the message.",
            )
        }

        val merchantName = parsed.rawMerchant
        if (!merchantName.isNullOrBlank()) {
            val resolution = merchantResolver.resolve(merchantName)
            resolution.category?.let { category ->
                return ClassificationResult(
                    category = category,
                    subcategory = resolution.subcategory,
                    confidence = resolution.confidence,
                    reason = "Merchant \"${resolution.canonicalName}\" is catalogued under ${category.displayName}.",
                )
            }
        }

        CategoryRules.match(smsBody)?.let { rule ->
            return ClassificationResult(
                category = rule.category,
                subcategory = rule.subcategory,
                confidence = Confidence.MEDIUM,
                reason = "The message text matched a keyword rule for ${rule.category.displayName}.",
            )
        }

        deriveFromType(parsed.type)?.let { (category, subcategory) ->
            return ClassificationResult(
                category = category,
                subcategory = subcategory,
                confidence = Confidence.LOW,
                reason = "No merchant or keyword match; inferred from the transaction type (${parsed.type.name}).",
            )
        }

        return ClassificationResult(
            category = TxnCategory.UNCATEGORIZED,
            subcategory = null,
            confidence = Confidence.GUESS,
            reason = "No merchant, keyword or transaction-type signal matched; left for manual review.",
        )
    }

    /**
     * Category implied purely by *what kind* of transaction this is, independent of who it
     * was with. Non-financial types ([TransactionType.isFinancial] false) and [TransactionType.PURCHASE]
     * carry no category opinion of their own — a purchase's category should come from the
     * merchant or the message, not the fact that money left the account.
     */
    private fun deriveFromType(type: TransactionType): Pair<TxnCategory, String?>? = when (type) {
        TransactionType.SALARY,
        TransactionType.BUSINESS_INCOME,
        TransactionType.INTEREST,
        TransactionType.DIVIDEND,
        TransactionType.CASHBACK,
        TransactionType.REFUND,
        TransactionType.REVERSAL,
        TransactionType.CASH_DEPOSIT,
        TransactionType.LOAN_DISBURSAL,
        TransactionType.MATURITY,
        TransactionType.REDEMPTION,
        -> TxnCategory.INCOME to null

        TransactionType.SIP, TransactionType.INVESTMENT -> TxnCategory.INVESTMENTS to null
        TransactionType.EMI, TransactionType.LOAN_REPAYMENT -> TxnCategory.LOANS to null
        TransactionType.INSURANCE_PREMIUM -> TxnCategory.INSURANCE to null
        TransactionType.SUBSCRIPTION -> TxnCategory.SUBSCRIPTIONS to null
        TransactionType.BILL_PAYMENT, TransactionType.RECHARGE -> TxnCategory.UTILITIES to null
        TransactionType.TAX -> TxnCategory.TAXES to null
        TransactionType.RENT -> TxnCategory.RENT to null
        TransactionType.FEE_OR_CHARGE -> TxnCategory.FEES to null
        TransactionType.DONATION -> TxnCategory.CHARITY to null
        TransactionType.ATM_WITHDRAWAL -> TxnCategory.CASH to null
        TransactionType.CREDIT_CARD_PAYMENT -> TxnCategory.TRANSFERS to null
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> TxnCategory.TRANSFERS to null

        TransactionType.PURCHASE,
        TransactionType.OTP,
        TransactionType.DELIVERY,
        TransactionType.BOOKING,
        TransactionType.APPOINTMENT,
        TransactionType.BILL_DUE,
        TransactionType.BALANCE_INFO,
        TransactionType.PROMOTIONAL,
        TransactionType.UNKNOWN,
        -> null
    }
}
