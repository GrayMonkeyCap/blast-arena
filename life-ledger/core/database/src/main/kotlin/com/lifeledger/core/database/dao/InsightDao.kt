package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lifeledger.core.database.entity.InsightEntity
import com.lifeledger.core.model.InsightKind
import kotlinx.coroutines.flow.Flow

/**
 * Generated observations.
 *
 * Generators re-run on a schedule and re-derive the same findings, so writes go through
 * [refresh], which updates in place on the dedupe key and — critically — leaves
 * `isDismissed` and `isPinned` alone. A blind upsert of the whole row would resurrect every
 * card the user has already waved away, which is the fastest way to make an insights feed
 * feel broken.
 */
@Dao
interface InsightDao {

    @Upsert
    suspend fun upsert(insight: InsightEntity): Long

    @Delete
    suspend fun delete(insight: InsightEntity)

    /**
     * Writes [insight], preserving the user's verdict on any existing row with the same
     * dedupe key. Returns the row id.
     */
    @Transaction
    suspend fun refresh(insight: InsightEntity): Long {
        val existing = findByDedupeKey(insight.dedupeKey)
        return if (existing == null) {
            upsert(insight)
        } else {
            upsert(
                insight.copy(
                    id = existing.id,
                    isDismissed = existing.isDismissed,
                    isPinned = existing.isPinned,
                ),
            )
            existing.id
        }
    }

    @Query("SELECT * FROM insights WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): InsightEntity?

    @Query("SELECT * FROM insights WHERE id = :id")
    fun observeById(id: Long): Flow<InsightEntity?>

    @Query(
        """
        SELECT * FROM insights
        WHERE isDismissed = 0
        ORDER BY isPinned DESC, generatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeActive(limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights ORDER BY generatedAt DESC LIMIT :limit OFFSET :offset")
    fun observeAll(limit: Int, offset: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE kind = :kind ORDER BY generatedAt DESC LIMIT :limit")
    fun observeByKind(kind: InsightKind, limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT COUNT(*) FROM insights WHERE isDismissed = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE insights SET isDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE insights SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /**
     * Clears out stale findings. Dismissed rows are kept even when old, because their whole
     * purpose after dismissal is to stop the same card coming back.
     */
    @Query("DELETE FROM insights WHERE generatedAt < :beforeMillis AND isPinned = 0 AND isDismissed = 0")
    suspend fun deleteOlderThan(beforeMillis: Long): Int
}
