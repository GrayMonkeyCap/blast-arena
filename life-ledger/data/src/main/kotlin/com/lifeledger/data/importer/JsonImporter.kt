package com.lifeledger.data.importer

import com.lifeledger.core.model.Transaction
import com.lifeledger.data.exporter.ExportEnvelopeDto
import com.lifeledger.data.exporter.toDomain
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream

/**
 * Reads Life Ledger's own [ExportEnvelopeDto] format, and a permissive generic
 * array-of-objects form for JSON exported by other budgeting apps.
 *
 * Unlike [CsvImporter] and [XlsxImporter], this deliberately does not hand-roll a streaming
 * tokenizer: `kotlinx-serialization-json` is already a first-class dependency (it is how the
 * export side writes the format in the first place), and `Json.decodeFromStream` reads directly
 * off the [InputStream] without the caller ever holding the raw file as one giant [String] in
 * memory. Re-implementing a JSON parser by hand — the way the CSV and SMS-backup-XML importers
 * have to, because the JDK has no bundled structured reader for those formats — would only
 * reproduce, worse, what the approved dependency already does correctly. A full restore is
 * bounded by one device's own backup size (which is always smaller than that device's SMS
 * inbox, the case that actually forced [SmsBackupXmlImporter] to page with `XmlPullParser`), so
 * a whole-document parse is the appropriate trade-off here.
 */
@Singleton
class JsonImporter @Inject constructor() : DataImporter<Transaction> {

    override val format: ImportFormat = ImportFormat.JSON

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun canHandle(fileName: String, header: ByteArray): Boolean {
        if (fileName.substringAfterLast('.', "").lowercase() == "json") return true
        val text = String(header, StandardCharsets.UTF_8).trimStart('﻿').trim()
        return text.startsWith("{") || text.startsWith("[")
    }

    override suspend fun import(input: InputStream, options: ImportOptions): ImportResult<Transaction> {
        val root: JsonElement = json.decodeFromStream(JsonElement.serializer(), input)
        return when {
            root is JsonObject && root.containsKey("transactions") ->
                importEnvelope(json.decodeFromJsonElement(ExportEnvelopeDto.serializer(), root), options)
            root is JsonArray -> importGenericArray(root, options)
            root is JsonObject -> importGenericArray(JsonArray(listOf(root)), options)
            else -> ImportResult(emptyList(), 0, 0, 0, listOf("Unrecognised JSON shape"), emptyList())
        }
    }

    /** The fast, lossless path: an envelope this app (or a compatible version of it) wrote. */
    private fun importEnvelope(envelope: ExportEnvelopeDto, options: ImportOptions): ImportResult<Transaction> {
        val seen = HashSet<String>()
        val items = mutableListOf<Transaction>()
        val rowErrors = mutableListOf<RowError>()
        var skipped = 0

        envelope.transactions.forEachIndexed { index, dto ->
            try {
                val transaction = dto.toDomain()
                val key = "${transaction.occurredAt}|${transaction.amount.minor}|${transaction.referenceNumber}"
                if (options.skipDuplicates && !seen.add(key)) {
                    skipped++
                } else {
                    items += transaction
                }
            } catch (e: Exception) {
                rowErrors += RowError(index + 1, e.message ?: "Could not read transaction", dto.toString())
            }
        }
        val warnings = if (envelope.version > ExportEnvelopeDto.FORMAT_VERSION) {
            listOf("File was written by a newer export format (v${envelope.version}); some fields may be ignored")
        } else {
            emptyList()
        }
        return ImportResult(items, items.size, skipped, rowErrors.size, warnings, rowErrors)
    }

    /**
     * Permissive fallback for "just an array of transaction-shaped objects" JSON, as produced by
     * other finance apps' exports or a hand-written script. Field names are matched the same
     * fuzzy way [ColumnMapper] matches CSV headers, reusing it so the two "I don't know this
     * exporter's exact schema" paths behave consistently.
     */
    private fun importGenericArray(array: JsonArray, options: ImportOptions): ImportResult<Transaction> {
        val items = mutableListOf<Transaction>()
        val rowErrors = mutableListOf<RowError>()
        var skipped = 0
        val seen = HashSet<String>()

        array.forEachIndexed { index, element ->
            val obj = element as? JsonObject
            if (obj == null) {
                rowErrors += RowError(index + 1, "Array element is not an object", element.toString())
                return@forEachIndexed
            }
            try {
                val keys = obj.keys.toList()
                val mapping = ColumnMapper.map(keys, options.columnMapping)
                val cells = keys.map { key -> obj[key]?.let(::stringify) ?: "" }
                val dateParser = FlexibleDateParser(options.dateFormat)
                val transaction = RowMapper.toTransaction(cells, mapping, options, dateParser)
                val key = "${transaction.occurredAt}|${transaction.amount.minor}|${transaction.description}"
                if (options.skipDuplicates && !seen.add(key)) {
                    skipped++
                } else {
                    items += transaction
                }
            } catch (e: RowMapper.RowMappingException) {
                rowErrors += RowError(index + 1, e.message ?: "Unparseable object", obj.toString())
            }
        }
        return ImportResult(items, items.size, skipped, rowErrors.size, emptyList(), rowErrors)
    }

    private fun stringify(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        JsonNull -> ""
        else -> element.toString()
    }
}
