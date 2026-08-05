package com.lifeledger.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * A holding Life Ledger has inferred from investment-related SMS.
 *
 * This is a *contribution ledger*, not a portfolio valuation: with no network access there
 * is no live NAV, so the app tracks what was put in and when. Current value is only shown
 * when the user enters it manually or a statement import supplies it.
 */
data class Investment(
    val id: Long = 0,
    val name: String,
    val instrumentType: InstrumentType,
    val folioOrAccount: String? = null,
    val provider: String? = null,
    val merchantId: Long? = null,
    val totalInvested: Money = Money.ZERO,
    val totalRedeemed: Money = Money.ZERO,
    /** Only populated from manual entry or import — never fetched. */
    val manualCurrentValue: Money? = null,
    val valueAsOf: Instant? = null,
    val units: Double? = null,
    val isSip: Boolean = false,
    val sipAmount: Money? = null,
    val sipRecurrence: Recurrence = Recurrence.MONTHLY,
    val sipDayOfMonth: Int? = null,
    val nextExpectedAt: LocalDate? = null,
    val isActive: Boolean = true,
    val contributionCount: Int = 0,
    val firstInvestedAt: Instant? = null,
    val lastInvestedAt: Instant? = null,
) {
    val netInvested: Money get() = totalInvested - totalRedeemed
}

/** A single buy/sell/dividend event attached to an [Investment]. */
data class InvestmentTransaction(
    val id: Long = 0,
    val investmentId: Long,
    val transactionId: Long?,
    val amount: Money,
    val kind: Kind,
    val occurredAt: Instant,
    val units: Double? = null,
    val navOrPrice: Double? = null,
) {
    enum class Kind { BUY, SIP_INSTALMENT, SELL, DIVIDEND, INTEREST, BONUS, MATURITY }
}
