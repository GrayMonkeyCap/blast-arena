package com.lifeledger.data.exporter

import com.lifeledger.core.model.DateRange
import java.io.OutputStream

/** The seam every export format is written against, mirroring [com.lifeledger.data.importer.DataImporter]. */
interface DataExporter {
    val format: ExportFormat
    suspend fun export(request: ExportRequest, sink: OutputStream): ExportSummary
}

enum class ExportFormat { CSV, JSON, ENCRYPTED_BACKUP, PDF_SUMMARY }

/**
 * What to export and how much of it. Every exporter reads only the fields it needs — a CSV of
 * transactions ignores [includeMerchants], a PDF summary ignores [passphrase] — so one request
 * shape can drive [com.lifeledger.data.exporter.ExportCoordinator] regardless of [format].
 */
data class ExportRequest(
    val format: ExportFormat,
    /** `null` means "everything", which is what a full backup wants. */
    val range: DateRange? = null,
    val includeMerchants: Boolean = true,
    val includeAccounts: Boolean = true,
    val includeSubscriptions: Boolean = true,
    val includeBills: Boolean = true,
    val includeInvestments: Boolean = true,
    val includeTags: Boolean = true,
    val includeRules: Boolean = true,
    /**
     * Required for [ExportFormat.ENCRYPTED_BACKUP]. Held as a [CharArray] rather than [String]
     * so the caller can zero it immediately after [DataExporter.export] returns — see
     * [com.lifeledger.data.exporter.EncryptedBackupExporter] for why a passphrase is never
     * stored anywhere, including here.
     */
    val passphrase: CharArray? = null,
    /** [ExportFormat.PDF_SUMMARY] only: whether to include the per-transaction appendix pages. */
    val includeTransactionAppendix: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportRequest) return false
        return format == other.format && range == other.range &&
            includeMerchants == other.includeMerchants && includeAccounts == other.includeAccounts &&
            includeSubscriptions == other.includeSubscriptions && includeBills == other.includeBills &&
            includeInvestments == other.includeInvestments && includeTags == other.includeTags &&
            includeRules == other.includeRules && passphrase.contentEquals(other.passphrase) &&
            includeTransactionAppendix == other.includeTransactionAppendix
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + (range?.hashCode() ?: 0)
        result = 31 * result + includeMerchants.hashCode()
        result = 31 * result + includeAccounts.hashCode()
        result = 31 * result + includeSubscriptions.hashCode()
        result = 31 * result + includeBills.hashCode()
        result = 31 * result + includeInvestments.hashCode()
        result = 31 * result + includeTags.hashCode()
        result = 31 * result + includeRules.hashCode()
        result = 31 * result + (passphrase?.contentHashCode() ?: 0)
        result = 31 * result + includeTransactionAppendix.hashCode()
        return result
    }
}

data class ExportSummary(
    val itemCount: Int,
    val bytesWritten: Long,
    val warnings: List<String> = emptyList(),
)
