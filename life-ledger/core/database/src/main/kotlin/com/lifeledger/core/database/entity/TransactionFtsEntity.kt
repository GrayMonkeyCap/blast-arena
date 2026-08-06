package com.lifeledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * The full-text index over [TransactionEntity].
 *
 * Declaring `transactions` as the content entity means the index stores no copy of the text —
 * it points back into the base table — and Room emits the FTS content-sync triggers, so the
 * index cannot drift from the rows it describes. That trigger generation is the whole reason
 * for the external-content form: hand-maintained FTS mirrors go stale the first time someone
 * adds an UPDATE path and forgets the index.
 *
 * [rowId] is the FTS table's implicit `rowid`, which for an external-content table equals the
 * content row's `rowid` — i.e. `transactions.id`. That is what search queries join on.
 */
@Entity(tableName = "transactions_fts")
@Fts4(contentEntity = TransactionEntity::class)
data class TransactionFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    val searchBlob: String,
    /** Indexed separately from [searchBlob] so a query can be scoped to the merchant column. */
    val merchantName: String? = null,
)
