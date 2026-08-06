package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.lifeledger.core.database.entity.TransactionEntity
import com.lifeledger.core.database.projection.AmountBucketRow
import com.lifeledger.core.database.projection.CashFlowRow
import com.lifeledger.core.database.projection.CategoryTotalRow
import com.lifeledger.core.database.projection.DayIntensityRow
import com.lifeledger.core.database.projection.MerchantTotalRow
import com.lifeledger.core.database.projection.PeriodTotalRow
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.TxnCategory
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes over the transactions table.
 *
 * **The statistics invariant.** Every aggregate here filters
 * `duplicateOfId IS NULL AND excludedFromStats = 0`. The predicate is written out in each
 * query rather than hidden behind a view or a shared constant, because it is the single rule
 * that decides whether the app's headline numbers are correct, and it must be visible at the
 * point where it either is or is not applied. A bank that sends both an "debited" and a
 * "spent on card" SMS for one purchase would otherwise double every card total.
 *
 * **Time.** All bounds are epoch millis and half-open (`>= from AND < to`), matching
 * `DateRange.startInstant()` / `DateRange.endExclusiveInstant()`. Half-open ranges are the
 * only form that tiles without gaps or overlaps at midnight.
 *
 * **Local time.** The `strftime` buckets convert through `'localtime'` so a spend at 00:30
 * lands on the day the user actually lived, not on the UTC day.
 */
@Dao
interface TransactionDao {

    // ---- Writes ---------------------------------------------------------------------------

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    /**
     * Returns the new rowid when the row was inserted, and `-1` when an existing row was
     * updated — Room's contract, not ours. Callers that need an id after an update already
     * hold it, since the update could only have matched on it.
     */
    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Hides [id] from every total by pointing it at the row it duplicates.
     *
     * `updatedAt` is left alone on purpose: this layer owns no clock — time comes from
     * `TimeProvider` in `core:common` — and inventing one here would make write timestamps
     * untestable.
     */
    @Query("UPDATE transactions SET duplicateOfId = :duplicateOfId WHERE id = :id")
    suspend fun markDuplicate(id: Long, duplicateOfId: Long)

    @Query("UPDATE transactions SET duplicateOfId = NULL WHERE id = :id")
    suspend fun clearDuplicate(id: Long)

    @Query("UPDATE transactions SET excludedFromStats = :excluded WHERE id = :id")
    suspend fun setExcludedFromStats(id: Long, excluded: Boolean)

    // ---- Single-row reads -----------------------------------------------------------------

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCountAll(): Flow<Int>

    // ---- List reads -----------------------------------------------------------------------

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    /**
     * Offset paging rather than a `PagingSource`: `core:database` deliberately does not put
     * Paging types in its public surface, so the choice of paging library stays a UI-layer
     * decision. The secondary sort on `id` keeps the window stable when several transactions
     * share a timestamp, which bank SMS batches routinely do.
     */
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE category = :category
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeByCategory(
        category: TxnCategory,
        limit: Int,
        offset: Int,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE merchantId = :merchantId
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeByMerchant(
        merchantId: Long,
        limit: Int,
        offset: Int,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeByAccount(
        accountId: Long,
        limit: Int,
        offset: Int,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT DISTINCT merchantName FROM transactions
        WHERE merchantName IS NOT NULL
        ORDER BY merchantName COLLATE NOCASE
        """,
    )
    suspend fun distinctMerchantNames(): List<String>

    // ---- Deduplication --------------------------------------------------------------------

    @Query("SELECT * FROM transactions WHERE dedupeHash = :dedupeHash LIMIT 1")
    suspend fun findByDedupeHash(dedupeHash: String): TransactionEntity?

    /**
     * Rows that could be the same event as one of amount [amountMinor] seen in the window.
     *
     * The window, not the hash, is what catches the interesting duplicates: two banks
     * describing one purchase produce different text and therefore different hashes, but the
     * same amount within a couple of minutes. Rows already marked as duplicates are skipped so
     * a chain of copies all point at the same survivor.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amountMinor = :amountMinor
          AND occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND duplicateOfId IS NULL
        ORDER BY occurredAt ASC, id ASC
        """,
    )
    suspend fun findCandidateDuplicates(
        amountMinor: Long,
        fromMillis: Long,
        toMillis: Long,
    ): List<TransactionEntity>

    // ---- Search ---------------------------------------------------------------------------

    /**
     * Full-text search over `transactions_fts`.
     *
     * [ftsQuery] must already be a valid FTS4 MATCH expression — build it with
     * `FtsQueries.toMatchQuery`, never by concatenating user input, or a stray quote turns a
     * search box into a query-syntax error.
     *
     * Duplicates are hidden because a search result the user cannot act on is noise; rows
     * they excluded from statistics are kept, because excluding something from totals is not
     * the same as wanting it to disappear.
     */
    @Query(
        """
        SELECT transactions.* FROM transactions
        JOIN transactions_fts ON transactions_fts.rowid = transactions.id
        WHERE transactions_fts MATCH :ftsQuery
          AND transactions.duplicateOfId IS NULL
        ORDER BY transactions.occurredAt DESC, transactions.id DESC
        LIMIT :limit
        """,
    )
    suspend fun search(ftsQuery: String, limit: Int): List<TransactionEntity>

    // ---- Aggregates -----------------------------------------------------------------------

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
            COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amountMinor ELSE 0 END), 0) AS expenseMinor,
            COALESCE(SUM(CASE WHEN type IN ('INVESTMENT', 'SIP') THEN amountMinor ELSE 0 END), 0) AS investedMinor,
            COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        """,
    )
    fun cashFlow(fromMillis: Long, toMillis: Long): Flow<CashFlowRow>

    @Query(
        """
        SELECT category AS category,
               COALESCE(SUM(amountMinor), 0) AS totalMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = :direction
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY category
        ORDER BY totalMinor DESC
        """,
    )
    fun categoryTotals(
        fromMillis: Long,
        toMillis: Long,
        direction: Direction,
    ): Flow<List<CategoryTotalRow>>

    /**
     * Grouping is on `merchantId, merchantName` rather than on the id alone so that spend
     * which never resolved to a catalogue merchant still groups by the name the bank used,
     * instead of collapsing every unresolved merchant into one "null" bar.
     */
    @Query(
        """
        SELECT merchantId AS merchantId,
               merchantName AS merchantName,
               COALESCE(SUM(amountMinor), 0) AS totalMinor,
               COUNT(*) AS count,
               MAX(occurredAt) AS lastAt
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = :direction
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY merchantId, merchantName
        ORDER BY totalMinor DESC
        LIMIT :limit
        """,
    )
    fun merchantTotals(
        fromMillis: Long,
        toMillis: Long,
        direction: Direction,
        limit: Int,
    ): Flow<List<MerchantTotalRow>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS periodKey,
               COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amountMinor ELSE 0 END), 0) AS expenseMinor,
               COALESCE(SUM(CASE WHEN type IN ('INVESTMENT', 'SIP') THEN amountMinor ELSE 0 END), 0) AS investedMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """,
    )
    fun periodTotalsByDay(fromMillis: Long, toMillis: Long): Flow<List<PeriodTotalRow>>

    @Query(
        """
        SELECT strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS periodKey,
               COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amountMinor ELSE 0 END), 0) AS expenseMinor,
               COALESCE(SUM(CASE WHEN type IN ('INVESTMENT', 'SIP') THEN amountMinor ELSE 0 END), 0) AS investedMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """,
    )
    fun periodTotalsByMonth(fromMillis: Long, toMillis: Long): Flow<List<PeriodTotalRow>>

    /**
     * Spend per calendar day for the heatmap.
     *
     * `date(..., 'localtime')` yields `YYYY-MM-DD`, which `strftime('%s', ...)` reads back as
     * midnight UTC — so the division by 86 400 is an exact epoch day with no timezone drift
     * left in it.
     */
    @Query(
        """
        SELECT CAST(strftime('%s', date(occurredAt / 1000, 'unixepoch', 'localtime')) AS INTEGER) / 86400 AS epochDay,
               COALESCE(SUM(amountMinor), 0) AS totalMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = 'DEBIT'
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """,
    )
    fun dayIntensity(fromMillis: Long, toMillis: Long): Flow<List<DayIntensityRow>>

    @Query(
        """
        SELECT (amountMinor / :bucketSizeMinor) * :bucketSizeMinor AS bucketFloorMinor,
               COALESCE(SUM(amountMinor), 0) AS totalMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = :direction
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        GROUP BY bucketFloorMinor
        ORDER BY bucketFloorMinor ASC
        """,
    )
    fun amountBuckets(
        fromMillis: Long,
        toMillis: Long,
        direction: Direction,
        bucketSizeMinor: Long,
    ): Flow<List<AmountBucketRow>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = 'DEBIT'
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        ORDER BY amountMinor DESC
        LIMIT 1
        """,
    )
    suspend fun largestExpense(fromMillis: Long, toMillis: Long): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
          AND direction = 'CREDIT'
          AND duplicateOfId IS NULL AND excludedFromStats = 0
        ORDER BY amountMinor DESC
        LIMIT 1
        """,
    )
    suspend fun largestIncome(fromMillis: Long, toMillis: Long): TransactionEntity?

    // ---- Dynamic queries ------------------------------------------------------------------

    /**
     * Escape hatch for `TransactionQuery`, whose filter combinations are open-ended enough
     * that enumerating them as `@Query` methods would mean hundreds of near-identical strings.
     *
     * Build the argument with `TransactionQueries.build` — it is the only supported producer,
     * it binds every user-supplied value as a parameter, and it carries the same duplicate and
     * excluded-row rules the compiled queries above enforce.
     */
    @RawQuery
    suspend fun rawQuery(query: SupportSQLiteQuery): List<TransactionEntity>

    /** Observing variant of [rawQuery]; re-emits whenever any transaction row changes. */
    @RawQuery(observedEntities = [TransactionEntity::class])
    fun observeRawQuery(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>
}
