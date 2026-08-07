package com.lifeledger.domain.insight

import com.lifeledger.core.common.di.DefaultDispatcher
import com.lifeledger.core.common.log.AppLog
import com.lifeledger.core.common.time.TimeProvider
import com.lifeledger.core.model.Bill
import com.lifeledger.core.model.CashFlow
import com.lifeledger.core.model.CategoryTotal
import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Insight
import com.lifeledger.core.model.Investment
import com.lifeledger.core.model.MerchantTotal
import com.lifeledger.core.model.Subscription
import com.lifeledger.core.model.Transaction
import com.lifeledger.data.repository.BillRepository
import com.lifeledger.data.repository.InsightRepository
import com.lifeledger.data.repository.InvestmentRepository
import com.lifeledger.data.repository.StatisticsRepository
import com.lifeledger.data.repository.SubscriptionRepository
import com.lifeledger.data.repository.TransactionRepository
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Runs every registered generator and replaces the stored insight set.
 *
 * Generators run concurrently but share one [InsightData] instance whose reads are memoised
 * for the duration of a refresh. With a dozen generators all asking for "this month's cash
 * flow", the difference between memoising and not is one query against a hundred-thousand
 * row table versus twelve.
 *
 * A generator that throws is logged and skipped rather than failing the whole refresh: one
 * bad heuristic must not cost the user every other insight.
 */
@Singleton
class DefaultInsightEngine @Inject constructor(
    private val generators: Set<@JvmSuppressWildcards InsightGenerator>,
    private val insightRepository: InsightRepository,
    private val transactionRepository: TransactionRepository,
    private val statisticsRepository: StatisticsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val billRepository: BillRepository,
    private val investmentRepository: InvestmentRepository,
    private val timeProvider: TimeProvider,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : InsightEngine {

    override suspend fun refresh(): List<Insight> = withContext(dispatcher) {
        val today = timeProvider.today()
        val data = MemoisedInsightData(
            transactionRepository = transactionRepository,
            statisticsRepository = statisticsRepository,
            subscriptionRepository = subscriptionRepository,
            billRepository = billRepository,
            investmentRepository = investmentRepository,
        )

        val earliest = data.earliestTransactionDate()
        val daysOfData = earliest?.let { ChronoUnit.DAYS.between(it, today).toInt() } ?: 0

        val context = InsightContext(
            today = today,
            zone = timeProvider.zone(),
            firstTransactionDate = earliest,
            repositories = data,
        )

        val produced = coroutineScope {
            val running: List<Deferred<List<Insight>>> = generators
                .filter { daysOfData >= it.minimumDaysOfData }
                .map { generator ->
                    async {
                        runCatching { generator.generate(context) }
                            .onFailure { error ->
                                AppLog.e(TAG, error) { "Insight generator ${generator.id} failed" }
                            }
                            .getOrDefault(emptyList())
                    }
                }
            running.map { it.await() }
        }.flatten()

        // Generators are independent, so two can legitimately produce the same finding.
        // De-duplicating on the key here keeps the UI clean without coupling them.
        val deduped = produced.distinctBy { it.dedupeKey }
        insightRepository.replaceAll(deduped)
        deduped
    }

    private companion object {
        const val TAG = "InsightEngine"
    }
}

/**
 * [InsightData] backed by the repositories, with per-refresh memoisation.
 *
 * The engine creates one instance per refresh and the concurrent generators only read from
 * it. Values are cached on first request keyed by range, because the access pattern is a
 * handful of distinct windows requested many times over.
 */
private class MemoisedInsightData(
    private val transactionRepository: TransactionRepository,
    private val statisticsRepository: StatisticsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val billRepository: BillRepository,
    private val investmentRepository: InvestmentRepository,
) : InsightData {

    private val transactionCache = java.util.concurrent.ConcurrentHashMap<DateRange, List<Transaction>>()
    private val cashFlowCache = java.util.concurrent.ConcurrentHashMap<DateRange, CashFlow>()
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<DateRange, List<CategoryTotal>>()

    @Volatile
    private var subscriptionsCache: List<Subscription>? = null

    @Volatile
    private var billsCache: List<Bill>? = null

    @Volatile
    private var investmentsCache: List<Investment>? = null

    override suspend fun transactions(range: DateRange): List<Transaction> =
        transactionCache[range] ?: transactionRepository.observeRange(range).first()
            .also { transactionCache[range] = it }

    override suspend fun cashFlow(range: DateRange): CashFlow =
        cashFlowCache[range] ?: statisticsRepository.observeCashFlow(range).first()
            .also { cashFlowCache[range] = it }

    override suspend fun categoryTotals(range: DateRange): List<CategoryTotal> =
        categoryCache[range] ?: statisticsRepository
            .observeCategoryTotals(range, Direction.DEBIT).first()
            .also { categoryCache[range] = it }

    override suspend fun merchantTotals(range: DateRange, limit: Int): List<MerchantTotal> =
        statisticsRepository.observeMerchantTotals(range, limit).first()

    override suspend fun subscriptions(): List<Subscription> =
        subscriptionsCache ?: subscriptionRepository.observeAll().first()
            .also { subscriptionsCache = it }

    override suspend fun bills(): List<Bill> =
        billsCache ?: billRepository.observeAll().first().also { billsCache = it }

    override suspend fun investments(): List<Investment> =
        investmentsCache ?: investmentRepository.observeAll().first()
            .also { investmentsCache = it }

    /** Oldest transaction date, which decides which generators have enough history to run. */
    suspend fun earliestTransactionDate(): java.time.LocalDate? =
        transactionRepository
            .page(
                query = com.lifeledger.core.model.TransactionQuery(
                    sort = com.lifeledger.core.model.TransactionQuery.Sort.DATE_ASC,
                ),
                limit = 1,
                offset = 0,
            )
            .firstOrNull()
            ?.localDate()
}
