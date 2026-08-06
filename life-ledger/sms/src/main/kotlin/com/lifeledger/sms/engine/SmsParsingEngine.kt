package com.lifeledger.sms.engine

import com.lifeledger.core.common.time.TimeProvider
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.ParseLogEntry
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.api.ParserRegistry
import com.lifeledger.sms.categorize.TransactionClassifier
import com.lifeledger.sms.dedupe.DuplicateDetector
import com.lifeledger.sms.merchant.MerchantResolution
import com.lifeledger.sms.merchant.MerchantResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one message through parsing and enrichment, and returns everything the data layer
 * needs to persist it.
 *
 * The engine performs **no I/O**. That is what lets a backfill parallelise across a thread
 * pool without contending on the database, and what lets the whole pipeline be exercised in
 * a plain JVM test over a corpus of thousands of messages. Persisting the result — and the
 * duplicate *lookup*, which genuinely needs the database — belongs to the data layer.
 */
@Singleton
class SmsParsingEngine @Inject constructor(
    private val registry: ParserRegistry,
    private val merchantResolver: MerchantResolver,
    private val classifier: TransactionClassifier,
    private val duplicateDetector: DuplicateDetector,
    private val timeProvider: TimeProvider,
) {

    fun process(sms: SmsRecord): EngineResult {
        val context = ParserContext(
            zone = timeProvider.zone(),
            fallbackInstant = sms.receivedAt,
        )

        val startNanos = System.nanoTime()
        var lastFailure: ParseResult.Failure? = null
        var firstIgnored: ParseResult.Ignored? = null

        for (parser in registry.parsers()) {
            if (!parser.canHandle(sms)) continue

            val result = try {
                parser.parse(sms, context)
            } catch (t: Throwable) {
                // A parser must never take the pipeline down with it. Record the failure,
                // then keep trying — a lower-priority parser may still handle the message.
                ParseResult.Failure(parser.info.id, t.message ?: t::class.java.simpleName, t)
            }

            when (result) {
                is ParseResult.Success -> return enrich(sms, result, elapsedMicros(startNanos))
                is ParseResult.Ignored -> firstIgnored = firstIgnored ?: result
                is ParseResult.Failure -> lastFailure = result
                ParseResult.NotApplicable -> Unit
            }
        }

        val micros = elapsedMicros(startNanos)
        return when {
            firstIgnored != null -> EngineResult.Ignored(
                log = log(sms, firstIgnored.parserId, ParseLogEntry.Outcome.IGNORED, firstIgnored.reason, micros),
            )
            lastFailure != null -> EngineResult.Failed(
                log = log(sms, lastFailure.parserId, ParseLogEntry.Outcome.FAILURE, lastFailure.reason, micros),
            )
            else -> EngineResult.Unmatched(
                log = log(sms, null, ParseLogEntry.Outcome.UNMATCHED, "no parser claimed the message", micros),
            )
        }
    }

    /**
     * Applies merchant resolution and categorisation to a raw parse.
     *
     * Order matters: the merchant is resolved first because a known merchant carries a
     * default category that is more reliable than any keyword hit on the message body.
     */
    private fun enrich(
        sms: SmsRecord,
        success: ParseResult.Success,
        micros: Long,
    ): EngineResult.Parsed {
        val parsed = success.transaction
        val resolution = parsed.rawMerchant?.let { merchantResolver.resolve(it) }
        val classification = classifier.classify(
            parsed = parsed,
            body = sms.body,
            merchantCategory = resolution?.category,
            merchantSubcategory = resolution?.subcategory,
        )

        val enriched = parsed.copy(
            category = classification.category,
            confidence = parsed.confidence,
        )

        return EngineResult.Parsed(
            parsed = enriched,
            parserId = success.parserId,
            merchant = resolution,
            merchantConfidence = resolution?.confidence ?: Confidence.NONE,
            categoryReason = classification.reason,
            dedupeHash = duplicateDetector.fingerprint(enriched, sms),
            log = log(sms, success.parserId, ParseLogEntry.Outcome.SUCCESS, null, micros, enriched.confidence),
        )
    }

    private fun elapsedMicros(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000

    private fun log(
        sms: SmsRecord,
        parserId: String?,
        outcome: ParseLogEntry.Outcome,
        reason: String?,
        micros: Long,
        confidence: Confidence = Confidence.NONE,
    ) = ParseLogEntry(
        smsId = sms.id,
        parserId = parserId,
        outcome = outcome,
        reason = reason,
        durationMicros = micros,
        confidence = confidence,
        at = timeProvider.now(),
        // Snippets only: the parse log is a debugging aid, not a second copy of the inbox.
        senderSnippet = sms.sender.take(SNIPPET_LENGTH),
        bodySnippet = sms.body.take(SNIPPET_LENGTH),
    )

    private companion object {
        const val SNIPPET_LENGTH = 80
    }
}

/** What the engine concluded about one message. */
sealed interface EngineResult {

    val log: ParseLogEntry

    data class Parsed(
        val parsed: ParsedTransaction,
        val parserId: String,
        val merchant: MerchantResolution?,
        val merchantConfidence: Confidence,
        val categoryReason: String,
        val dedupeHash: String,
        override val log: ParseLogEntry,
    ) : EngineResult {
        val category: TxnCategory get() = parsed.category ?: TxnCategory.UNCATEGORIZED
    }

    data class Ignored(override val log: ParseLogEntry) : EngineResult
    data class Unmatched(override val log: ParseLogEntry) : EngineResult
    data class Failed(override val log: ParseLogEntry) : EngineResult
}
