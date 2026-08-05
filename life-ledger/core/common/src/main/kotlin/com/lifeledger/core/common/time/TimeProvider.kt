package com.lifeledger.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection over "now", so that every date-sensitive behaviour — bill due dates,
 * subscription lapse detection, insight windows — is testable without sleeping.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
