package com.lifeledger.core.database.query

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.lifeledger.core.model.TransactionQuery
import java.time.ZoneId

/**
 * Compiles a [TransactionQuery] into SQL for `TransactionDao.rawQuery`.
 *
 * It lives in `core:database` rather than in the repository layer for two reasons. The filter
 * rules that decide whether duplicates and excluded rows are visible are the same invariant
 * the compiled DAO queries enforce, and an invariant with two implementations has none. And
 * SQL text is exactly the kind of knowledge a persistence layer exists to contain — leaking
 * it upward would make column renames a cross-module change.
 *
 * **Injection.** Every value taken from the query object is bound as a parameter. The only
 * text interpolated into the statement is generated here: fixed column names and `?`
 * placeholders. Enum names are never interpolated either, even though they could not carry an
 * injection — binding them keeps one rule instead of two.
 */
object TransactionQueries {

    /**
     * @param zone the zone the query's date range is interpreted in; the caller passes the
     *   user's zone so that "last month" means their month.
     */
    fun build(
        query: TransactionQuery,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SupportSQLiteQuery {
        val sql = StringBuilder("SELECT * FROM transactions")
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // Full-text first: it is the most selective predicate when present, and reducing the
        // row set through the FTS index beats scanning and filtering.
        query.text
            ?.takeIf { it.isNotBlank() }
            ?.let(FtsQueries::toMatchQuery)
            ?.let { match ->
                where += "id IN (SELECT rowid FROM transactions_fts WHERE transactions_fts MATCH ?)"
                args += match
            }

        query.range?.let { range ->
            where += "occurredAt >= ? AND occurredAt < ?"
            args += range.startInstant(zone).toEpochMilli()
            args += range.endExclusiveInstant(zone).toEpochMilli()
        }

        query.categories.takeIf { it.isNotEmpty() }?.let { categories ->
            where += "category IN (${placeholders(categories.size)})"
            args += categories.map { it.name }
        }

        query.types.takeIf { it.isNotEmpty() }?.let { types ->
            where += "type IN (${placeholders(types.size)})"
            args += types.map { it.name }
        }

        query.paymentMethods.takeIf { it.isNotEmpty() }?.let { methods ->
            where += "paymentMethod IN (${placeholders(methods.size)})"
            args += methods.map { it.name }
        }

        query.merchantIds.takeIf { it.isNotEmpty() }?.let { ids ->
            where += "merchantId IN (${placeholders(ids.size)})"
            args += ids
        }

        // Case-insensitive, because a merchant name reaching this filter may have come from a
        // spoken question or a chip label rather than from the row itself.
        query.merchantNames.takeIf { it.isNotEmpty() }?.let { names ->
            where += "merchantName COLLATE NOCASE IN (${placeholders(names.size)})"
            args += names
        }

        query.accountIds.takeIf { it.isNotEmpty() }?.let { ids ->
            where += "accountId IN (${placeholders(ids.size)})"
            args += ids
        }

        // EXISTS rather than a JOIN: a transaction carrying three of the requested tags must
        // appear once, and a join would return it three times.
        query.tagIds.takeIf { it.isNotEmpty() }?.let { ids ->
            where += """
                EXISTS (
                    SELECT 1 FROM transaction_tags
                    WHERE transaction_tags.transactionId = transactions.id
                      AND transaction_tags.tagId IN (${placeholders(ids.size)})
                )
            """.trimIndent()
            args += ids
        }

        query.direction?.let {
            where += "direction = ?"
            args += it.name
        }

        query.minAmountMinor?.let {
            where += "amountMinor >= ?"
            args += it
        }

        query.maxAmountMinor?.let {
            where += "amountMinor <= ?"
            args += it
        }

        // The statistics invariant, mirrored from the compiled queries in TransactionDao.
        if (!query.includeDuplicates) where += "duplicateOfId IS NULL"
        if (!query.includeExcluded) where += "excludedFromStats = 0"

        if (where.isNotEmpty()) {
            sql.append(" WHERE ").append(where.joinToString(separator = " AND "))
        }
        sql.append(" ORDER BY ").append(orderBy(query.sort))

        query.limit?.let {
            sql.append(" LIMIT ?")
            args += it
        }

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    /**
     * `RELEVANCE` falls back to newest-first: FTS4 offers only `matchinfo`, whose scoring
     * would have to be computed row by row in Kotlin, and for a personal ledger recency is a
     * better relevance signal than term frequency anyway. The enum value is kept because the
     * NLQ layer produces it and a future FTS5 `bm25` ranking would land right here.
     */
    private fun orderBy(sort: TransactionQuery.Sort): String = when (sort) {
        TransactionQuery.Sort.DATE_DESC -> "occurredAt DESC, id DESC"
        TransactionQuery.Sort.DATE_ASC -> "occurredAt ASC, id ASC"
        TransactionQuery.Sort.AMOUNT_DESC -> "amountMinor DESC, occurredAt DESC"
        TransactionQuery.Sort.AMOUNT_ASC -> "amountMinor ASC, occurredAt DESC"
        TransactionQuery.Sort.MERCHANT_ASC -> "merchantName COLLATE NOCASE ASC, occurredAt DESC"
        TransactionQuery.Sort.RELEVANCE -> "occurredAt DESC, id DESC"
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(separator = ", ")
}
