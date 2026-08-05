package com.lifeledger.domain.nlq

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.QueryAnswer
import com.lifeledger.core.model.TransactionQuery

/**
 * Turns a sentence into a [TransactionQuery].
 *
 * This is the seam that keeps the "AI" story honest. Life Ledger ships a deterministic,
 * rule-based interpreter — it costs nothing, runs in microseconds, works offline by
 * construction, and can always explain itself. But the *interface* is model-shaped: an
 * on-device LLM can be bound here later and every consumer (Search, Insights, the ask-bar)
 * keeps working unchanged, because the contract is "sentence in, structured query out"
 * rather than "sentence in, prose out".
 *
 * Implementations must never perform I/O to a network. That is enforced socially by review
 * and structurally by the app having no INTERNET permission.
 */
interface QueryInterpreter {

    /** Stable id shown in Developer Mode, e.g. `rules.v1` or `gemma-2b-it.q4`. */
    val id: String

    /** True when this interpreter is usable right now (a model interpreter may need weights). */
    suspend fun isAvailable(): Boolean

    suspend fun interpret(question: String, context: InterpreterContext): Interpretation
}

/**
 * Ambient facts an interpreter may need — supplied rather than looked up, so interpreters
 * stay pure and testable, and so a model implementation cannot quietly reach into storage.
 */
data class InterpreterContext(
    val today: java.time.LocalDate,
    val zone: java.time.ZoneId,
    /** Merchant names known to this user, so "swiggy" resolves without a catalogue lookup. */
    val knownMerchants: List<String> = emptyList(),
    val knownTags: List<String> = emptyList(),
    val knownAccounts: List<String> = emptyList(),
)

/**
 * The interpreter's reading of a question.
 *
 * [explanation] is mandatory, not decorative: the UI shows it under every answer so the
 * user can see the question was understood as "food category, last calendar month" before
 * trusting the number.
 */
data class Interpretation(
    val query: TransactionQuery,
    val intent: QueryIntent,
    val confidence: Confidence,
    val explanation: String,
    /** Tokens the interpreter could not account for; shown as "ignored: …" when non-empty. */
    val unresolvedTerms: List<String> = emptyList(),
)

/** What the user is asking *for*, which decides how the answer is rendered. */
enum class QueryIntent {
    /** "How much did I spend on food?" → a total. */
    TOTAL,

    /** "Show all Swiggy" → a list. */
    LIST,

    /** "When was my last insurance payment?" → a single most-recent item. */
    LATEST,

    /** "How many times did I visit hospitals?" → a count. */
    COUNT,

    /** "What did I spend most on?" → a ranked breakdown. */
    BREAKDOWN,

    /** "Am I spending more on food than last month?" → a period-over-period comparison. */
    COMPARE,

    /** "Average daily spend this month" → a derived statistic. */
    AVERAGE,

    UNKNOWN,
}

/**
 * Runs an [Interpretation] against the data layer and produces a rendered answer.
 * Separated from interpretation so a future model interpreter cannot bypass the
 * repository layer or invent numbers: only this class is allowed to produce figures.
 */
interface QueryAnswerer {
    suspend fun answer(question: String, interpretation: Interpretation): QueryAnswer
}

/** Front door used by the UI: interpret, then answer. */
interface AskService {
    suspend fun ask(question: String): QueryAnswer

    /** Example questions offered on the empty state of the ask bar. */
    fun samples(): List<String>
}
