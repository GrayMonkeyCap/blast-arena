package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lifeledger.core.database.entity.SmsEntity
import com.lifeledger.core.model.SmsRecord
import kotlinx.coroutines.flow.Flow

/**
 * The inbox mirror.
 *
 * Import is expected to run repeatedly over an overlapping window, so the write path is built
 * around the unique fingerprint index rather than around the caller remembering what it has
 * already seen.
 */
@Dao
interface SmsDao {

    /**
     * Inserts everything that is not already present and reports `-1` for each row that was
     * already there.
     *
     * IGNORE rather than REPLACE: a message body never legitimately changes, so a conflict
     * means "seen this one", and REPLACE would delete and re-insert the row — taking its id,
     * and with it every transaction's link back to the message it was parsed from.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(messages: List<SmsEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: SmsEntity): Long

    /** Oldest first, so the ledger is built in the order the user's life happened. */
    @Query("SELECT * FROM sms WHERE status = 'PENDING' ORDER BY receivedAt ASC, id ASC LIMIT :limit")
    fun observePending(limit: Int): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms WHERE status = 'PENDING' ORDER BY receivedAt ASC, id ASC LIMIT :limit")
    suspend fun takePending(limit: Int): List<SmsEntity>

    @Query("SELECT COUNT(*) FROM sms WHERE status = :status")
    fun countByStatus(status: SmsRecord.ProcessingStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms")
    suspend fun countAll(): Int

    @Query(
        """
        UPDATE sms
        SET status = :status, processedAt = :processedAtMillis, parserId = :parserId
        WHERE id = :id
        """,
    )
    suspend fun markProcessed(
        id: Long,
        status: SmsRecord.ProcessingStatus,
        processedAtMillis: Long,
        parserId: String?,
    )

    /** Resets messages so an improved parser can be replayed over history. */
    @Query("UPDATE sms SET status = 'PENDING', processedAt = NULL, parserId = NULL WHERE status = :status")
    suspend fun resetForReplay(status: SmsRecord.ProcessingStatus): Int

    @Query("SELECT * FROM sms WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): SmsEntity?

    @Query("SELECT * FROM sms WHERE id = :id")
    suspend fun findById(id: Long): SmsEntity?

    /** Watermark for incremental import: everything after this is new to us. */
    @Query("SELECT MAX(receivedAt) FROM sms")
    suspend fun latestReceivedAt(): Long?

    @Query("SELECT * FROM sms ORDER BY receivedAt DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observeAll(limit: Int, offset: Int): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms WHERE senderCode = :senderCode ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun findBySenderCode(senderCode: String, limit: Int): List<SmsEntity>

    /**
     * Retention pruning. Transactions survive it — their `smsId` foreign key is
     * `ON DELETE SET NULL` — but parse logs for the pruned messages cascade away with them.
     */
    @Query("DELETE FROM sms WHERE receivedAt < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("DELETE FROM sms")
    suspend fun deleteAll()
}
