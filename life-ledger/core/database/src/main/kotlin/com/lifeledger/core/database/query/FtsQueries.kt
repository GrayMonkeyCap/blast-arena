package com.lifeledger.core.database.query

/**
 * Turns what a user typed into something safe to hand to `MATCH`.
 *
 * FTS4's query language is a language: `"`, `*`, `-`, `^`, `:`, `(`, `)`, `NEAR`, `AND`, `OR`
 * and `NOT` all mean something in it. Passing a search box straight through therefore has two
 * failure modes — a syntax error thrown at the user for typing `Amazon (India)`, and a query
 * that quietly means something other than what they asked for.
 *
 * The strategy here is reduction rather than escaping: every token is stripped down to
 * letters and digits, which cannot express any FTS operator, so there is nothing left to
 * escape and no quoting rule for callers to get wrong. Lowercasing is part of that — FTS4's
 * bareword operators are only operators in upper case, so `not` cannot become `NOT`.
 *
 * The cost is that phrase search and negation are unavailable from the search box. That is a
 * deliberate trade: Life Ledger's search box is a merchant/description finder, and neither
 * feature is worth a class of user-visible errors.
 */
object FtsQueries {

    /**
     * Builds a MATCH expression that requires every token, with the final token treated as a
     * prefix so results narrow as the user types.
     *
     * Returns `null` when [raw] holds nothing searchable — punctuation only, or blank. A null
     * means "do not run a search", not "match everything": an empty MATCH string is a syntax
     * error, and a wildcard would be a surprising answer to a question that was never asked.
     */
    fun toMatchQuery(raw: String, prefixLastToken: Boolean = true): String? {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { index, token ->
            if (prefixLastToken && index == tokens.lastIndex) "$token*" else token
        }.joinToString(separator = " ")
    }

    /**
     * Restricts a match to the merchant column, e.g. `merchantName:swiggy*`.
     *
     * The column qualifier is generated here rather than taken from a caller, so a column name
     * can never arrive from user input.
     */
    fun merchantMatchQuery(raw: String, prefixLastToken: Boolean = true): String? =
        toMatchQuery(raw, prefixLastToken)
            ?.split(" ")
            ?.joinToString(separator = " ") { "$MERCHANT_COLUMN:$it" }

    /**
     * The tokenizer. Anything that is not a letter or a digit is a separator — which also
     * means `AMZN*MKTP` and `amzn mktp` reduce to the same two tokens, matching how the
     * merchant normaliser in `core:model` treats the same strings.
     */
    fun tokenize(raw: String): List<String> = raw
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString(separator = "")
        .split(' ')
        .filter { it.isNotBlank() }

    private const val MERCHANT_COLUMN = "merchantName"
}
