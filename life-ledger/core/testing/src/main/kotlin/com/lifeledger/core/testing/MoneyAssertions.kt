package com.lifeledger.core.testing

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Money

/**
 * Entry point for Truth-style assertions on [Money], mirroring the usual
 * `assertThat(x)....` shape so money assertions read the same as everything else.
 */
fun assertThatMoney(actual: Money): MoneySubject = MoneySubject(actual)

/**
 * A small, hand-rolled Truth-style subject for [Money].
 *
 * This deliberately isn't a full `com.google.common.truth.Subject` (which needs a
 * `Subject.Factory` and `StandardSubjectBuilder` wiring for very little benefit here) — it
 * exists to spell out amounts in rupees rather than paise, since `assertThat(m.minor)
 * .isEqualTo(49900)` reads worse than [isEqualToRupees].
 */
class MoneySubject internal constructor(private val actual: Money) {

    /** Compares against a whole-rupee amount, e.g. `isEqualToRupees(420)` for ₹420.00. */
    fun isEqualToRupees(rupees: Long) {
        assertThat(actual.minor).isEqualTo(rupees * 100)
    }

    /** Compares minor units directly, for amounts with paise. */
    fun isEqualToMinor(minor: Long) {
        assertThat(actual.minor).isEqualTo(minor)
    }

    fun isEqualTo(expected: Money) {
        assertThat(actual).isEqualTo(expected)
    }

    fun isZero() {
        assertThat(actual.isZero).isTrue()
    }

    fun isPositive() {
        assertThat(actual.isPositive).isTrue()
    }

    fun isNegative() {
        assertThat(actual.isNegative).isTrue()
    }

    fun hasCurrency(currency: String) {
        assertThat(actual.currency).isEqualTo(currency)
    }
}
