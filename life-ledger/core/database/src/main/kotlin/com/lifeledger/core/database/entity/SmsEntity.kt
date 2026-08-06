package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The raw inbox, stored verbatim.
 *
 * Bodies are never trimmed or normalised on the way in: when a parser is improved the whole
 * corpus is replayed against it, and anything discarded at import time is gone for good.
 *
 * [senderCode] is denormalised out of [sender] even though the domain model derives it,
 * because parser dispatch selects by short code on every ingest batch and deriving it in
 * SQL would forfeit the index.
 */
@Entity(
    tableName = "sms",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        // The ingest worker's hot query: oldest unprocessed message first.
        Index(value = ["status", "receivedAt"]),
        Index(value = ["receivedAt"]),
        Index(value = ["senderCode"]),
        Index(value = ["providerId"]),
        Index(value = ["threadId"]),
    ],
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Stable identity over sender + body + timestamp; the only guard against re-import. */
    val fingerprint: String,
    val sender: String,
    val senderCode: String,
    val body: String,
    /** Epoch millis, as reported by the SMS provider. */
    val receivedAt: Long,
    val threadId: Long? = null,
    val providerId: Long? = null,
    /** Name of `SmsRecord.ProcessingStatus`. */
    val status: String,
    val processedAt: Long? = null,
    val parserId: String? = null,
    /** Name of `SourceKind`. */
    val source: String,
)
