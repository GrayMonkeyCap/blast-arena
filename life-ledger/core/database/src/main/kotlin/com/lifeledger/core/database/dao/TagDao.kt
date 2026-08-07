package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lifeledger.core.database.entity.TagEntity
import com.lifeledger.core.database.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * Tags and their membership.
 *
 * [usageCount] is denormalised, so it has exactly one writer: the [attach] / [detach] pair
 * below. Anything that inserts a cross-reference row by another route will silently drift the
 * counter, which is why the raw insert is not part of this interface.
 */
@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tag: TagEntity): Long

    @Upsert
    suspend fun upsert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY usageCount DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: Long): TagEntity?

    @Query(
        """
        SELECT tags.* FROM tags
        JOIN transaction_tags ON transaction_tags.tagId = tags.id
        WHERE transaction_tags.transactionId = :transactionId
        ORDER BY tags.name COLLATE NOCASE ASC
        """,
    )
    fun observeForTransaction(transactionId: Long): Flow<List<TagEntity>>

    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun tagIdsForTransaction(transactionId: Long): List<Long>

    /** Attaches a tag and keeps [TagEntity.usageCount] true, in one database transaction. */
    @Transaction
    suspend fun attach(transactionId: Long, tagId: Long) {
        if (insertCrossRefIgnore(TransactionTagCrossRef(transactionId, tagId)) != -1L) {
            incrementUsage(tagId)
        }
    }

    @Transaction
    suspend fun detach(transactionId: Long, tagId: Long) {
        if (deleteCrossRef(transactionId, tagId) > 0) {
            decrementUsage(tagId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefIgnore(crossRef: TransactionTagCrossRef): Long

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId AND tagId = :tagId")
    suspend fun deleteCrossRef(transactionId: Long, tagId: Long): Int

    @Query("UPDATE tags SET usageCount = usageCount + 1 WHERE id = :tagId")
    suspend fun incrementUsage(tagId: Long)

    @Query("UPDATE tags SET usageCount = MAX(usageCount - 1, 0) WHERE id = :tagId")
    suspend fun decrementUsage(tagId: Long)

    /** Repairs the denormalised counters; cheap enough to run after a bulk import. */
    @Query(
        """
        UPDATE tags
        SET usageCount = (SELECT COUNT(*) FROM transaction_tags WHERE transaction_tags.tagId = tags.id)
        """,
    )
    suspend fun recomputeUsageCounts()
}
