package com.lifeledger.data.importer

import com.lifeledger.core.model.Money
import java.io.InputStream

/**
 * The seam every import format is written against.
 *
 * `T` is deliberately generic rather than fixed to [com.lifeledger.core.model.Transaction]:
 * [com.lifeledger.data.importer.SmsBackupXmlImporter] produces [com.lifeledger.core.model.SmsRecord]
 * (raw messages, to be replayed through the parser pipeline like any other inbox message) while
 * the statement-shaped formats ([CsvImporter], [XlsxImporter]) produce transactions directly.
 */
interface DataImporter<T> {
    val format: ImportFormat

    /**
     * Cheap, non-destructive sniff used by [ImportCoordinator] to pick an importer.
     *
     * [header] is a small prefix of the file (a few KB is enough) so callers never have to
     * read a whole multi-megabyte export just to find out what it is.
     */
    fun canHandle(fileName: String, header: ByteArray): Boolean

    /**
     * Streams [input] into [T]s. Implementations must not buffer the whole file in memory —
     * on-device imports routinely cover years of bank statements or a decade of SMS backups.
     */
    suspend fun import(input: InputStream, options: ImportOptions): ImportResult<T>
}

/**
 * Tuning knobs shared by every importer. Not every field applies to every format — e.g.
 * [dateFormat] is meaningless for [JsonImporter], which carries its own ISO timestamps — and
 * implementations that don't need a field simply ignore it.
 */
data class ImportOptions(
    /**
     * An explicit [java.time.format.DateTimeFormatter] pattern to parse dates with. When
     * `null`, [CsvImporter] and [XlsxImporter] auto-detect from a fixed list of formats
     * commonly seen in Indian bank exports, locking onto the first one that parses the whole
     * column successfully.
     */
    val dateFormat: String? = null,
    val currency: String = Money.INR,
    /** Rows/messages that collide with an existing fingerprint or reference are dropped. */
    val skipDuplicates: Boolean = true,
    /** When true, nothing is persisted — the caller only wants the parsed preview + errors. */
    val dryRun: Boolean = false,
    /**
     * Explicit header overrides: canonical field name (see [ColumnMapper.CanonicalField], e.g.
     * `"AMOUNT"`, `"MERCHANT"`) to the literal column header text to use for it. Anything not
     * present here falls back to [ColumnMapper]'s fuzzy matching.
     */
    val columnMapping: Map<String, String> = emptyMap(),
)

/**
 * The outcome of one whole-file import.
 *
 * Counts are kept separate from [items] because a `dryRun` still wants accurate counts without
 * the caller having to re-derive them from [rowErrors]. `skipped` covers rows the importer
 * recognised as valid but deliberately did not emit (duplicates, blank lines); `failed` covers
 * rows that could not be interpreted at all and are mirrored into [rowErrors].
 */
data class ImportResult<T>(
    val items: List<T>,
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val warnings: List<String>,
    val rowErrors: List<RowError>,
)

/** One row/record that could not be turned into a domain object. Never thrown — always collected. */
data class RowError(
    val rowNumber: Int,
    val message: String,
    val rawRow: String,
)

enum class ImportFormat { CSV, XLSX, JSON, SMS_BACKUP_XML }
