package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A free-form user label.
 *
 * [usageCount] is denormalised so the tag picker can order by popularity without a join and
 * a GROUP BY over the whole cross-reference table on every keystroke.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorSeed: Int = 0,
    val usageCount: Int = 0,
)

/**
 * Transaction ↔ tag membership.
 *
 * Both sides cascade: a tag row and a transaction row are each the sole reason a membership
 * exists, so an orphaned pair could only ever be a leak.
 */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["tagId"]),
    ],
)
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long,
)
