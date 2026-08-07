package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lifeledger.core.database.entity.ParseLogEntity
import com.lifeledger.core.model.ParseLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * The parser audit trail.
 *
 * Append-only by design: a log that can be edited is not evidence. The only removal path is
 * [deleteOlderThan] / [trimToMostRecent], which exist because this table grows with every
 * message processed and nobody debugs a mis-parse from six months ago.
 */
@Dao
interface ParseLogDao {

    @Insert
    suspend fun insert(entry: ParseLogEntity): Long

    @Insert
    suspend fun insertAll(entries: List<ParseLogEntity>)

    @Query("SELECT * FROM parse_logs ORDER BY at DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(limit: Int, offset: Int): Flow<List<ParseLogEntity>>

    @Query("SELECT * FROM parse_logs WHERE smsId = :smsId ORDER BY at DESC")
    suspend fun findForSms(smsId: Long): List<ParseLogEntity>

    @Query("SELECT * FROM parse_logs WHERE outcome = :outcome ORDER BY at DESC LIMIT :limit")
    fun observeByOutcome(outcome: ParseLogEntry.Outcome, limit: Int): Flow<List<ParseLogEntity>>

    @Query("SELECT COUNT(*) FROM parse_logs WHERE outcome = :outcome AND at >= :sinceMillis")
    suspend fun countByOutcomeSince(outcome: ParseLogEntry.Outcome, sinceMillis: Long): Int

    /** Median would be better, but the mean over a bounded window is enough to spot a parser
     *  that has started backtracking catastrophically. */
    @Query("SELECT AVG(durationMicros) FROM parse_logs WHERE parserId = :parserId AND at >= :sinceMillis")
    suspend fun averageDurationMicros(parserId: String, sinceMillis: Long): Double?

    @Query("DELETE FROM parse_logs WHERE at < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    /**
     * Hard cap on the table, for the case where a bulk import writes more log rows in an hour
     * than the age-based prune would ever catch.
     */
    @Query(
        """
        DELETE FROM parse_logs
        WHERE id NOT IN (SELECT id FROM parse_logs ORDER BY at DESC, id DESC LIMIT :keep)
        """,
    )
    suspend fun trimToMostRecent(keep: Int): Int

    @Query("DELETE FROM parse_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM parse_logs")
    suspend fun countAll(): Int
}
