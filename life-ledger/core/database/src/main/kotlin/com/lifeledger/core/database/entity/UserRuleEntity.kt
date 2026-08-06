package com.lifeledger.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lifeledger.core.model.UserRule

/**
 * A user-authored rewrite rule.
 *
 * [conditions] and [actions] are stored as JSON in a single column each rather than in child
 * tables. A rule is always read and written whole — nothing ever queries "all rules whose
 * third condition mentions Swiggy" — so two extra tables and their joins would buy nothing
 * and cost the atomicity that makes rule editing safe.
 */
@Entity(
    tableName = "user_rules",
    indices = [
        // The engine's only read: enabled rules in priority order.
        Index(value = ["enabled", "priority"]),
        Index(value = ["name"]),
    ],
)
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** Lower runs first; ties broken by [id]. */
    val priority: Int = 100,
    val conditions: List<UserRule.Condition>,
    val actions: List<UserRule.Action>,
    val matchAllConditions: Boolean = true,
    val stopOnMatch: Boolean = false,
    val createdAt: Long = 0,
    val timesApplied: Int = 0,
)
