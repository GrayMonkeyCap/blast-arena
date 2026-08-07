package com.lifeledger.data.exporter

import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.data.repository.TransactionRepository
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes transactions as RFC 4180 CSV.
 *
 * The header names are chosen to be exactly what [com.lifeledger.data.importer.ColumnMapper]
 * recognises, so a file this class writes round-trips losslessly through
 * [com.lifeledger.data.importer.CsvImporter] — see `CsvRoundTripTest`. Quoting follows the same
 * hand-rolled rule the importer reads: a field is quoted when (and only when) it contains the
 * delimiter, a double quote, or a line break, and embedded quotes are doubled.
 */
@Singleton
class CsvExporter @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : DataExporter {

    override val format: ExportFormat = ExportFormat.CSV

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    override suspend fun export(request: ExportRequest, sink: OutputStream): ExportSummary {
        val counting = CountingOutputStream(sink)
        val writer = BufferedWriter(OutputStreamWriter(counting, StandardCharsets.UTF_8))
        var written = 0

        writer.write(HEADERS.joinToString(",") { csvField(it) })
        writer.write("\r\n")

        val query = TransactionQuery(range = request.range, sort = TransactionQuery.Sort.DATE_ASC)
        var offset = 0
        val pageSize = 500
        while (true) {
            val page = transactionRepository.page(query, pageSize, offset)
            if (page.isEmpty()) break
            for (transaction in page) {
                val row = listOf(
                    dateFormatter.format(transaction.occurredAt.atZone(ZoneId.systemDefault())),
                    transaction.description.orEmpty(),
                    transaction.merchantName ?: transaction.rawMerchant.orEmpty(),
                    transaction.category.name,
                    transaction.type.name,
                    // Signed minor-unit amount: negative for a debit, non-negative for a credit
                    // — the exact convention CsvImporter's single-AMOUNT-column path expects back.
                    (if (transaction.direction == com.lifeledger.core.model.Direction.DEBIT) {
                        -transaction.amount.abs().minor
                    } else {
                        transaction.amount.abs().minor
                    }).let { it / 100.0 }.let { String.format(Locale.ROOT, "%.2f", it) },
                    transaction.referenceNumber.orEmpty(),
                    transaction.balanceAfter?.let { String.format(Locale.ROOT, "%.2f", it.minor / 100.0) }.orEmpty(),
                )
                writer.write(row.joinToString(",") { csvField(it) })
                writer.write("\r\n")
                written++
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        writer.flush()
        return ExportSummary(itemCount = written, bytesWritten = counting.count)
    }

    private fun csvField(raw: String): String {
        val needsQuoting = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
    }

    companion object {
        private val HEADERS = listOf(
            "Date", "Description", "Merchant", "Category", "Type", "Amount", "Reference", "Balance",
        )
    }
}
