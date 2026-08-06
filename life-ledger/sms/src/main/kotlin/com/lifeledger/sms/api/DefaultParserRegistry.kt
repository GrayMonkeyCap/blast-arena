package com.lifeledger.sms.api

import com.lifeledger.core.model.ParserInfo
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shipped [ParserRegistry].
 *
 * Enablement is held in memory and mirrored to the database by the data layer rather than
 * read from it here: the registry is consulted once per message during a backfill of
 * potentially 100,000 messages, and a database round trip on that path would dominate the
 * cost of parsing itself.
 */
@Singleton
class DefaultParserRegistry @Inject constructor(
    private val allParsers: Set<@JvmSuppressWildcards SmsParser>,
) : ParserRegistry {

    private val disabled = ConcurrentHashMap.newKeySet<String>()

    /**
     * Sorted once at construction. Priority ascending puts bank-specific parsers ahead of
     * the generic fallbacks, and the id tiebreak makes the order deterministic so that two
     * parsers claiming the same message always resolve the same way.
     */
    private val ordered: List<SmsParser> =
        allParsers.sortedWith(compareBy({ it.info.priority }, { it.info.id }))

    override fun parsers(): List<SmsParser> =
        if (disabled.isEmpty()) ordered else ordered.filter { it.info.id !in disabled }

    override fun allInfo(): List<ParserInfo> =
        ordered.map { it.info.copy(enabled = it.info.id !in disabled) }

    override fun setEnabled(parserId: String, enabled: Boolean) {
        if (enabled) disabled.remove(parserId) else disabled.add(parserId)
    }

    /** Restores enablement state loaded from storage at startup. */
    fun applyDisabledSet(ids: Collection<String>) {
        disabled.clear()
        disabled.addAll(ids)
    }
}
