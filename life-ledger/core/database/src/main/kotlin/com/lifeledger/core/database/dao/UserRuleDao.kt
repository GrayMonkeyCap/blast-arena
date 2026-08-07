package com.lifeledger.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.lifeledger.core.database.entity.UserRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * User-authored rewrite rules.
 *
 * The engine reads [enabledInOrder] once per pipeline run and applies the result in the order
 * it comes back, so ordering is settled here rather than in the caller: priority first, then
 * id, which makes rule evaluation deterministic across runs and across devices.
 */
@Dao
interface UserRuleDao {

    @Upsert
    suspend fun upsert(rule: UserRuleEntity): Long

    @Delete
    suspend fun delete(rule: UserRuleEntity)

    @Query("DELETE FROM user_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM user_rules ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    @Query("SELECT * FROM user_rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun enabledInOrder(): List<UserRuleEntity>

    @Query("SELECT * FROM user_rules WHERE id = :id")
    suspend fun findById(id: Long): UserRuleEntity?

    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE user_rules SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    /** Feeds the "this rule has changed 214 transactions" line in the rule editor. */
    @Query("UPDATE user_rules SET timesApplied = timesApplied + :delta WHERE id = :id")
    suspend fun recordApplications(id: Long, delta: Int)

    @Query("SELECT COUNT(*) FROM user_rules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>
}
