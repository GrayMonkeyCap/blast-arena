package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.BillEntity
import com.lifeledger.core.model.Bill
import com.lifeledger.core.model.BillType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Recurring bills and their due dates. */
@Dao
interface BillDao {

    @Upsert
    suspend fun upsert(bill: BillEntity): Long

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("SELECT * FROM bills WHERE id = :id")
    fun observeById(id: Long): Flow<BillEntity?>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun findById(id: Long): BillEntity?

    @Query("SELECT * FROM bills ORDER BY status ASC, dueDate ASC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE type = :type ORDER BY dueDate ASC")
    fun observeByType(type: BillType): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE status = :status ORDER BY dueDate ASC")
    fun observeByStatus(status: Bill.Status): Flow<List<BillEntity>>

    /**
     * Identity for the detector: one bill per (biller, consumer number). The consumer number
     * is compared with `IS` because most billers never state one, and two nameless
     * electricity bills from the same biller are the same bill.
     */
    @Query("SELECT * FROM bills WHERE merchantId IS :merchantId AND consumerNumber IS :consumerNumber LIMIT 1")
    suspend fun findByIdentity(merchantId: Long?, consumerNumber: String?): BillEntity?

    /** What the dashboard's "coming up" strip reads. */
    @Query(
        """
        SELECT * FROM bills
        WHERE status IN ('UPCOMING', 'DUE', 'OVERDUE')
          AND dueDate IS NOT NULL AND dueDate <= :onOrBefore
        ORDER BY dueDate ASC
        """,
    )
    fun observeDueBy(onOrBefore: LocalDate): Flow<List<BillEntity>>

    @Query(
        """
        SELECT * FROM bills
        WHERE type IN ('LOAN_EMI', 'CREDIT_CARD')
          AND status IN ('UPCOMING', 'DUE', 'OVERDUE')
          AND dueDate IS NOT NULL AND dueDate <= :onOrBefore
        ORDER BY dueDate ASC
        """,
    )
    fun observeEmisDueBy(onOrBefore: LocalDate): Flow<List<BillEntity>>

    /**
     * Ages bills whose date has passed. Run from the periodic worker rather than computed on
     * read, so that a reminder fires once at the moment a bill goes overdue instead of the
     * status flickering with whatever "today" happened to be when a screen was opened.
     */
    @Query(
        """
        UPDATE bills
        SET status = 'OVERDUE'
        WHERE status IN ('UPCOMING', 'DUE') AND dueDate IS NOT NULL AND dueDate < :today
        """,
    )
    suspend fun markOverdue(today: LocalDate): Int

    @Query(
        """
        UPDATE bills
        SET status = 'PAID',
            lastPaidAt = :atMillis,
            lastAmountMinor = :amountMinor,
            paymentCount = paymentCount + 1,
            amountDueMinor = NULL
        WHERE id = :id
        """,
    )
    suspend fun recordPayment(id: Long, amountMinor: Long, atMillis: Long)

    @Query("UPDATE bills SET dueDate = :dueDate, dueDateIsEstimated = :estimated WHERE id = :id")
    suspend fun setDueDate(id: Long, dueDate: LocalDate?, estimated: Boolean)

    @Query("UPDATE bills SET reminderEnabled = :enabled, reminderDaysBefore = :daysBefore WHERE id = :id")
    suspend fun setReminder(id: Long, enabled: Boolean, daysBefore: Int)
}
