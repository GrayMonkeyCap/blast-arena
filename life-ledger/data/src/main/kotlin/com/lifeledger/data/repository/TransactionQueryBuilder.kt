package com.lifeledger.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.lifeledger.core.model.TransactionQuery
import java.time.ZoneId

/**
 * Compiles a [TransactionQuery] into SQL for the DAO's `@RawQuery`.
 *
 * A raw query is used rather than a large `@Query` with nullable parameters because the
 * filter space is genuinely combinatorial — period × categories × types × merchants ×
 * accounts × amount bounds × direction — and expressing that as `(:categories IS NULL OR
 * category IN (:categories))` produces SQL that SQLite cannot index well and that nobody
 * can read.
 *
 * Two invariants this builder guarantees, and the reason it exists as one place rather than
 * being inlined at call sites:
 *
 *  1. **Every value is bound, never interpolated.** User text reaches SQLite as an argument,
 *     so a merchant named `'; DROP TABLE` is a merchant name and nothing else.
 *  2. **The exclusion guards are always applied.** Duplicates and user-excluded rows are
 *     filtered unless the caller explicitly opts in. Forgetting them at one call site would
 *     silently double a total, which is the kind of bug a user would never report because
 *     they would simply stop trusting the app.
 */
object TransactionQueryBuilder {

    fun build(
        query: TransactionQuery,
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int? = null,
        offset: Int = 0,
    ): SupportSQLiteQuery {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (!query.includeDuplicates) clauses += "t.duplicateOfId IS NULL"
        if (!query.includeExcluded) clauses += "t.excludedFromStats = 0"

        query.range?.let { range ->
            clauses += "t.occurredAt >= ? AND t.occurredAt < ?"
            args += range.startInstant(zone).toEpochMilli()
            args += range.endExclusiveInstant(zone).toEpochMilli()
        }

        query.direction?.let {
            clauses += "t.direction = ?"
            args += it.name
        }

        if (query.categories.isNotEmpty()) {
            clauses += "t.category IN (${placeholders(query.categories.size)})"
            args += query.categories.map { it.name }
        }

        if (query.types.isNotEmpty()) {
            clauses += "t.type IN (${placeholders(query.types.size)})"
            args += query.types.map { it.name }
        }

        if (query.paymentMethods.isNotEmpty()) {
            clauses += "t.paymentMethod IN (${placeholders(query.paymentMethods.size)})"
            args += query.paymentMethods.map { it.name }
        }

        if (query.merchantIds.isNotEmpty()) {
            clauses += "t.merchantId IN (${placeholders(query.merchantIds.size)})"
            args += query.merchantIds.toList()
        }

        if (query.merchantNames.isNotEmpty()) {
            // Matched case-insensitively because the same merchant can be stored with
            // different casing across a history that predates the merchant catalogue.
            clauses += "LOWER(t.merchantName) IN (${placeholders(query.merchantNames.size)})"
            args += query.merchantNames.map { it.lowercase() }
        }

        if (query.accountIds.isNotEmpty()) {
            clauses += "t.accountId IN (${placeholders(query.accountIds.size)})"
            args += query.accountIds.toList()
        }

        if (query.tagIds.isNotEmpty()) {
            clauses += "t.id IN (SELECT transactionId FROM transaction_tags " +
                "WHERE tagId IN (${placeholders(query.tagIds.size)}))"
            args += query.tagIds.toList()
        }

        query.minAmountMinor?.let {
            clauses += "t.amountMinor >= ?"
            args += it
        }

        query.maxAmountMinor?.let {
            clauses += "t.amountMinor <= ?"
            args += it
        }

        query.text?.takeIf { it.isNotBlank() }?.let { text ->
            // The FTS table is joined by rowid, which for an external-content table is the
            // base row's id — so this stays a single indexed lookup rather than a scan.
            clauses += "t.id IN (SELECT rowid FROM transactions_fts WHERE transactions_fts MATCH ?)"
            args += escapeFts(text)
        }

        val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
        val order = orderClause(query.sort)
        val paging = buildString {
            if (limit != null) {
                append(" LIMIT ?")
                args += limit
                if (offset > 0) {
                    append(" OFFSET ?")
                    args += offset
                }
            }
        }

        return SimpleSQLiteQuery(
            "SELECT t.* FROM transactions AS t $where $order$paging",
            args.toTypedArray(),
        )
    }

    private fun orderClause(sort: TransactionQuery.Sort): String = when (sort) {
        TransactionQuery.Sort.DATE_DESC -> "ORDER BY t.occurredAt DESC, t.id DESC"
        TransactionQuery.Sort.DATE_ASC -> "ORDER BY t.occurredAt ASC, t.id ASC"
        TransactionQuery.Sort.AMOUNT_DESC -> "ORDER BY t.amountMinor DESC, t.occurredAt DESC"
        TransactionQuery.Sort.AMOUNT_ASC -> "ORDER BY t.amountMinor ASC, t.occurredAt DESC"
        TransactionQuery.Sort.MERCHANT_ASC -> "ORDER BY t.merchantName COLLATE NOCASE ASC"
        // FTS already returns rows in relevance order; falling back to date keeps the
        // ordering deterministic when no MATCH clause is present.
        TransactionQuery.Sort.RELEVANCE -> "ORDER BY t.occurredAt DESC"
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

    /**
     * Turns arbitrary user text into a safe FTS4 MATCH expression.
     *
     * FTS treats `"`, `*`, `^`, `:`, `-`, `(`, `)` and the bare words `AND`, `OR`, `NOT`
     * and `NEAR` as syntax. An unescaped apostrophe or a stray hyphen in a search box is
     * enough to throw a syntax error mid-typing, so every token is quoted and a trailing
     * `*` is added to the last one to give prefix matching as the user types.
     */
    fun escapeFts(raw: String): String {
        val tokens = raw
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "\"\""
        return tokens.mapIndexed { index, token ->
            val quoted = "\"" + token.replace("\"", "") + "\""
            if (index == tokens.lastIndex) "$quoted*" else quoted
        }.joinToString(" ")
    }
}
