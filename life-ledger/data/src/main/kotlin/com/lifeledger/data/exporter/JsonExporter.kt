package com.lifeledger.data.exporter

import com.lifeledger.core.model.TransactionQuery
import com.lifeledger.data.repository.AccountRepository
import com.lifeledger.data.repository.BillRepository
import com.lifeledger.data.repository.InvestmentRepository
import com.lifeledger.data.repository.MerchantRepository
import com.lifeledger.data.repository.RuleRepository
import com.lifeledger.data.repository.SubscriptionRepository
import com.lifeledger.data.repository.TagRepository
import com.lifeledger.data.repository.TransactionRepository
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writes Life Ledger's canonical, versioned export envelope (see [ExportEnvelopeDto]).
 *
 * Arrays are streamed element-by-element with `kotlinx.serialization` encoding one DTO at a
 * time, rather than building a `List<TransactionDto>` and calling `Json.encodeToString` on the
 * whole envelope: a device with years of history can have a transaction table well into the
 * hundreds of thousands of rows, and materialising all of them as DTOs simultaneously is
 * exactly the kind of avoidable spike a background export should not cause.
 *
 * `counts` is written as the *last* key rather than where the illustrative envelope sketch put
 * it first: JSON object key order carries no meaning (and [com.lifeledger.data.importer.JsonImporter]
 * reads fields by name, not position), and putting it last is what lets the transaction count be
 * derived from the same single streaming pass that writes the array, instead of a separate
 * counting pass or buffering the whole array just to learn its size up front.
 */
@Singleton
class JsonExporter @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val billRepository: BillRepository,
    private val investmentRepository: InvestmentRepository,
    private val tagRepository: TagRepository,
    private val ruleRepository: RuleRepository,
) : DataExporter {

    override val format: ExportFormat = ExportFormat.JSON

    private val json = Json { encodeDefaults = true; prettyPrint = false }

    override suspend fun export(request: ExportRequest, sink: OutputStream): ExportSummary {
        val counting = CountingOutputStream(sink)
        val writer = BufferedWriter(OutputStreamWriter(counting, StandardCharsets.UTF_8))

        writer.write("{")
        writer.write("\"format\":\"${ExportEnvelopeDto.FORMAT_ID}\",")
        writer.write("\"version\":${ExportEnvelopeDto.FORMAT_VERSION},")
        writer.write("\"exportedAt\":\"${Instant.now()}\",")

        val transactionCount = writeTransactions(writer, request.range)
        writer.write(",")
        val merchantCount = writeArray(writer, "merchants", request.includeMerchants) {
            merchantRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val accountCount = writeArray(writer, "accounts", request.includeAccounts) {
            accountRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val subscriptionCount = writeArray(writer, "subscriptions", request.includeSubscriptions) {
            subscriptionRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val billCount = writeArray(writer, "bills", request.includeBills) {
            billRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val investmentCount = writeArray(writer, "investments", request.includeInvestments) {
            investmentRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val tagCount = writeArray(writer, "tags", request.includeTags) {
            tagRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")
        val ruleCount = writeArray(writer, "rules", request.includeRules) {
            ruleRepository.observeAll().first().map { it.toDto() }
        }
        writer.write(",")

        val counts = ExportCountsDto(
            transactions = transactionCount,
            merchants = merchantCount,
            accounts = accountCount,
            subscriptions = subscriptionCount,
            bills = billCount,
            investments = investmentCount,
            tags = tagCount,
            rules = ruleCount,
        )
        writer.write("\"counts\":")
        writer.write(json.encodeToString(counts))
        writer.write("}")
        writer.flush()

        val total = transactionCount + merchantCount + accountCount + subscriptionCount +
            billCount + investmentCount + tagCount + ruleCount
        return ExportSummary(itemCount = total, bytesWritten = counting.count)
    }

    /** Pages through every matching transaction, writing (and counting) one DTO at a time. */
    private suspend fun writeTransactions(writer: BufferedWriter, range: com.lifeledger.core.model.DateRange?): Int {
        writer.write("\"transactions\":[")
        val query = TransactionQuery(range = range, sort = TransactionQuery.Sort.DATE_ASC)
        var count = 0
        var offset = 0
        val pageSize = 500
        while (true) {
            val page = transactionRepository.page(query, pageSize, offset)
            if (page.isEmpty()) break
            for (transaction in page) {
                if (count > 0) writer.write(",")
                writer.write(json.encodeToString(transaction.toDto()))
                count++
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        writer.write("]")
        return count
    }

    /**
     * Catalogue collections (merchants, accounts, subscriptions, bills, investments, tags,
     * rules) are, in practice, orders of magnitude smaller than the transaction table — they are
     * one row per distinct entity rather than per event — so fetching each one as a plain list
     * and encoding its elements in a loop is both simple and never a real memory concern.
     */
    private suspend inline fun <reified T> writeArray(
        writer: BufferedWriter,
        key: String,
        include: Boolean,
        fetch: suspend () -> List<T>,
    ): Int {
        writer.write("\"$key\":[")
        if (!include) {
            writer.write("]")
            return 0
        }
        val items = fetch()
        items.forEachIndexed { index, item ->
            if (index > 0) writer.write(",")
            writer.write(json.encodeToString(item))
        }
        writer.write("]")
        return items.size
    }
}
