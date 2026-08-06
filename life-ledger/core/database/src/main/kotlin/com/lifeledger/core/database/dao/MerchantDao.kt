package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.MerchantEntity
import com.lifeledger.core.model.TxnCategory
import kotlinx.coroutines.flow.Flow

/**
 * The merchant catalogue plus everything the resolver has learned.
 *
 * Reads here sit on the hot path of parsing — one lookup per message — so the lookups are all
 * single-index equality on `normalizedKey`.
 */
@Dao
interface MerchantDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(merchant: MerchantEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(merchants: List<MerchantEntity>): List<Long>

    @Upsert
    suspend fun upsert(merchant: MerchantEntity): Long

    @Delete
    suspend fun delete(merchant: MerchantEntity)

    @Query("SELECT * FROM merchants WHERE normalizedKey = :normalizedKey LIMIT 1")
    suspend fun findByNormalizedKey(normalizedKey: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun findById(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE id = :id")
    fun observeById(id: Long): Flow<MerchantEntity?>

    @Query("SELECT * FROM merchants ORDER BY canonicalName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MerchantEntity>>

    /**
     * Substring match for the merchant picker. `%` and `_` in [term] behave as LIKE
     * wildcards; that is harmless for a picker and avoids an ESCAPE clause that every caller
     * would then have to honour.
     */
    @Query(
        """
        SELECT * FROM merchants
        WHERE canonicalName LIKE '%' || :term || '%'
        ORDER BY transactionCount DESC, canonicalName COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchByName(term: String, limit: Int): List<MerchantEntity>

    @Query("SELECT * FROM merchants ORDER BY transactionCount DESC LIMIT :limit")
    fun observeMostUsed(limit: Int): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE isSubscriptionProvider = 1 ORDER BY canonicalName ASC")
    suspend fun subscriptionProviders(): List<MerchantEntity>

    @Query("SELECT * FROM merchants WHERE isBillProvider = 1 ORDER BY canonicalName ASC")
    suspend fun billProviders(): List<MerchantEntity>

    @Query("SELECT * FROM merchants WHERE isInvestmentProvider = 1 ORDER BY canonicalName ASC")
    suspend fun investmentProviders(): List<MerchantEntity>

    /**
     * Folds one sighting into the merchant's running stats.
     *
     * Done in SQL rather than read-modify-write so that concurrent ingest batches cannot lose
     * counts to a lost update, and so the whole batch stays inside one transaction.
     */
    @Query(
        """
        UPDATE merchants
        SET transactionCount = transactionCount + 1,
            firstSeenAt = MIN(COALESCE(firstSeenAt, :atMillis), :atMillis),
            lastSeenAt = MAX(COALESCE(lastSeenAt, :atMillis), :atMillis)
        WHERE id = :id
        """,
    )
    suspend fun recordSighting(id: Long, atMillis: Long)

    /** User correction: what this merchant should categorise as from now on. */
    @Query("UPDATE merchants SET defaultCategory = :category, defaultSubcategory = :subcategory WHERE id = :id")
    suspend fun setDefaultCategory(id: Long, category: TxnCategory, subcategory: String?)

    @Query("SELECT COUNT(*) FROM merchants")
    suspend fun countAll(): Int
}
