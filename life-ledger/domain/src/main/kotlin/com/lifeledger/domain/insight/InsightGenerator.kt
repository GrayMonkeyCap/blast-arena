package com.lifeledger.domain.insight

import com.lifeledger.core.model.Insight
import java.time.LocalDate

/**
 * One family of observations about the user's data.
 *
 * Insights are split into small generators rather than one analyser because each has its
 * own window, its own notion of "interesting", and its own failure mode. Adding an insight
 * means adding a generator and a multibinding — never editing an existing one.
 *
 * A generator must be deterministic for a given input window: the engine reruns them and
 * replaces the previous batch, so a flapping generator would produce notification noise.
 */
interface InsightGenerator {

    /** Stable id, used as the prefix of every [Insight.dedupeKey] this generator emits. */
    val id: String

    /** Minimum history needed before this generator says anything useful. */
    val minimumDaysOfData: Int get() = 30

    suspend fun generate(context: InsightContext): List<Insight>
}

/**
 * Everything a generator is allowed to read.
 *
 * Generators receive an explicit context instead of injecting repositories so that the
 * engine can fetch shared data once for all of them — with tens of generators, letting
 * each query independently would turn one insight run into hundreds of queries.
 */
data class InsightContext(
    val today: LocalDate,
    val zone: java.time.ZoneId,
    val firstTransactionDate: LocalDate?,
    val repositories: InsightData,
)

/**
 * Narrow read-only view of the data layer handed to generators. Deliberately smaller than
 * the full repository surface: a generator has no business writing anything.
 */
interface InsightData {
    suspend fun transactions(range: com.lifeledger.core.model.DateRange): List<com.lifeledger.core.model.Transaction>
    suspend fun cashFlow(range: com.lifeledger.core.model.DateRange): com.lifeledger.core.model.CashFlow
    suspend fun categoryTotals(range: com.lifeledger.core.model.DateRange): List<com.lifeledger.core.model.CategoryTotal>
    suspend fun merchantTotals(range: com.lifeledger.core.model.DateRange, limit: Int): List<com.lifeledger.core.model.MerchantTotal>
    suspend fun subscriptions(): List<com.lifeledger.core.model.Subscription>
    suspend fun bills(): List<com.lifeledger.core.model.Bill>
    suspend fun investments(): List<com.lifeledger.core.model.Investment>
}

/** Runs every registered generator and replaces the stored insight set. */
interface InsightEngine {
    suspend fun refresh(): List<Insight>
}
