package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.InvestmentEntity
import com.lifeledger.core.model.InstrumentType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Holdings, tracked as a contribution ledger. */
@Dao
interface InvestmentDao {

    @Upsert
    suspend fun upsert(investment: InvestmentEntity): Long

    @Delete
    suspend fun delete(investment: InvestmentEntity)

    @Query("SELECT * FROM investments WHERE id = :id")
    suspend fun findById(id: Long): InvestmentEntity?

    @Query("SELECT * FROM investments WHERE id = :id")
    fun observeById(id: Long): Flow<InvestmentEntity?>

    /**
     * Identity for the detector. Folio is compared with `IS` so that two holdings whose folio
     * the parser could not read are still treated as the same holding when name and instrument
     * match — otherwise every SIP debit would mint a new investment.
     */
    @Query(
        """
        SELECT * FROM investments
        WHERE name = :name AND instrumentType = :instrumentType AND folioOrAccount IS :folioOrAccount
        LIMIT 1
        """,
    )
    suspend fun findByIdentity(
        name: String,
        instrumentType: InstrumentType,
        folioOrAccount: String?,
    ): InvestmentEntity?

    @Query("SELECT * FROM investments WHERE isActive = 1 ORDER BY totalInvestedMinor DESC")
    fun observeActive(): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investments ORDER BY isActive DESC, totalInvestedMinor DESC")
    fun observeAll(): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investments WHERE instrumentType = :instrumentType ORDER BY name ASC")
    fun observeByInstrument(instrumentType: InstrumentType): Flow<List<InvestmentEntity>>

    @Query("SELECT * FROM investments WHERE isSip = 1 AND isActive = 1 ORDER BY nextExpectedAt ASC")
    fun observeActiveSips(): Flow<List<InvestmentEntity>>

    /** SIPs whose expected debit has come and gone — the basis of "did my SIP go through?". */
    @Query(
        """
        SELECT * FROM investments
        WHERE isSip = 1 AND isActive = 1
          AND nextExpectedAt IS NOT NULL AND nextExpectedAt <= :onOrBefore
        ORDER BY nextExpectedAt ASC
        """,
    )
    suspend fun sipsDueBy(onOrBefore: LocalDate): List<InvestmentEntity>

    /** Net contributed across every holding: totals invested less what has been taken back out. */
    @Query("SELECT COALESCE(SUM(totalInvestedMinor - totalRedeemedMinor), 0) FROM investments")
    fun observeNetInvestedMinor(): Flow<Long>

    @Query(
        """
        UPDATE investments
        SET totalInvestedMinor = totalInvestedMinor + :amountMinor,
            contributionCount = contributionCount + 1,
            firstInvestedAt = MIN(COALESCE(firstInvestedAt, :atMillis), :atMillis),
            lastInvestedAt = MAX(COALESCE(lastInvestedAt, :atMillis), :atMillis)
        WHERE id = :id
        """,
    )
    suspend fun recordContribution(id: Long, amountMinor: Long, atMillis: Long)

    @Query("UPDATE investments SET totalRedeemedMinor = totalRedeemedMinor + :amountMinor WHERE id = :id")
    suspend fun recordRedemption(id: Long, amountMinor: Long)

    @Query("UPDATE investments SET nextExpectedAt = :date WHERE id = :id")
    suspend fun setNextExpectedAt(id: Long, date: LocalDate?)

    @Query("UPDATE investments SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
