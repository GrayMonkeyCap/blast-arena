package com.lifeledger.domain.nlq

import com.lifeledger.core.common.format.DateTimeFormatters
import com.lifeledger.core.common.format.MoneyFormatter
import com.lifeledger.core.common.time.TimeProvider
import com.lifeledger.core.model.CategoryTotal
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.QueryAnswer
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.data.repository.MerchantRepository
import com.lifeledger.data.repository.SearchRepository
import com.lifeledger.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The ask bar's implementation: interpret the question, then answer it from the database.
 *
 * The split between [QueryInterpreter] and [QueryAnswerer] is the load-bearing part. An
 * interpreter — rule-based today, possibly a small on-device model later — decides *what*
 * was asked. Only [DefaultQueryAnswerer] produces figures, and it produces them by querying
 * stored transactions. A future model can therefore misread a question, and the worst
 * outcome is a correctly computed answer to the wrong question, shown next to the
 * interpretation that produced it. It can never invent an amount.
 */
@Singleton
class DefaultAskService @Inject constructor(
    private val interpreter: QueryInterpreter,
    private val answerer: QueryAnswerer,
    private val merchantRepository: MerchantRepository,
    private val searchRepository: SearchRepository,
    private val timeProvider: TimeProvider,
) : AskService {

    override suspend fun ask(question: String): QueryAnswer {
        val context = InterpreterContext(
            today = timeProvider.today(),
            zone = timeProvider.zone(),
            knownMerchants = merchantRepository.observeAll().first().map { it.canonicalName },
        )
        val interpretation = interpreter.interpret(question, context)
        searchRepository.rememberQuery(question)
        return answerer.answer(question, interpretation)
    }

    override fun samples(): List<String> = listOf(
        "What did I spend on coffee last month?",
        "When was my last insurance payment?",
        "How much did I invest this year?",
        "Show travel expenses",
        "How many times did I order Swiggy in March?",
        "Salary",
        "Anything over ₹5000 last week",
    )
}

/**
 * Turns an [Interpretation] into a rendered answer.
 *
 * Each [QueryIntent] gets its own headline shape, because "₹2,340" answers a *how much*
 * question and answers a *when* question not at all. The matching rows are always attached
 * whatever the intent, so the user can drill from any answer into the transactions that
 * produced it — an answer nobody can verify is not worth showing.
 */
@Singleton
class DefaultQueryAnswerer @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : QueryAnswerer {

    override suspend fun answer(question: String, interpretation: Interpretation): QueryAnswer {
        val query = interpretation.query
        val transactions = transactionRepository.page(
            query = query.copy(limit = null),
            limit = MAX_ROWS,
            offset = 0,
        )

        val total = Money(transactions.sumOf { it.amount.minor })
        val breakdown = breakdownOf(transactions, total)

        val headline = when (interpretation.intent) {
            QueryIntent.TOTAL -> totalHeadline(total, transactions.size)
            QueryIntent.COUNT -> countHeadline(transactions.size)
            QueryIntent.LATEST -> latestHeadline(transactions.firstOrNull())
            QueryIntent.AVERAGE -> averageHeadline(query, total, transactions)
            QueryIntent.BREAKDOWN -> breakdownHeadline(breakdown)
            QueryIntent.COMPARE, QueryIntent.LIST, QueryIntent.UNKNOWN ->
                listHeadline(transactions.size, total)
        }

        return QueryAnswer(
            question = question,
            headline = headline,
            total = total.takeIf { transactions.isNotEmpty() },
            count = transactions.size,
            range = query.range,
            transactions = transactions.take(PREVIEW_ROWS),
            breakdown = breakdown,
            interpretedQuery = query,
            confidence = interpretation.confidence,
            explanation = buildString {
                append(interpretation.explanation)
                if (interpretation.unresolvedTerms.isNotEmpty()) {
                    append(" Ignored: ")
                    append(interpretation.unresolvedTerms.joinToString(", "))
                    append('.')
                }
            },
        )
    }

    private fun totalHeadline(total: Money, count: Int): String =
        if (count == 0) {
            "Nothing found for that."
        } else {
            "${MoneyFormatter.format(total)} across $count ${plural(count, "transaction")}."
        }

    private fun countHeadline(count: Int): String =
        if (count == 0) "None found." else "$count ${plural(count, "time")}."

    private fun latestHeadline(latest: Transaction?): String = when (latest) {
        null -> "No matching transaction found."
        else -> "${DateTimeFormatters.date(latest.localDate())} — " +
            "${MoneyFormatter.format(latest.amount)} at ${latest.displayTitle}."
    }

    private fun averageHeadline(
        query: TransactionQuery,
        total: Money,
        transactions: List<Transaction>,
    ): String {
        if (transactions.isEmpty()) return "Nothing to average."
        val days = query.range?.dayCount ?: transactions.distinctBy { it.localDate() }.size
        val perDay = Money(total.minor / days.coerceAtLeast(1))
        return "${MoneyFormatter.format(perDay)} a day, over $days ${plural(days, "day")}."
    }

    private fun breakdownHeadline(breakdown: List<CategoryTotal>): String {
        val top = breakdown.firstOrNull() ?: return "Nothing to break down."
        return "${top.category.displayName} led at ${MoneyFormatter.format(top.total)} " +
            "(${(top.shareOfTotal * 100).toInt()}%)."
    }

    private fun listHeadline(count: Int, total: Money): String = when (count) {
        0 -> "Nothing found for that."
        1 -> "1 transaction, ${MoneyFormatter.format(total)}."
        else -> "$count transactions, ${MoneyFormatter.format(total)}."
    }

    private fun breakdownOf(transactions: List<Transaction>, total: Money): List<CategoryTotal> {
        if (transactions.isEmpty()) return emptyList()
        return transactions
            .groupBy { it.category }
            .map { (category: TxnCategory, rows) ->
                val subtotal = Money(rows.sumOf { it.amount.minor })
                CategoryTotal(
                    category = category,
                    total = subtotal,
                    transactionCount = rows.size,
                    shareOfTotal = subtotal.ratioTo(total) ?: 0.0,
                )
            }
            .sortedByDescending { it.total.minor }
    }

    private fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"

    private companion object {
        /** Enough to compute an honest total without ever materialising a whole history. */
        const val MAX_ROWS = 5_000
        const val PREVIEW_ROWS = 100
    }
}
