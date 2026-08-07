package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.SubscriptionEntity
import com.lifeledger.core.model.Subscription
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Recurring charges the detector inferred from repeated debits. */
@Dao
interface SubscriptionDao {

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity): Long

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observeById(id: Long): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun findById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions ORDER BY status ASC, nextExpectedAt ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE status = :status ORDER BY nextExpectedAt ASC")
    fun observeByStatus(status: Subscription.Status): Flow<List<SubscriptionEntity>>

    /**
     * The detector's identity check. Amount is part of it only through [findByMerchantAndName]
     * callers, because a subscription that changes price is still the same subscription.
     */
    @Query("SELECT * FROM subscriptions WHERE merchantId IS :merchantId AND name = :name LIMIT 1")
    suspend fun findByMerchantAndName(merchantId: Long?, name: String): SubscriptionEntity?

    @Query(
        """
        SELECT * FROM subscriptions
        WHERE status = 'ACTIVE' AND nextExpectedAt IS NOT NULL AND nextExpectedAt <= :onOrBefore
        ORDER BY nextExpectedAt ASC
        """,
    )
    suspend fun dueBy(onOrBefore: LocalDate): List<SubscriptionEntity>

    /**
     * Normalised monthly cost of everything still running.
     *
     * The arithmetic mirrors `Subscription.monthlyCost` so the dashboard figure and the list
     * cannot disagree; irregular and one-off rows contribute nothing because they have no
     * cadence to normalise against.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE recurrence
                WHEN 'DAILY' THEN amountMinor * 30
                WHEN 'WEEKLY' THEN amountMinor * 52 / 12
                WHEN 'FORTNIGHTLY' THEN amountMinor * 26 / 12
                WHEN 'MONTHLY' THEN amountMinor
                WHEN 'BIMONTHLY' THEN amountMinor / 2
                WHEN 'QUARTERLY' THEN amountMinor / 3
                WHEN 'HALF_YEARLY' THEN amountMinor / 6
                WHEN 'YEARLY' THEN amountMinor / 12
                ELSE 0
            END
        ), 0)
        FROM subscriptions
        WHERE status = 'ACTIVE'
        """,
    )
    fun observeMonthlyCostMinor(): Flow<Long>

    @Query("UPDATE subscriptions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: Subscription.Status)

    @Query("UPDATE subscriptions SET userConfirmed = 1, confidence = 1.0 WHERE id = :id")
    suspend fun confirm(id: Long)

    @Query(
        """
        UPDATE subscriptions
        SET chargeCount = chargeCount + 1,
            lastChargedAt = MAX(COALESCE(lastChargedAt, :atMillis), :atMillis),
            firstChargedAt = MIN(COALESCE(firstChargedAt, :atMillis), :atMillis),
            nextExpectedAt = :nextExpectedAt
        WHERE id = :id
        """,
    )
    suspend fun recordCharge(id: Long, atMillis: Long, nextExpectedAt: LocalDate?)

    /** Records a price change, keeping the previous amount so the UI can explain the jump. */
    @Query(
        """
        UPDATE subscriptions
        SET previousAmountMinor = amountMinor, amountMinor = :amountMinor, priceChangedAt = :atMillis
        WHERE id = :id AND amountMinor <> :amountMinor
        """,
    )
    suspend fun recordPriceChange(id: Long, amountMinor: Long, atMillis: Long)
}
