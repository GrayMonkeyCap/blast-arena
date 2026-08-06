package com.lifeledger.core.testing

import com.lifeledger.core.common.time.TimeProvider
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A [TimeProvider] whose clock only moves when a test tells it to.
 *
 * Every date-sensitive behaviour in Life Ledger (bill due dates, subscription lapse
 * detection, app-lock idle timeouts) is written against [TimeProvider] specifically so
 * tests can drive time deterministically with this fake instead of sleeping real threads.
 */
class FakeTimeProvider(
    initial: Instant = DEFAULT_INSTANT,
    private val fixedZone: ZoneId = ZoneId.of("Asia/Kolkata"),
) : TimeProvider {

    var instant: Instant = initial
        private set

    override fun now(): Instant = instant

    override fun zone(): ZoneId = fixedZone

    /** Moves the fake clock forward (or backward, with a negative duration) by [duration]. */
    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }

    /** Jumps the fake clock straight to [newInstant]. */
    fun set(newInstant: Instant) {
        instant = newInstant
    }

    private companion object {
        val DEFAULT_INSTANT: Instant = Instant.parse("2026-01-15T10:30:00Z")
    }
}
