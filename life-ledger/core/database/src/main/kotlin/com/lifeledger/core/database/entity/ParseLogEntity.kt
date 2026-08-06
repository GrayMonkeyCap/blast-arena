package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The parser audit trail behind Developer Mode.
 *
 * Only snippets of the sender and body are kept: the log is a debugging aid that may be
 * exported when a user reports a mis-parse, and a full copy of every message body here would
 * turn a diagnostic into a second, less protected inbox.
 *
 * The table is expected to be pruned by age — see `ParseLogDao.deleteOlderThan`.
 */
@Entity(
    tableName = "parse_logs",
    foreignKeys = [
        ForeignKey(
            entity = SmsEntity::class,
            parentColumns = ["id"],
            childColumns = ["smsId"],
            // A log line about a message that no longer exists cannot be acted on.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["at"]),
        Index(value = ["smsId"]),
        Index(value = ["outcome", "at"]),
        Index(value = ["parserId", "at"]),
    ],
)
data class ParseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Epoch millis at which the parse ran. */
    val at: Long,
    val smsId: Long,
    val parserId: String? = null,
    /** Name of `ParseLogEntry.Outcome`. */
    val outcome: String,
    val reason: String? = null,
    val durationMicros: Long = 0,
    val confidence: Float = 0f,
    val senderSnippet: String = "",
    val bodySnippet: String = "",
)
