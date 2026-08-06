package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow

/**
 * The raw-string to merchant mapping that makes `AMZN*MKTP` and `AMAZON PAY` one merchant.
 */
@Dao
interface MerchantAliasDao {

    /**
     * IGNORE on conflict, because the first mapping for a normalised alias wins: aliases are
     * learned incrementally and a later, lower-confidence guess must not silently re-point an
     * alias the user already relies on. Deliberate re-pointing goes through [repoint].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(alias: MerchantAliasEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(aliases: List<MerchantAliasEntity>): List<Long>

    @Upsert
    suspend fun upsert(alias: MerchantAliasEntity)

    @Delete
    suspend fun delete(alias: MerchantAliasEntity)

    @Query("SELECT * FROM merchant_aliases WHERE normalizedAlias = :normalizedAlias LIMIT 1")
    suspend fun findByNormalizedAlias(normalizedAlias: String): MerchantAliasEntity?

    @Query("SELECT * FROM merchant_aliases WHERE merchantId = :merchantId ORDER BY hitCount DESC")
    fun observeForMerchant(merchantId: Long): Flow<List<MerchantAliasEntity>>

    @Query("SELECT * FROM merchant_aliases WHERE merchantId = :merchantId ORDER BY hitCount DESC")
    suspend fun forMerchant(merchantId: Long): List<MerchantAliasEntity>

    @Query("UPDATE merchant_aliases SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun recordHit(id: Long)

    @Query("UPDATE merchant_aliases SET merchantId = :merchantId, userDefined = 1, confidence = 1.0 WHERE id = :id")
    suspend fun repoint(id: Long, merchantId: Long)

    /**
     * Prunes machine-learned aliases that never paid off. User-defined aliases are exempt:
     * the user said so, and an unused rule is not a wrong one.
     */
    @Query("DELETE FROM merchant_aliases WHERE userDefined = 0 AND hitCount <= :maxHits")
    suspend fun deleteUnusedLearned(maxHits: Int): Int

    @Query("SELECT COUNT(*) FROM merchant_aliases")
    suspend fun countAll(): Int
}
