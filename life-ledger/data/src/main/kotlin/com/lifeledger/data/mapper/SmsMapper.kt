package com.lifeledger.data.mapper

import com.lifeledger.core.database.entity.SmsEntity
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.SourceKind
import java.time.Instant

fun SmsEntity.toDomain(): SmsRecord = SmsRecord(
    id = id,
    fingerprint = fingerprint,
    sender = sender,
    body = body,
    receivedAt = Instant.ofEpochMilli(receivedAt),
    threadId = threadId,
    providerId = providerId,
    status = enumOrDefault(status, SmsRecord.ProcessingStatus.PENDING),
    processedAt = processedAt?.let(Instant::ofEpochMilli),
    parserId = parserId,
    source = enumOrDefault(source, SourceKind.SMS),
)

/**
 * The sender short code is computed once here and stored, rather than being derived on
 * every read. Parser selection consults it for every message in a backfill of potentially
 * 100,000 rows, and an indexed column beats re-parsing the sender string each time.
 */
fun SmsRecord.toEntity(): SmsEntity = SmsEntity(
    id = id,
    fingerprint = fingerprint,
    sender = sender,
    senderCode = senderCode,
    body = body,
    receivedAt = receivedAt.toEpochMilli(),
    threadId = threadId,
    providerId = providerId,
    status = status.name,
    processedAt = processedAt?.toEpochMilli(),
    parserId = parserId,
    source = source.name,
)
