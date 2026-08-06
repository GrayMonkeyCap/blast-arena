package com.lifeledger.data.importer

/**
 * Fuzzy header-to-field mapping shared by [CsvImporter] and [XlsxImporter].
 *
 * Bank statement exports use wildly inconsistent header spellings — the same "money that left
 * the account" column shows up as `Withdrawal Amt.`, `Debit`, `Dr Amount` or just `Amount` with
 * a sign — so both tabular importers need the exact same "guess what this column means" logic.
 * Pulling it out here means a fix to one bank's header quirk benefits both formats and keeps the
 * unit tests for the mapping itself independent of either file format.
 */
object ColumnMapper {

    /** The transaction-shaped fields a spreadsheet column can be mapped to. */
    enum class CanonicalField {
        DATE,
        VALUE_DATE,
        DESCRIPTION,
        MERCHANT,
        CATEGORY,
        TYPE,
        ACCOUNT,
        REFERENCE,
        AMOUNT,
        DEBIT,
        CREDIT,
        BALANCE,
    }

    // Every alias is stored normalized (lowercase, alphanumeric-only, single-spaced) so that
    // "Chq./Ref.No.", "Chq Ref No" and "chq-ref-no" all collapse to the same lookup key.
    private val synonyms: Map<CanonicalField, List<String>> = mapOf(
        CanonicalField.DATE to listOf(
            "date", "txn date", "transaction date", "posting date", "date time",
            "transaction dt", "tran date", "txndate",
        ),
        CanonicalField.VALUE_DATE to listOf(
            "value dt", "value date", "valuedate",
        ),
        CanonicalField.DESCRIPTION to listOf(
            "narration", "description", "details", "particulars", "transaction remarks",
            "remarks", "transaction details", "desc",
        ),
        CanonicalField.MERCHANT to listOf(
            "merchant", "payee", "beneficiary", "paid to", "vendor",
        ),
        CanonicalField.CATEGORY to listOf(
            "category", "spend category", "txn category",
        ),
        CanonicalField.TYPE to listOf(
            "type", "transaction type", "dr cr", "cr dr", "drcr", "txn type",
        ),
        CanonicalField.ACCOUNT to listOf(
            "account", "account no", "account number", "account no.",
        ),
        CanonicalField.REFERENCE to listOf(
            "chq ref no", "reference", "ref no", "cheque no", "utr", "txn ref no",
            "chq no", "reference number", "cheque number",
        ),
        CanonicalField.AMOUNT to listOf(
            "amount", "amt", "transaction amount", "txn amount", "value",
        ),
        CanonicalField.DEBIT to listOf(
            "withdrawal amt", "debit", "withdrawal", "dr amount", "debit amount", "withdrawal amt.",
            "amount debited", "debit amt",
        ),
        CanonicalField.CREDIT to listOf(
            "deposit amt", "credit", "deposit", "cr amount", "credit amount", "deposit amt.",
            "amount credited", "credit amt",
        ),
        CanonicalField.BALANCE to listOf(
            "closing balance", "balance", "available balance", "balance amt", "running balance",
            "balance amount",
        ),
    )

    /**
     * Resolves each header string to the [CanonicalField] it most likely represents.
     *
     * [overrides] (keyed by [CanonicalField.name]) win outright — they come from
     * [ImportOptions.columnMapping], i.e. the user pointed at a specific column explicitly.
     * Everything else is scored by exact match first, then longest-synonym-substring match, so
     * `"Cheque No"` maps to REFERENCE even though the column is also technically "a number".
     *
     * A header never claims two fields and a field never claims two headers: once a column or a
     * field is assigned, both are removed from further consideration.
     */
    fun map(headers: List<String>, overrides: Map<String, String> = emptyMap()): Map<CanonicalField, Int> {
        val result = mutableMapOf<CanonicalField, Int>()
        val claimedColumns = mutableSetOf<Int>()
        val normalizedHeaders = headers.map { normalize(it) }

        for ((fieldName, headerText) in overrides) {
            val field = CanonicalField.entries.firstOrNull { it.name.equals(fieldName, ignoreCase = true) }
                ?: continue
            val target = normalize(headerText)
            val idx = normalizedHeaders.indexOfFirst { it == target }
            if (idx >= 0) {
                result[field] = idx
                claimedColumns += idx
            }
        }

        for (field in CanonicalField.entries) {
            if (result.containsKey(field)) continue
            val syns = synonyms[field] ?: continue

            var bestIdx = -1
            var bestScore = -1
            normalizedHeaders.forEachIndexed { idx, header ->
                if (idx in claimedColumns || header.isEmpty()) return@forEachIndexed
                for (syn in syns) {
                    val score = when {
                        header == syn -> 1000 + syn.length
                        header.contains(syn) -> syn.length
                        else -> continue
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestIdx = idx
                    }
                }
            }
            if (bestIdx >= 0) {
                result[field] = bestIdx
                claimedColumns += bestIdx
            }
        }
        return result
    }

    /** Lowercase, punctuation-stripped, single-spaced — the same normalization on both sides. */
    fun normalize(header: String): String = header
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
}
