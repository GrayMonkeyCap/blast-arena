package com.lifeledger.data.repository

import com.lifeledger.core.model.Account
import com.lifeledger.core.model.Bill
import com.lifeledger.core.model.CashFlow
import com.lifeledger.core.model.CategoryTotal
import com.lifeledger.core.model.DashboardSnapshot
import com.lifeledger.core.model.DateRange
import com.lifeledger.core.model.DayIntensity
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Insight
import com.lifeledger.core.model.Investment
import com.lifeledger.core.model.InvestmentTransaction
import com.lifeledger.core.model.Merchant
import com.lifeledger.core.model.MerchantAlias
import com.lifeledger.core.model.MerchantTotal
import com.lifeledger.core.model.ParseLogEntry
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PeriodGranularity
import com.lifeledger.core.model.PeriodTotal
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.Streak
import com.lifeledger.core.model.Subscription
import com.lifeledger.core.model.Tag
import com.lifeledger.core.model.TimelineDay
import com.lifeledger.core.model.TimelineEvent
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.core.model.UserRule
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts for the whole application.
 *
 * These interfaces are the seam between "what the app needs" and "how it is stored".
 * They are declared together because they form one coherent contract that the feature
 * modules program against; the Room-backed implementations live in
 * [com.lifeledger.data.repository] alongside them but are never referenced directly.
 *
 * Two conventions hold throughout:
 *  - Reads that a screen observes return [Flow]. Reads used inside a computation are
 *    `suspend` one-shots. A repository never exposes a blocking call.
 *  - Writes are `suspend` and return the affected id, so a caller can navigate to what it
 *    just created without a second query.
 */

interface TransactionRepository {
    fun observeById(id: Long): Flow<Transaction?>
    fun observeRange(range: DateRange): Flow<List<Transaction>>
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observeByCategory(category: TxnCategory, range: DateRange?): Flow<List<Transaction>>
    fun observeByMerchant(merchantId: Long, range: DateRange?): Flow<List<Transaction>>
    fun observeByAccount(accountId: Long, range: DateRange?): Flow<List<Transaction>>

    /** Backing query for the paged transaction list; [offset] pages through history. */
    suspend fun page(query: TransactionQuery, limit: Int, offset: Int): List<Transaction>

    /** Live results for an arbitrary filter — used by Search and every filtered list. */
    fun observeQuery(query: TransactionQuery): Flow<List<Transaction>>

    suspend fun findById(id: Long): Transaction?
    suspend fun count(): Int

    suspend fun insert(transaction: Transaction): Long
    suspend fun insertAll(transactions: List<Transaction>): List<Long>
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: Long)

    /** Records that [id] is a duplicate of [originalId]; it stops counting toward totals. */
    suspend fun markDuplicate(id: Long, originalId: Long)

    /**
     * Applies a user correction and remembers it: the merchant alias is learned and any
     * matching rule is offered, which is how the app's accuracy improves over time.
     */
    suspend fun applyCorrection(correction: TransactionCorrection)
}

/** A user's manual fix to one transaction, plus whether to generalise it. */
data class TransactionCorrection(
    val transactionId: Long,
    val category: TxnCategory? = null,
    val subcategory: String? = null,
    val merchantName: String? = null,
    val type: com.lifeledger.core.model.TransactionType? = null,
    val notes: String? = null,
    val excludedFromStats: Boolean? = null,
    /** When true, the raw merchant string is remembered as an alias for [merchantName]. */
    val learnMerchantAlias: Boolean = true,
    /** When true, a [UserRule] is generated so future matching messages are fixed too. */
    val createRule: Boolean = false,
)

interface SmsRepository {
    fun observeAll(limit: Int, offset: Int): Flow<List<SmsRecord>>
    fun observeCounts(): Flow<SmsCounts>
    suspend fun latestReceivedAtMillis(): Long?
    suspend fun insertNew(records: List<SmsRecord>): Int
    suspend fun pending(limit: Int): List<SmsRecord>
    suspend fun markProcessed(id: Long, status: SmsRecord.ProcessingStatus, parserId: String?)
    suspend fun deleteOlderThan(millis: Long): Int
    /** Clears every parse result and requeues all messages — used after a parser upgrade. */
    suspend fun requeueAll(): Int
}

data class SmsCounts(
    val total: Int = 0,
    val pending: Int = 0,
    val parsed: Int = 0,
    val ignored: Int = 0,
    val unmatched: Int = 0,
    val failed: Int = 0,
)

interface MerchantRepository {
    fun observeAll(): Flow<List<Merchant>>
    fun observeTop(range: DateRange, limit: Int): Flow<List<MerchantTotal>>
    suspend fun findByNormalizedKey(key: String): Merchant?
    suspend fun findByAlias(normalizedAlias: String): Merchant?
    suspend fun upsert(merchant: Merchant): Long
    suspend fun addAlias(alias: MerchantAlias): Long
    suspend fun aliasesFor(merchantId: Long): List<MerchantAlias>
    suspend fun merge(sourceId: Long, targetId: Long)
    /** Seeds the shipped merchant catalogue on first run; idempotent. */
    suspend fun ensureCatalogueSeeded()
}

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    fun observeById(id: Long): Flow<Account?>
    suspend fun findOrCreate(bankCode: String?, maskedNumber: String?, type: com.lifeledger.core.model.AccountType): Account
    suspend fun upsert(account: Account): Long
    suspend fun archive(id: Long, archived: Boolean)
}

interface InvestmentRepository {
    fun observeAll(): Flow<List<Investment>>
    fun observeById(id: Long): Flow<Investment?>
    fun observeContributions(investmentId: Long): Flow<List<InvestmentTransaction>>
    fun observeAllocation(): Flow<List<CategoryTotal>>
    fun observeMonthlyInvested(range: DateRange): Flow<List<PeriodTotal>>
    suspend fun upsert(investment: Investment): Long
    suspend fun recordContribution(entry: InvestmentTransaction): Long
    suspend fun setManualValue(id: Long, valueMinor: Long)
}

interface SubscriptionRepository {
    fun observeAll(): Flow<List<Subscription>>
    fun observeActive(): Flow<List<Subscription>>
    fun observeMonthlyCost(): Flow<com.lifeledger.core.model.Money>
    suspend fun upsert(subscription: Subscription): Long
    suspend fun setStatus(id: Long, status: Subscription.Status)
    suspend fun delete(id: Long)
}

interface BillRepository {
    fun observeAll(): Flow<List<Bill>>
    fun observeUpcoming(withinDays: Int): Flow<List<Bill>>
    fun observeOverdue(): Flow<List<Bill>>
    suspend fun upsert(bill: Bill): Long
    suspend fun markPaid(id: Long, transactionId: Long?)
    suspend fun delete(id: Long)
}

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    suspend fun create(name: String): Long
    suspend fun attach(transactionId: Long, tagId: Long)
    suspend fun detach(transactionId: Long, tagId: Long)
    suspend fun delete(id: Long)
}

interface TimelineRepository {
    /** The life timeline, already grouped into days, newest first. */
    fun observeDays(range: DateRange): Flow<List<TimelineDay>>
    fun observeRecent(limit: Int): Flow<List<TimelineEvent>>
    suspend fun page(before: java.time.Instant?, limit: Int): List<TimelineDay>
    suspend fun record(event: TimelineEvent): Long
    suspend fun setPinned(id: Long, pinned: Boolean)
}

interface InsightRepository {
    fun observeActive(): Flow<List<Insight>>
    fun observePinned(): Flow<List<Insight>>
    suspend fun replaceAll(insights: List<Insight>)
    suspend fun dismiss(id: Long)
    suspend fun setPinned(id: Long, pinned: Boolean)
}

interface RuleRepository {
    fun observeAll(): Flow<List<UserRule>>
    suspend fun enabledRules(): List<UserRule>
    suspend fun upsert(rule: UserRule): Long
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
    /** Re-runs [rule] over stored history and returns how many rows it changed. */
    suspend fun applyToHistory(rule: UserRule): Int
}

interface ParserLogRepository {
    fun observeRecent(limit: Int): Flow<List<ParseLogEntry>>
    fun observeParsers(): Flow<List<ParserInfo>>
    suspend fun log(entry: ParseLogEntry)
    suspend fun setParserEnabled(parserId: String, enabled: Boolean)
    suspend fun purgeOlderThan(millis: Long): Int
    suspend fun stats(): Map<String, ParserStats>
}

data class ParserStats(
    val parserId: String,
    val success: Int,
    val ignored: Int,
    val failure: Int,
    val averageMicros: Long,
)

/**
 * Read-only aggregation over transactions.
 *
 * Kept separate from [TransactionRepository] because the two have genuinely different
 * consumers and caching needs: lists want rows, dashboards want pre-reduced numbers, and
 * mixing them produces a repository nobody can change safely.
 */
interface StatisticsRepository {
    fun observeCashFlow(range: DateRange): Flow<CashFlow>
    fun observeDashboard(): Flow<DashboardSnapshot>
    fun observeCategoryTotals(range: DateRange, direction: Direction): Flow<List<CategoryTotal>>
    fun observeMerchantTotals(range: DateRange, limit: Int): Flow<List<MerchantTotal>>
    fun observePeriodTotals(range: DateRange, granularity: PeriodGranularity): Flow<List<PeriodTotal>>
    fun observeDayIntensity(range: DateRange): Flow<List<DayIntensity>>
    suspend fun streaks(): List<Streak>
    suspend fun largestExpense(range: DateRange): Transaction?
    suspend fun largestIncome(range: DateRange): Transaction?
}

/**
 * Full-text and structured search.
 *
 * Backed by SQLite FTS4 today. The interface deliberately takes a [TransactionQuery]
 * rather than a raw string so that the natural-language layer — and, later, an on-device
 * model — can target the same entry point without the storage layer changing.
 */
interface SearchRepository {
    fun search(query: TransactionQuery): Flow<List<Transaction>>
    suspend fun suggestions(prefix: String, limit: Int): List<SearchSuggestion>
    suspend fun recentQueries(limit: Int): List<String>
    suspend fun rememberQuery(text: String)
}

data class SearchSuggestion(
    val label: String,
    val kind: Kind,
    val query: TransactionQuery,
) {
    enum class Kind { MERCHANT, CATEGORY, AMOUNT, PERIOD, TAG, ACCOUNT, FREE_TEXT }
}
