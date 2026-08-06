package com.lifeledger.data.importer

import com.lifeledger.core.model.Transaction
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * A hand-rolled RFC 4180 CSV reader and bank-statement-to-[Transaction] importer.
 *
 * Written by hand rather than pulled from a library on purpose: Life Ledger ships no
 * third-party dependencies (see the module-level product constraint), and a correct CSV reader
 * is genuinely small — the whole spec is "fields are delimiter-separated, a field can be
 * quoted, a quoted field can contain the delimiter/newlines/literal quotes (escaped as `\"\"`),
 * and line endings can be CRLF or LF". [readRecord] implements exactly that as a
 * character-at-a-time state machine, which also means it never holds more than one record in
 * memory at a time — a hard requirement for multi-year, multi-megabyte bank exports.
 */
@Singleton
class CsvImporter @Inject constructor() : DataImporter<Transaction> {

    override val format: ImportFormat = ImportFormat.CSV

    override fun canHandle(fileName: String, header: ByteArray): Boolean {
        if (fileName.substringAfterLast('.', "").lowercase() in setOf("csv", "txt")) return true
        // Content sniff for extension-less files: not a ZIP (xlsx/llbk), not JSON, not XML.
        val isZip = header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        val text = String(header, StandardCharsets.UTF_8).trimStart('﻿').trimStart()
        val looksJson = text.startsWith("{") || text.startsWith("[")
        val looksXml = text.startsWith("<")
        return !isZip && !looksJson && !looksXml
    }

    override suspend fun import(input: InputStream, options: ImportOptions): ImportResult<Transaction> =
        withContext(Dispatchers.IO) {
            val pushback = PushbackReader(InputStreamReader(input, StandardCharsets.UTF_8), 2)
            stripBom(pushback)

            val header = readNonBlankRecordSniffingDelimiter(pushback)
                ?: return@withContext ImportResult(emptyList(), 0, 0, 0, listOf("File is empty"), emptyList())
            val (headers, delimiter) = header

            val mapping = ColumnMapper.map(headers, options.columnMapping)
            val warnings = mutableListOf<String>()
            if (ColumnMapper.CanonicalField.DATE !in mapping && ColumnMapper.CanonicalField.VALUE_DATE !in mapping) {
                warnings += "No date column recognised; every row will fail"
            }
            if (ColumnMapper.CanonicalField.AMOUNT !in mapping &&
                ColumnMapper.CanonicalField.DEBIT !in mapping &&
                ColumnMapper.CanonicalField.CREDIT !in mapping
            ) {
                warnings += "No amount column recognised; every row will fail"
            }

            val items = mutableListOf<Transaction>()
            val rowErrors = mutableListOf<RowError>()
            var imported = 0
            var skipped = 0
            var failed = 0

            val dateParser = FlexibleDateParser(options.dateFormat)
            val seenDedupeKeys = HashSet<String>()
            var rowNumber = 1 // the header is row 1, matching what a spreadsheet app would show

            var record = readRecord(pushback, delimiter)
            while (record != null) {
                coroutineContext.ensureActive()
                rowNumber++
                val isBlank = record.size == 1 && record[0].isBlank()
                if (!isBlank) {
                    try {
                        val transaction = RowMapper.toTransaction(record, mapping, options, dateParser)
                        val dedupeKey = "${transaction.occurredAt}|${transaction.amount.minor}|${transaction.description}"
                        if (options.skipDuplicates && !seenDedupeKeys.add(dedupeKey)) {
                            skipped++
                        } else {
                            items += transaction
                            imported++
                        }
                    } catch (e: RowMapper.RowMappingException) {
                        failed++
                        rowErrors += RowError(rowNumber, e.message ?: "Unparseable row", record.joinToString(","))
                    }
                }
                record = readRecord(pushback, delimiter)
            }

            ImportResult(items, imported, skipped, failed, warnings, rowErrors)
        }

    /** Skips fully blank leading lines (some exports open with a couple of empty rows). */
    private fun readNonBlankRecordSniffingDelimiter(pushback: PushbackReader): Pair<List<String>, Char>? {
        while (true) {
            val peeked = peekLine(pushback) ?: return null
            val delimiter = sniffDelimiter(peeked)
            val record = readRecord(pushback, delimiter) ?: return null
            if (record.size == 1 && record[0].isBlank()) continue
            return record to delimiter
        }
    }

    /** Peeks one physical line (up to the next `\n`/`\r`) without consuming it from the stream. */
    private fun peekLine(pushback: PushbackReader): String? {
        val buffer = StringBuilder()
        val readChars = mutableListOf<Int>()
        var c = pushback.read()
        while (c != -1 && c.toChar() != '\n' && c.toChar() != '\r') {
            readChars += c
            buffer.append(c.toChar())
            c = pushback.read()
        }
        if (c != -1) readChars += c
        for (i in readChars.indices.reversed()) pushback.unread(readChars[i])
        return if (buffer.isEmpty() && c == -1) null else buffer.toString()
    }

    /** Picks whichever of `,` `;` `\t` appears most often in the header line. Defaults to `,`. */
    private fun sniffDelimiter(headerLine: String): Char {
        val candidates = charArrayOf(',', ';', '\t')
        val best = candidates.maxByOrNull { d -> headerLine.count { it == d } } ?: ','
        return if (headerLine.count { it == best } > 0) best else ','
    }

    private fun stripBom(pushback: PushbackReader) {
        val first = pushback.read()
        if (first != -1 && first.toChar() != '﻿') pushback.unread(first)
    }

    /**
     * Reads one CSV record as a character-at-a-time state machine: RFC 4180 quoting, `""`
     * escapes, embedded delimiters/newlines inside quotes, and CRLF-or-LF line endings. Returns
     * `null` at end of stream. A field is only treated as quoted when the opening `"` is the
     * very first character of the field — a stray `"` later in an unquoted field is kept
     * literally, matching how spreadsheet apps themselves tolerate slightly malformed exports.
     */
    private fun readRecord(pushback: PushbackReader, delimiter: Char): List<String>? {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var sawAnyChar = false

        while (true) {
            val ci = pushback.read()
            if (ci == -1) {
                if (!sawAnyChar && fields.isEmpty()) return null
                fields += field.toString()
                return fields
            }
            sawAnyChar = true
            val c = ci.toChar()

            if (inQuotes) {
                if (c == '"') {
                    val next = pushback.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        if (next != -1) pushback.unread(next)
                    }
                } else {
                    field.append(c)
                }
                continue
            }

            when {
                c == '"' && field.isEmpty() -> inQuotes = true
                c == delimiter -> {
                    fields += field.toString()
                    field.setLength(0)
                }
                c == '\r' -> {
                    val next = pushback.read()
                    if (next != -1 && next.toChar() != '\n') pushback.unread(next)
                    fields += field.toString()
                    return fields
                }
                c == '\n' -> {
                    fields += field.toString()
                    return fields
                }
                else -> field.append(c)
            }
        }
    }
}
