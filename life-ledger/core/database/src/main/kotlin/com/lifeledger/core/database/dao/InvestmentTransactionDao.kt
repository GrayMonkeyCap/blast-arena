package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.InvestmentTransactionEntity
import com.lifeledger.core.database.projection.PeriodTotalRow
import kotlinx.coroutines.flow.Flow

/** Individual contributions and redemptions under a holding. */
@Dao
interface InvestmentTransactionDao {

    @Insert
    suspend fun insert(entry: InvestmentTransactionEntity): Long

    @Upsert
    suspend fun upsert(entry: InvestmentTransactionEntity)

    @Delete
    suspend fun delete(entry: InvestmentTransactionEntity)

    @Query("SELECT * FROM investment_transactions WHERE investmentId = :investmentId ORDER BY occurredAt DESC")
    fun observeForInvestment(investmentId: Long): Flow<List<InvestmentTransactionEntity>>

    @Query("SELECT * FROM investment_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun findByTransactionId(transactionId: Long): InvestmentTransactionEntity?

    @Query(
        """
        SELECT * FROM investment_transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        ORDER BY occurredAt DESC
        """,
    )
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<InvestmentTransactionEntity>>

    /**
     * Monthly contribution curve.
     *
     * Buys and SIP instalments count as money in; sells, dividends and maturities are money
     * coming back out, and are reported as income so the chart shows a genuine two-sided flow
     * rather than a monotonically rising line.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS periodKey,
               COALESCE(SUM(CASE WHEN kind IN ('SELL', 'DIVIDEND', 'INTEREST', 'MATURITY') THEN amountMinor ELSE 0 END), 0) AS incomeMinor,
               0 AS expenseMinor,
               COALESCE(SUM(CASE WHEN kind IN ('BUY', 'SIP_INSTALMENT') THEN amountMinor ELSE 0 END), 0) AS investedMinor,
               COUNT(*) AS count
        FROM investment_transactions
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY periodKey
        ORDER BY periodKey ASC
        """,
    )
    fun monthlyTotals(fromMillis: Long, toMillis: Long): Flow<List<PeriodTotalRow>>

    @Query("SELECT COUNT(*) FROM investment_transactions WHERE investmentId = :investmentId")
    suspend fun countForInvestment(investmentId: Long): Int
}
