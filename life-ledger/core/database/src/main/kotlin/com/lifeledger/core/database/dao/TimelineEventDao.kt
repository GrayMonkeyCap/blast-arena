package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.TimelineEventEntity
import com.lifeledger.core.model.SourceKind
import com.lifeledger.core.model.TimelineEventType
import kotlinx.coroutines.flow.Flow

/**
 * The unified life timeline.
 *
 * Reads are always a time range plus a limit: the timeline is infinite by nature and the only
 * bound that means anything to it is "the window on screen".
 */
@Dao
interface TimelineEventDao {

    @Insert
    suspend fun insert(event: TimelineEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<TimelineEventEntity>): List<Long>

    @Upsert
    suspend fun upsert(event: TimelineEventEntity): Long

    @Delete
    suspend fun delete(event: TimelineEventEntity)

    @Query("SELECT * FROM timeline_events WHERE id = :id")
    fun observeById(id: Long): Flow<TimelineEventEntity?>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events ORDER BY occurredAt DESC, id DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<TimelineEventEntity>>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE type = :type
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeByType(type: TimelineEventType, limit: Int): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE isPinned = 1 ORDER BY occurredAt DESC")
    fun observePinned(): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TimelineEventEntity>>

    /**
     * Lookup by origin, which is how a writer avoids emitting the same event twice when its
     * source is re-processed. Source kind and id together are the only stable identity a
     * projected event has.
     */
    @Query("SELECT * FROM timeline_events WHERE sourceKind = :sourceKind AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(sourceKind: SourceKind, sourceId: Long): TimelineEventEntity?

    @Query("SELECT * FROM timeline_events WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findByTransactionId(transactionId: Long): TimelineEventEntity?

    @Query("UPDATE timeline_events SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM timeline_events WHERE occurredAt < :beforeMillis AND isPinned = 0")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM timeline_events")
    suspend fun countAll(): Int
}
