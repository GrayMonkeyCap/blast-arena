package com.lifeledger.sms.merchant

import com.lifeledger.core.common.text.TextSimilarity
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Merchant
import com.lifeledger.core.model.TxnCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What resolving a raw merchant string produced.
 *
 * [catalogEntry] is `null` whenever the match came from a learned alias (the caller has no
 * catalogue row, only a canonical name) or from the title-case fallback — callers that need
 * `isSubscription`/`isBill`/`isInvestment` should treat a `null` entry as "unknown", not "no".
 */
data class MerchantResolution(
    val canonicalName: String,
    val category: TxnCategory?,
    val subcategory: String?,
    val confidence: Confidence,
    /** The catalogue/learned alias string that actually matched, for audit and debugging. */
    val matchedAlias: String?,
    val isPassThrough: Boolean,
    val catalogEntry: CatalogEntry?,
)

/**
 * A user-taught mapping, ready for the data layer to persist as a [com.lifeledger.core.model.MerchantAlias].
 *
 * [MerchantResolver] never touches storage itself (see [MerchantResolver.learn]); this is
 * the handoff shape between "the user just corrected a transaction" and "the database has a
 * durable row for it".
 */
data class LearnedAlias(
    val rawAlias: String,
    val normalizedAlias: String,
    val canonicalName: String,
)

/**
 * Read-only lookup over merchant aliases the user has taught the app, or that a previous
 * fuzzy/learned resolution already committed to storage.
 *
 * This lives in the `sms` module only as an interface so [MerchantResolver] can stay free of
 * a database dependency; the real implementation is backed by the `core.database` module and
 * injected by the app. [key] is always a [Merchant.normalizeKey]-normalized string.
 */
fun interface AliasLookup {
    fun findByNormalizedAlias(key: String): MerchantResolution?
}

/** Used wherever no persistence-backed [AliasLookup] has been wired in yet. */
object NoOpAliasLookup : AliasLookup {
    override fun findByNormalizedAlias(key: String): MerchantResolution? = null
}

/**
 * Turns whatever a bank/UPI/card-network SMS calls a merchant into a stable, categorised
 * identity.
 *
 * The resolver is intentionally stateless: every call is a pure function of its inputs plus
 * the immutable [MerchantCatalog]. Learning ([learn]) only *describes* the alias that should
 * be remembered — persisting it, and feeding it back in via [AliasLookup] on the next call,
 * is the data layer's job. Keeping that boundary explicit is what lets this module be tested
 * with plain JUnit and no database at all.
 */
interface MerchantResolver {

    /** Resolves one raw merchant string as it appeared on a [com.lifeledger.core.model.ParsedTransaction]. */
    fun resolve(rawMerchant: String): MerchantResolution

    /**
     * Packages a user correction as an alias to persist. This does not mutate any state on
     * the resolver — call it, then hand the result to the database layer.
     */
    fun learn(raw: String, canonicalName: String): LearnedAlias
}

/**
 * Default [MerchantResolver], backed by [MerchantCatalog] and an injectable [AliasLookup].
 *
 * ## Resolution order, and why
 *
 * 1. **Learned alias exact hit** ([AliasLookup]) — checked before anything else. A user who
 *    corrected "SWIGGY BLR 09" to "Swiggy" once should never see the app second-guess that
 *    correction with a catalogue or fuzzy match; a human decision always outranks a heuristic.
 * 2. **Exact normalized-key hit against the catalogue** → [Confidence.CERTAIN]. Bank SMS
 *    narrations for well-known merchants are drawn from a small, stable vocabulary
 *    (`AMZN*MKTP`, `SWIGGY*BANGALORE`); an exact match against a hand-curated alias is as
 *    trustworthy as data gets, so it is tried before any more permissive strategy has a
 *    chance to produce a *wrong* confident answer.
 * 3. **UPI-VPA handle extraction** — the part of a VPA before `@` is sometimes the merchant
 *    name in the clear (`swiggy@ybl`) and sometimes an opaque per-transaction id
 *    (`q123456@ybl`). Restricting this step to alphabetic handles avoids treating a random
 *    numeric-looking id as a merchant name, which would otherwise create a garbage "merchant".
 * 4. **Prefix/containment match** on a catalogue alias → [Confidence.HIGH]. Real narrations
 *    routinely wrap a known alias in noise the catalogue does not enumerate (branch city,
 *    a trailing store code): `SWIGGY BANGALORE 400001` contains the exact alias `SWIGGY` as
 *    a whole token. This is weaker than an exact hit only because the surrounding text is
 *    unverified, not because the core match is any less certain.
 * 5. **Fuzzy match** via [TextSimilarity.merchantSimilarity] — the last resort *within* the
 *    catalogue, for typos and reordered tokens a human would still recognise (`SWIGY`,
 *    `LTD SWIGGY BLR`). The 0.88/0.80 thresholds were chosen so that two catalogue entries
 *    that are genuinely similar (`Airtel` vs `Airtel Xstream`) don't accidentally collide at
 *    [Confidence.HIGH]; anything under 0.80 is rejected outright rather than guessed at,
 *    because a wrong high-confidence merchant is worse for the user than an honest "unknown".
 * 6. **Title-case cleanup fallback** → [Confidence.LOW]. The user should never see a raw
 *    `RANDOM*MERCHANT#93` string in their timeline just because nothing matched; a readable
 *    best-effort label beats an unreadable exact one, as long as it is honestly scored low.
 *
 * ## Pass-through handling
 *
 * Steps 2–5 can land on a payment processor ([CatalogEntry.isPassThrough]) instead of the
 * real merchant, because processors are the ones who actually submit the SMS-triggering
 * transaction (`RAZORPAY*SWIGGY`, `PHONEPE/UDAY STORES`). When that happens and the raw
 * string still has tokens left after removing the processor's alias, this resolver strips
 * those tokens and re-resolves the remainder — recursively, up to [MAX_PASS_THROUGH_DEPTH]
 * hops, in case two processors are chained. If the remainder resolves to nothing, the
 * pass-through resolution itself is returned rather than discarded, since "we saw a known
 * payment gateway" is still more informative than nothing.
 */
@Singleton
class DefaultMerchantResolver @Inject constructor(
    private val aliasLookup: AliasLookup,
) : MerchantResolver {

    /**
     * Every catalogue alias *and* canonical name, keyed by [Merchant.normalizeKey], built
     * once and reused for the lifetime of the resolver. [MerchantCatalogTest] guarantees the
     * catalogue itself has no duplicate normalized aliases; on the rare case a canonical name
     * collides with another entry's alias, the earlier entry in [MerchantCatalog.entries]
     * wins, which is deterministic and good enough for a collision that should never happen
     * in a curated catalogue.
     */
    private val catalogueIndex: Map<String, IndexedAlias> = buildIndex()

    override fun resolve(rawMerchant: String): MerchantResolution {
        val trimmed = rawMerchant.trim()
        if (trimmed.isEmpty()) return unresolved(trimmed)

        aliasLookup.findByNormalizedAlias(Merchant.normalizeKey(trimmed))?.let { return it }

        return resolveAgainstCatalogue(trimmed, depth = 0) ?: fallback(trimmed)
    }

    override fun learn(raw: String, canonicalName: String): LearnedAlias {
        val trimmed = raw.trim()
        return LearnedAlias(
            rawAlias = trimmed,
            normalizedAlias = Merchant.normalizeKey(trimmed),
            canonicalName = canonicalName,
        )
    }

    private fun resolveAgainstCatalogue(raw: String, depth: Int): MerchantResolution? {
        val direct = resolveExact(raw)
            ?: resolveVpa(raw)
            ?: resolvePrefixContainment(raw)
            ?: resolveFuzzy(raw)
            ?: return null

        if (!direct.isPassThrough || depth >= MAX_PASS_THROUGH_DEPTH) return direct

        val remainder = stripMatchedTokens(raw, direct.matchedAlias)
        if (remainder.isBlank() || remainder.equals(raw, ignoreCase = true)) return direct

        return resolveAgainstCatalogue(remainder, depth + 1) ?: direct
    }

    private fun resolveExact(raw: String): MerchantResolution? =
        catalogueIndex[Merchant.normalizeKey(raw)]?.toResolution(Confidence.CERTAIN)

    private fun resolveVpa(raw: String): MerchantResolution? {
        val at = raw.indexOf('@')
        if (at <= 0) return null
        val handle = raw.substring(0, at)
        if (handle.isEmpty() || !handle.all { it.isLetter() }) return null
        return resolveExact(handle) ?: resolvePrefixContainment(handle) ?: resolveFuzzy(handle)
    }

    private fun resolvePrefixContainment(raw: String): MerchantResolution? {
        val normalizedRaw = Merchant.normalizeKey(raw)
        if (normalizedRaw.isEmpty()) return null

        var best: IndexedAlias? = null
        var bestAliasLength = -1
        for ((normalizedAlias, indexed) in catalogueIndex) {
            if (normalizedAlias.length < MIN_CONTAINMENT_ALIAS_LENGTH) continue
            val isWholeTokenMatch = normalizedRaw == normalizedAlias ||
                normalizedRaw.startsWith("$normalizedAlias ") ||
                normalizedRaw.endsWith(" $normalizedAlias") ||
                normalizedRaw.contains(" $normalizedAlias ")
            if (isWholeTokenMatch && normalizedAlias.length > bestAliasLength) {
                best = indexed
                bestAliasLength = normalizedAlias.length
            }
        }
        return best?.toResolution(Confidence.HIGH)
    }

    private fun resolveFuzzy(raw: String): MerchantResolution? {
        var best: IndexedAlias? = null
        var bestScore = 0.0
        for (indexed in catalogueIndex.values) {
            val score = TextSimilarity.merchantSimilarity(raw, indexed.originalAlias)
            if (score > bestScore) {
                bestScore = score
                best = indexed
            }
        }
        val hit = best ?: return null
        val confidence = when {
            bestScore >= FUZZY_HIGH_THRESHOLD -> Confidence.HIGH
            bestScore >= FUZZY_MEDIUM_THRESHOLD -> Confidence.MEDIUM
            else -> return null
        }
        return hit.toResolution(confidence)
    }

    private fun fallback(raw: String): MerchantResolution = MerchantResolution(
        canonicalName = titleCase(raw),
        category = null,
        subcategory = null,
        confidence = Confidence.LOW,
        matchedAlias = null,
        isPassThrough = false,
        catalogEntry = null,
    )

    private fun unresolved(raw: String): MerchantResolution = MerchantResolution(
        canonicalName = raw,
        category = null,
        subcategory = null,
        confidence = Confidence.NONE,
        matchedAlias = null,
        isPassThrough = false,
        catalogEntry = null,
    )

    private fun buildIndex(): Map<String, IndexedAlias> {
        val index = LinkedHashMap<String, IndexedAlias>()
        for (entry in MerchantCatalog.entries) {
            index.putIfAbsent(Merchant.normalizeKey(entry.canonicalName), IndexedAlias(entry.canonicalName, entry))
            for (alias in entry.aliases) {
                index.putIfAbsent(Merchant.normalizeKey(alias), IndexedAlias(alias, entry))
            }
        }
        return index
    }

    private data class IndexedAlias(val originalAlias: String, val entry: CatalogEntry) {
        fun toResolution(confidence: Confidence) = MerchantResolution(
            canonicalName = entry.canonicalName,
            category = entry.category,
            subcategory = entry.subcategory,
            confidence = confidence,
            matchedAlias = originalAlias,
            isPassThrough = entry.isPassThrough,
            catalogEntry = entry,
        )
    }

    private companion object {
        /** Aliases shorter than this are excluded from prefix/containment matching (step 4)
         *  because short tokens (`hp`, `si`) collide with ordinary English far too often to
         *  be trustworthy evidence of a merchant when they are merely *contained* rather than
         *  matched exactly. */
        const val MIN_CONTAINMENT_ALIAS_LENGTH = 4

        const val FUZZY_HIGH_THRESHOLD = 0.88
        const val FUZZY_MEDIUM_THRESHOLD = 0.80

        /** Guards against pathological chains of processors referencing processors. */
        const val MAX_PASS_THROUGH_DEPTH = 3
    }
}

/** Strips every token belonging to [matchedAlias] out of [raw], leaving what (if anything) is left. */
private fun stripMatchedTokens(raw: String, matchedAlias: String?): String {
    if (matchedAlias.isNullOrBlank()) return raw
    val aliasTokens = matchedAlias.uppercase()
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .toSet()
    val rawTokens = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    return rawTokens.filter { it.uppercase() !in aliasTokens }.joinToString(" ")
}

/** Best-effort readable label for a raw merchant string nothing in the catalogue recognised. */
private fun titleCase(raw: String): String {
    val tokens = raw.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return raw.trim()
    return tokens.joinToString(" ") { token ->
        if (token.all { it.isDigit() }) token else token.lowercase().replaceFirstChar { it.uppercase() }
    }
}
