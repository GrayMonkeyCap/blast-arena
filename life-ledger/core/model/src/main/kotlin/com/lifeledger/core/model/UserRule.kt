package com.lifeledger.core.model

import java.time.Instant

/**
 * A user-authored rule that rewrites transactions after parsing.
 *
 * Rules are the escape hatch that keeps the app useful when a parser gets something
 * subtly wrong: rather than editing hundreds of rows, the user writes one rule and it is
 * applied to matching history and to everything that arrives afterwards.
 */
data class UserRule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** Lower runs first; ties broken by id. */
    val priority: Int = 100,
    val conditions: List<Condition>,
    val actions: List<Action>,
    val matchAllConditions: Boolean = true,
    val stopOnMatch: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val timesApplied: Int = 0,
) {
    data class Condition(
        val field: Field,
        val operator: Operator,
        val value: String,
    ) {
        enum class Field {
            MERCHANT, RAW_MERCHANT, DESCRIPTION, SMS_BODY, SENDER,
            AMOUNT_MINOR, CATEGORY, TYPE, PAYMENT_METHOD, ACCOUNT, UPI_ID, BANK,
        }

        enum class Operator {
            EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, STARTS_WITH, ENDS_WITH,
            MATCHES_REGEX, GREATER_THAN, LESS_THAN,
        }
    }

    data class Action(
        val target: Target,
        val value: String,
    ) {
        enum class Target {
            SET_CATEGORY, SET_SUBCATEGORY, SET_TYPE, SET_MERCHANT, SET_PAYMENT_METHOD,
            ADD_TAG, SET_NOTE, EXCLUDE_FROM_STATS, MARK_SUBSCRIPTION, MARK_INVESTMENT,
        }
    }
}
