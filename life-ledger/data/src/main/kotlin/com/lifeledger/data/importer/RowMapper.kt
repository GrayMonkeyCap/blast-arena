package com.lifeledger.data.importer

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SourceKind
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import java.time.ZoneId

/**
 * Turns one already-tokenised statement row into a [Transaction].
 *
 * Shared by [CsvImporter] and [XlsxImporter] so a fix to, say, the debit/credit-column sign
 * convention benefits both formats identically. Callers always pass plain [String] cells, even
 * for XLSX: [XlsxImporter] resolves shared strings and Excel serial dates to text *before*
 * handing rows here, which keeps this mapper — and its unit tests — file-format agnostic.
 */
internal object RowMapper {

    /** A row this mapper could not turn into a transaction; callers convert it to a [RowError]. */
    class RowMappingException(message: String) : Exception(message)

    fun toTransaction(
        cells: List<String>,
        mapping: Map<ColumnMapper.CanonicalField, Int>,
        options: ImportOptions,
        dateParser: FlexibleDateParser,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Transaction {
        fun cell(field: ColumnMapper.CanonicalField): String? =
            mapping[field]?.let { idx -> cells.getOrNull(idx)?.trim()?.ifEmpty { null } }

        val dateText = cell(ColumnMapper.CanonicalField.DATE)
            ?: cell(ColumnMapper.CanonicalField.VALUE_DATE)
            ?: throw RowMappingException("No date column found, or the date cell is blank")
        val occurredAt = dateParser.parse(dateText)?.atZone(zone)?.toInstant()
            ?: throw RowMappingException("Could not parse date '$dateText'")

        val amountText = cell(ColumnMapper.CanonicalField.AMOUNT)
        val debitText = cell(ColumnMapper.CanonicalField.DEBIT)
        val creditText = cell(ColumnMapper.CanonicalField.CREDIT)

        val (amount, direction) = resolveAmount(amountText, debitText, creditText, options.currency)
            ?: throw RowMappingException("No usable amount (checked AMOUNT/DEBIT/CREDIT columns)")

        val type = inferType(cell(ColumnMapper.CanonicalField.TYPE), direction)
        val description = cell(ColumnMapper.CanonicalField.DESCRIPTION)
        val merchant = cell(ColumnMapper.CanonicalField.MERCHANT) ?: description
        val category = TxnCategory.fromNameOrNull(cell(ColumnMapper.CanonicalField.CATEGORY))
            ?: TxnCategory.UNCATEGORIZED
        val balance = cell(ColumnMapper.CanonicalField.BALANCE)?.let { Money.parse(it, options.currency) }
        val reference = cell(ColumnMapper.CanonicalField.REFERENCE)

        return Transaction(
            amount = amount,
            type = type,
            direction = direction,
            category = category,
            occurredAt = occurredAt,
            merchantName = merchant,
            rawMerchant = merchant,
            balanceAfter = balance,
            referenceNumber = reference,
            description = description,
            source = SourceKind.IMPORT,
            paymentMethod = PaymentMethod.UNKNOWN,
        )
    }

    /**
     * Best-effort direction + amount resolution across the two statement layouts every bank
     * uses: one signed AMOUNT column, or a DEBIT/CREDIT (withdrawal/deposit) pair where exactly
     * one side is non-zero per row. DEBIT/CREDIT is preferred when both are present because it
     * is unambiguous; a signed AMOUNT column relies on the sign meaning what we assume it means.
     */
    private fun resolveAmount(
        amountText: String?,
        debitText: String?,
        creditText: String?,
        currency: String,
    ): Pair<Money, Direction>? {
        val debit = debitText?.let { Money.parse(it, currency) }?.takeIf { !it.isZero }
        val credit = creditText?.let { Money.parse(it, currency) }?.takeIf { !it.isZero }
        if (debit != null || credit != null) {
            return if (credit != null) credit.abs() to Direction.CREDIT else debit!!.abs() to Direction.DEBIT
        }
        val signed = amountText?.let { Money.parse(it, currency) } ?: return null
        return if (signed.isNegative) signed.abs() to Direction.DEBIT else signed to Direction.CREDIT
    }

    /**
     * A generic tabular import cannot recover the semantic [TransactionType] a bank SMS parser
     * would — salary, refund and a plain transfer all look identical as a spreadsheet row — so
     * this deliberately collapses to the two coarse buckets that are always correct: money that
     * arrived is [TransactionType.TRANSFER_IN], money that left is [TransactionType.PURCHASE].
     * The user (or a [com.lifeledger.core.model.UserRule]) can recategorise afterwards; the
     * category column, when present, is still honoured via [TxnCategory].
     */
    private fun inferType(typeText: String?, direction: Direction): TransactionType {
        val normalized = typeText?.lowercase()?.trim()
        return when {
            normalized == null -> defaultType(direction)
            normalized.startsWith("cr") || normalized.contains("credit") -> TransactionType.TRANSFER_IN
            normalized.startsWith("dr") || normalized.contains("debit") -> TransactionType.PURCHASE
            else -> defaultType(direction)
        }
    }

    private fun defaultType(direction: Direction): TransactionType =
        if (direction == Direction.CREDIT) TransactionType.TRANSFER_IN else TransactionType.PURCHASE
}
