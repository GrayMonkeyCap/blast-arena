package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.AccountEntity
import com.lifeledger.core.model.AccountType
import kotlinx.coroutines.flow.Flow

/**
 * Accounts, cards and wallets discovered from masked identifiers.
 *
 * Discovery runs on every parsed message, so it must be idempotent: [insertIgnore] leans on
 * the unique (bank, masked number, type) index and returns `-1` when the account was already
 * known, which is the signal to look it up instead of creating a second one.
 */
@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(account: AccountEntity): Long

    @Upsert
    suspend fun upsert(account: AccountEntity): Long

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query(
        """
        SELECT * FROM accounts
        WHERE bankCode IS :bankCode AND maskedNumber IS :maskedNumber AND type = :type
        LIMIT 1
        """,
    )
    suspend fun findByIdentity(
        bankCode: String?,
        maskedNumber: String?,
        type: AccountType,
    ): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY lastSeenAt DESC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isArchived ASC, lastSeenAt DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type = :type ORDER BY lastSeenAt DESC")
    suspend fun findByType(type: AccountType): List<AccountEntity>

    /**
     * Only moves the balance forward. Bank SMS arrive out of order often enough that a late
     * message from yesterday would otherwise overwrite today's balance with a stale one.
     */
    @Query(
        """
        UPDATE accounts
        SET lastKnownBalanceMinor = :balanceMinor, balanceAsOf = :asOfMillis
        WHERE id = :id AND (balanceAsOf IS NULL OR balanceAsOf <= :asOfMillis)
        """,
    )
    suspend fun updateBalanceIfNewer(id: Long, balanceMinor: Long, asOfMillis: Long)

    @Query(
        """
        UPDATE accounts
        SET transactionCount = transactionCount + 1,
            firstSeenAt = MIN(COALESCE(firstSeenAt, :atMillis), :atMillis),
            lastSeenAt = MAX(COALESCE(lastSeenAt, :atMillis), :atMillis)
        WHERE id = :id
        """,
    )
    suspend fun recordSighting(id: Long, atMillis: Long)

    @Query("UPDATE accounts SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Net worth across everything the app can actually see a balance for. */
    @Query("SELECT COALESCE(SUM(lastKnownBalanceMinor), 0) FROM accounts WHERE isArchived = 0")
    fun observeTrackedBalanceMinor(): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAll(): Int
}
