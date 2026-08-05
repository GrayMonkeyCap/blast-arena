package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A recurring charge Life Ledger detected by finding a repeating amount/merchant rhythm.
 *
 * Detection is entirely retrospective — the app watches what actually got charged rather
 * than asking the user to declare subscriptions up front.
 */
data class Subscription(
    val id: Long = 0,
    val name: String,
    val merchantId: Long? = null,
    val amount: Money,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val category: TxnCategory = TxnCategory.SUBSCRIPTIONS,
    val firstChargedAt: Instant? = null,
    val lastChargedAt: Instant? = null,
    val nextExpectedAt: LocalDate? = null,
    val chargeCount: Int = 0,
    val status: Status = Status.ACTIVE,
    val confidence: Confidence = Confidence.MEDIUM,
    /** Set when the user confirmed or created this rather than the detector inferring it. */
    val userConfirmed: Boolean = false,
    val notes: String? = null,
    /** Amount at the previous cadence, when a price change was observed. */
    val previousAmount: Money? = null,
    val priceChangedAt: Instant? = null,
) {
    /** Normalised monthly cost, so a yearly plan can be compared with a monthly one. */
    val monthlyCost: Money
        get() = when (recurrence) {
            Recurrence.DAILY -> Money(amount.minor * 30, amount.currency)
            Recurrence.WEEKLY -> Money(amount.minor * 52 / 12, amount.currency)
            Recurrence.FORTNIGHTLY -> Money(amount.minor * 26 / 12, amount.currency)
            Recurrence.MONTHLY -> amount
            Recurrence.BIMONTHLY -> Money(amount.minor / 2, amount.currency)
            Recurrence.QUARTERLY -> Money(amount.minor / 3, amount.currency)
            Recurrence.HALF_YEARLY -> Money(amount.minor / 6, amount.currency)
            Recurrence.YEARLY -> Money(amount.minor / 12, amount.currency)
            Recurrence.IRREGULAR, Recurrence.ONE_OFF -> Money.zero(amount.currency)
        }

    val annualCost: Money get() = Money(monthlyCost.minor * 12, amount.currency)

    enum class Status {
        ACTIVE,
        /** No charge seen for well over one cycle — probably cancelled. */
        LAPSED,
        CANCELLED,
        PAUSED,
    }
}
