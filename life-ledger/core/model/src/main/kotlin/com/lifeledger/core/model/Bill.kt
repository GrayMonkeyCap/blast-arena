package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A recurring bill with an estimated due date.
 *
 * Due dates come from two places: an explicit due date in a biller SMS, or — when the
 * biller never states one — the median gap between past payments.
 */
data class Bill(
    val id: Long = 0,
    val name: String,
    val type: BillType,
    val merchantId: Long? = null,
    val accountId: Long? = null,
    /** Consumer/connection number, when the biller included one. */
    val consumerNumber: String? = null,
    val lastAmount: Money? = null,
    val averageAmount: Money? = null,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val lastPaidAt: Instant? = null,
    val dueDate: LocalDate? = null,
    val dueDateIsEstimated: Boolean = true,
    val amountDue: Money? = null,
    val status: Status = Status.UPCOMING,
    val paymentCount: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val confidence: Confidence = Confidence.MEDIUM,
) {
    enum class Status { UPCOMING, DUE, OVERDUE, PAID, INACTIVE }
}
