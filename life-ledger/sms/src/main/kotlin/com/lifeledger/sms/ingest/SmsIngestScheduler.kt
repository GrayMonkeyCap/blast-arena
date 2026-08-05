package com.lifeledger.sms.ingest

/**
 * How the SMS module asks for ingestion work to run.
 *
 * The `:sms` module owns *reading* messages but not *storing* them, so the workers that
 * write to the database live in `:data`. This interface is the seam between the two: the
 * broadcast receiver in this module can trigger processing without `:sms` gaining a
 * dependency on the data layer, which is what keeps parsers pure and unit-testable.
 */
interface SmsIngestScheduler {

    /** One-time import of everything already in the device inbox. Resumable and chunked. */
    fun scheduleBackfill()

    /**
     * Processes messages that arrived since the last run. Called from the SMS broadcast
     * receiver, so implementations must coalesce: a burst of ten messages should produce
     * one run, not ten.
     */
    fun scheduleIncremental()

    /** Re-runs every stored message through the current parsers after a parser upgrade. */
    fun scheduleReprocess()

    fun cancelAll()
}
