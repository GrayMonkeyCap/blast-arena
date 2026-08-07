package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.ParserStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Per-parser enablement and health counters.
 *
 * Rows are created lazily by [ensure] the first time a parser runs: the registry is code, and
 * seeding this table at install time would go stale the moment a parser is added or removed.
 */
@Dao
interface ParserStateDao {

    @Upsert
    suspend fun upsert(state: ParserStateEntity)

    @Query("SELECT * FROM parser_state ORDER BY parserId ASC")
    fun observeAll(): Flow<List<ParserStateEntity>>

    @Query("SELECT * FROM parser_state WHERE parserId = :parserId")
    suspend fun find(parserId: String): ParserStateEntity?

    @Query("SELECT parserId FROM parser_state WHERE enabled = 0")
    suspend fun disabledParserIds(): List<String>

    /** Creates the row if this parser has never been seen, leaving an existing one untouched. */
    @Query(
        """
        INSERT OR IGNORE INTO parser_state (parserId, enabled, version, lastRunAt, successCount, failureCount)
        VALUES (:parserId, 1, :version, NULL, 0, 0)
        """,
    )
    suspend fun ensure(parserId: String, version: Int)

    @Query("UPDATE parser_state SET enabled = :enabled WHERE parserId = :parserId")
    suspend fun setEnabled(parserId: String, enabled: Boolean)

    @Query(
        """
        UPDATE parser_state
        SET lastRunAt = :atMillis,
            successCount = successCount + :successes,
            failureCount = failureCount + :failures
        WHERE parserId = :parserId
        """,
    )
    suspend fun recordRun(parserId: String, atMillis: Long, successes: Long, failures: Long)

    @Query("UPDATE parser_state SET successCount = 0, failureCount = 0 WHERE parserId = :parserId")
    suspend fun resetCounters(parserId: String)
}
