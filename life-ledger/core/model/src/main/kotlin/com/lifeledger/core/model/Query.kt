package com.lifeledger.core.model

/**
 * A structured description of "which transactions", shared by search, filters and the
 * natural-language query engine.
 *
 * The NLQ layer's job is only to turn a sentence into one of these; everything downstream
 * — including a future on-device LLM — targets this same type, so swapping the front end
 * never touches the data layer.
 */
data class TransactionQuery(
    val text: String? = null,
    val range: DateRange? = null,
    val categories: Set<TxnCategory> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
    val paymentMethods: Set<PaymentMethod> = emptySet(),
    val merchantIds: Set<Long> = emptySet(),
    val merchantNames: Set<String> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val direction: Direction? = null,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val includeDuplicates: Boolean = false,
    val includeExcluded: Boolean = false,
    val sort: Sort = Sort.DATE_DESC,
    val limit: Int? = null,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && range == null && categories.isEmpty() &&
            types.isEmpty() && paymentMethods.isEmpty() && merchantIds.isEmpty() &&
            merchantNames.isEmpty() && accountIds.isEmpty() && tagIds.isEmpty() &&
            direction == null && minAmountMinor == null && maxAmountMinor == null

    enum class Sort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, MERCHANT_ASC, RELEVANCE }
}

/** What an answered natural-language question looks like. */
data class QueryAnswer(
    val question: String,
    /** One-sentence answer, e.g. "You spent ₹2,340 on coffee last month." */
    val headline: String,
    val total: Money? = null,
    val count: Int = 0,
    val range: DateRange? = null,
    val transactions: List<Transaction> = emptyList(),
    val breakdown: List<CategoryTotal> = emptyList(),
    val interpretedQuery: TransactionQuery,
    /** How sure the interpreter is that it understood the question. */
    val confidence: Confidence = Confidence.MEDIUM,
    /** Human-readable account of how the question was interpreted, shown under the answer. */
    val explanation: String = "",
)
