package com.lifeledger.sms.api

import com.lifeledger.core.model.ParserInfo

/**
 * Ordered collection of the parsers the engine will try.
 *
 * Ordering is by [ParserInfo.priority] ascending, so specific bank parsers get first
 * refusal and the generic fallbacks run last. New parsers are added by binding them into
 * the Hilt multibinding set — nothing else in the app changes, which is the extensibility
 * requirement made concrete.
 */
interface ParserRegistry {
    /** Enabled parsers in the order the pipeline should try them. */
    fun parsers(): List<SmsParser>

    /** Every registered parser, enabled or not, for the Parser Management screen. */
    fun allInfo(): List<ParserInfo>

    fun setEnabled(parserId: String, enabled: Boolean)
}
