package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A locally computed observation about the user's life.
 *
 * Every insight is produced by a deterministic generator over local data; there is no
 * model call and no network. [evidence] carries the numbers behind the sentence so the UI
 * can show the working rather than asking the user to trust a claim.
 */
data class Insight(
    val id: Long = 0,
    val kind: InsightKind,
    val severity: InsightSeverity = InsightSeverity.INFO,
    val title: String,
    val body: String,
    val generatedAt: Instant,
    /** Window the insight describes. */
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val category: TxnCategory? = null,
    val merchantId: Long? = null,
    val amount: Money? = null,
    val changePercent: Double? = null,
    /** Free-form key/value numbers backing the statement, shown in the detail sheet. */
    val evidence: Map<String, String> = emptyMap(),
    /** Ids of transactions the user can tap through to. */
    val relatedTransactionIds: List<Long> = emptyList(),
    val isDismissed: Boolean = false,
    val isPinned: Boolean = false,
    /** Stable key so the same finding is refreshed rather than duplicated on each run. */
    val dedupeKey: String,
)
