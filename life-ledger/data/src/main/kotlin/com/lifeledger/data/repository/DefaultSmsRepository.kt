package com.lifeledger.data.repository

import com.lifeledger.core.common.di.IoDispatcher
import com.lifeledger.core.database.dao.SmsDao
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.data.mapper.toDomain
import com.lifeledger.data.mapper.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [SmsRepository].
 *
 * Inserts use the DAO's ignore-on-conflict form keyed on the message fingerprint, which
 * makes ingestion idempotent: re-running a backfill, restoring a device, or receiving the
 * same message twice all converge on one row rather than duplicating history.
 */
@Singleton
class DefaultSmsRepository @Inject constructor(
    private val smsDao: SmsDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SmsRepository {

    override fun observeAll(limit: Int, offset: Int): Flow<List<SmsRecord>> =
        smsDao.observeAll(limit, offset).map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    override fun observeCounts(): Flow<SmsCounts> = combine(
        smsDao.countByStatus(SmsRecord.ProcessingStatus.PENDING),
        smsDao.countByStatus(SmsRecord.ProcessingStatus.PARSED),
        smsDao.countByStatus(SmsRecord.ProcessingStatus.IGNORED),
        smsDao.countByStatus(SmsRecord.ProcessingStatus.UNMATCHED),
        smsDao.countByStatus(SmsRecord.ProcessingStatus.FAILED),
    ) { pending, parsed, ignored, unmatched, failed ->
        SmsCounts(
            total = pending + parsed + ignored + unmatched + failed,
            pending = pending,
            parsed = parsed,
            ignored = ignored,
            unmatched = unmatched,
            failed = failed,
        )
    }.flowOn(io)

    override suspend fun latestReceivedAtMillis(): Long? =
        withContext(io) { smsDao.latestReceivedAt() }

    override suspend fun insertNew(records: List<SmsRecord>): Int = withContext(io) {
        // insertIgnore returns -1 for a row that collided with an existing fingerprint, so
        // counting the non-negative ids gives the number genuinely new to the database.
        smsDao.insertIgnore(records.map { it.toEntity() }).count { it >= 0 }
    }

    override suspend fun pending(limit: Int): List<SmsRecord> =
        withContext(io) { smsDao.takePending(limit).map { it.toDomain() } }

    override suspend fun markProcessed(
        id: Long,
        status: SmsRecord.ProcessingStatus,
        parserId: String?,
    ) = withContext(io) {
        smsDao.markProcessed(id, status.name, parserId, System.currentTimeMillis())
    }

    override suspend fun deleteOlderThan(millis: Long): Int =
        withContext(io) { smsDao.deleteOlderThan(millis) }

    /**
     * Requeues everything for a fresh parse.
     *
     * This is the operation that makes retaining raw message bodies worthwhile: when a
     * parser improves, years of previously unmatched or mis-parsed messages can be replayed
     * rather than being written off.
     */
    override suspend fun requeueAll(): Int =
        withContext(io) { smsDao.resetForReplay(SmsRecord.ProcessingStatus.PENDING) }
}
