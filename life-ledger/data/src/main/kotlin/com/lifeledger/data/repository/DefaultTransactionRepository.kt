package com.lifeledger.data.repository

import com.lifeledger.core.common.di.IoDispatcher
import com.lifeledger.core.database.dao.TransactionDao
import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.Merchant
import com.lifeledger.core.model.MerchantAlias
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.core.model.UserRule
import com.lifeledger.data.mapper.toDomain
import com.lifeledger.data.mapper.toEntity
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [TransactionRepository].
 *
 * Arbitrary filtering goes through [TransactionQueryBuilder] and the DAO's `@RawQuery`
 * rather than through a per-filter DAO method, so a new filter is a change in one place.
 */
@Singleton
class DefaultTransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantRepository: MerchantRepository,
    private val ruleRepository: RuleRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : TransactionRepository {

    override fun observeById(id: Long): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }.flowOn(io)

    override fun observeRange(range: DateRange): Flow<List<Transaction>> =
        transactionDao
            .observeRange(
                range.startInstant(zone).toEpochMilli(),
                range.endExclusiveInstant(zone).toEpochMilli(),
            )
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactionDao.observePage(limit, 0).map { rows -> rows.map { it.toDomain() } }.flowOn(io)

    override fun observeByCategory(category: TxnCategory, range: DateRange?): Flow<List<Transaction>> =
        observeQuery(TransactionQuery(categories = setOf(category), range = range))

    override fun observeByMerchant(merchantId: Long, range: DateRange?): Flow<List<Transaction>> =
        observeQuery(TransactionQuery(merchantIds = setOf(merchantId), range = range))

    override fun observeByAccount(accountId: Long, range: DateRange?): Flow<List<Transaction>> =
        observeQuery(TransactionQuery(accountIds = setOf(accountId), range = range))

    override fun observeQuery(query: TransactionQuery): Flow<List<Transaction>> =
        transactionDao
            .observeRawQuery(TransactionQueryBuilder.build(query, zone, limit = query.limit))
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(io)

    override suspend fun page(query: TransactionQuery, limit: Int, offset: Int): List<Transaction> =
        withContext(io) {
            transactionDao
                .rawQuery(TransactionQueryBuilder.build(query, zone, limit = limit, offset = offset))
                .map { it.toDomain() }
        }

    override suspend fun findById(id: Long): Transaction? =
        withContext(io) { transactionDao.findById(id)?.toDomain() }

    override suspend fun count(): Int = withContext(io) { transactionDao.countAll() }

    override suspend fun insert(transaction: Transaction): Long =
        withContext(io) { transactionDao.insert(transaction.toEntity()) }

    override suspend fun insertAll(transactions: List<Transaction>): List<Long> =
        withContext(io) { transactionDao.insertAll(transactions.map { it.toEntity() }) }

    override suspend fun update(transaction: Transaction) =
        withContext(io) { transactionDao.update(transaction.toEntity()) }

    override suspend fun delete(id: Long) {
        withContext(io) { transactionDao.deleteById(id) }
    }

    override suspend fun markDuplicate(id: Long, originalId: Long) =
        withContext(io) { transactionDao.markDuplicate(id, originalId) }

    /**
     * Applies a user correction — and, crucially, remembers it.
     *
     * A correction that only fixed one row would be a chore the user has to repeat forever.
     * Two things generalise it: the raw merchant string is learned as an alias so the
     * merchant resolver gets it right next time, and optionally a [UserRule] is generated so
     * the same correction is replayed over history and over everything that arrives later.
     *
     * `userVerified` is set so that no automatic rule or re-parse will overwrite the user's
     * judgement afterwards — the app must never argue with a correction it was given.
     */
    override suspend fun applyCorrection(correction: TransactionCorrection) = withContext(io) {
        val existing = transactionDao.findById(correction.transactionId)?.toDomain()
            ?: return@withContext

        val corrected = existing.copy(
            category = correction.category ?: existing.category,
            subcategory = correction.subcategory ?: existing.subcategory,
            merchantName = correction.merchantName ?: existing.merchantName,
            type = correction.type ?: existing.type,
            notes = correction.notes ?: existing.notes,
            excludedFromStats = correction.excludedFromStats ?: existing.excludedFromStats,
            userVerified = true,
        )
        transactionDao.update(corrected.toEntity())

        val newMerchantName = correction.merchantName
        val rawMerchant = existing.rawMerchant

        if (correction.learnMerchantAlias && newMerchantName != null && !rawMerchant.isNullOrBlank()) {
            val key = Merchant.normalizeKey(newMerchantName)
            val merchantId = merchantRepository.findByNormalizedKey(key)?.id
                ?: merchantRepository.upsert(
                    Merchant(
                        canonicalName = newMerchantName,
                        normalizedKey = key,
                        defaultCategory = corrected.category,
                        defaultSubcategory = corrected.subcategory,
                    ),
                )
            merchantRepository.addAlias(
                MerchantAlias(
                    merchantId = merchantId,
                    alias = rawMerchant,
                    userDefined = true,
                ),
            )
        }

        if (correction.createRule && !rawMerchant.isNullOrBlank()) {
            ruleRepository.upsert(ruleFrom(correction, corrected, rawMerchant))
        }
    }

    /**
     * Builds a rule that reproduces this correction for anything with the same raw merchant.
     *
     * Matching on the *raw* merchant rather than the corrected name is what makes the rule
     * useful: the raw string is the thing that keeps arriving from the bank, and the
     * corrected name is the thing the user wants to see instead.
     */
    private fun ruleFrom(
        correction: TransactionCorrection,
        corrected: Transaction,
        rawMerchant: String,
    ): UserRule {
        val actions = buildList {
            correction.category?.let { add(UserRule.Action(UserRule.Action.Target.SET_CATEGORY, it.name)) }
            correction.subcategory?.let { add(UserRule.Action(UserRule.Action.Target.SET_SUBCATEGORY, it)) }
            correction.merchantName?.let { add(UserRule.Action(UserRule.Action.Target.SET_MERCHANT, it)) }
            correction.type?.let { add(UserRule.Action(UserRule.Action.Target.SET_TYPE, it.name)) }
            if (correction.excludedFromStats == true) {
                add(UserRule.Action(UserRule.Action.Target.EXCLUDE_FROM_STATS, "true"))
            }
        }
        return UserRule(
            name = "Always treat \"$rawMerchant\" as ${corrected.displayTitle}",
            conditions = listOf(
                UserRule.Condition(
                    field = UserRule.Condition.Field.RAW_MERCHANT,
                    operator = UserRule.Condition.Operator.EQUALS,
                    value = rawMerchant,
                ),
            ),
            actions = actions,
        )
    }
}
