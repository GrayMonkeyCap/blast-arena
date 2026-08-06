package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-parser bookkeeping, keyed by the parser's own id.
 *
 * The id is the primary key rather than a surrogate because parsers are code, not data: their
 * identity is fixed at build time, and using it directly makes "enable/disable this parser"
 * a single idempotent upsert with no lookup.
 *
 * [successCount] and [failureCount] are what Settings › Parser Management ranks by, and what
 * makes a regression in a parser visible after a release.
 */
@Entity(tableName = "parser_state")
data class ParserStateEntity(
    @PrimaryKey
    val parserId: String,
    val enabled: Boolean = true,
    val version: Int = 1,
    val lastRunAt: Long? = null,
    val successCount: Long = 0,
    val failureCount: Long = 0,
)
