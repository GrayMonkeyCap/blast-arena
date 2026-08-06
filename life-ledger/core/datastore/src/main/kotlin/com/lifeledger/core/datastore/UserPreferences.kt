package com.lifeledger.core.datastore

import com.lifeledger.core.model.TxnCategory
import java.time.DayOfWeek
import java.time.Instant

/** Which colour scheme the app follows. [SYSTEM] tracks the OS day/night setting. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/**
 * A single immutable snapshot of every user-configurable setting in Life Ledger.
 *
 * There is deliberately one flat data class rather than several smaller preference groups:
 * settings are read together on almost every screen (theme + lock state on app start,
 * currency + week start on every chart), and a single object keeps the persisted shape,
 * the in-memory shape and the round-trip test all pointed at the same source of truth.
 */
data class UserPreferences(
    // --- Appearance ---------------------------------------------------------------------
    val theme: AppTheme = AppTheme.SYSTEM,
    val dynamicColor: Boolean = true,

    // --- App lock / security -------------------------------------------------------------
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutSeconds: Int = DEFAULT_AUTO_LOCK_TIMEOUT_SECONDS,

    // --- SMS ingestion ---------------------------------------------------------------------
    val smsIngestionEnabled: Boolean = true,
    val backfillCompleted: Boolean = false,
    val lastBackfillAt: Instant? = null,

    // --- Notifications -----------------------------------------------------------------------
    val notificationsEnabled: Boolean = true,
    val billRemindersEnabled: Boolean = true,
    val insightNotificationsEnabled: Boolean = true,

    // --- Data retention ----------------------------------------------------------------------
    /** Days of history to keep before automatic pruning; `0` means keep forever. */
    val dataRetentionDays: Int = 0,
    val keepRawSms: Boolean = true,

    // --- Locale / calendar ---------------------------------------------------------------------
    val defaultCurrency: String = "INR",
    val startOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** 1-12; India's financial year starts in April, so this defaults to 4. */
    val financialYearStartMonth: Int = 4,

    // --- Dashboard / UI personalisation --------------------------------------------------------
    val hiddenCategories: Set<TxnCategory> = emptySet(),
    val dashboardCardOrder: List<String> = emptyList(),

    // --- Developer / diagnostics ------------------------------------------------------------------
    val developerModeEnabled: Boolean = false,
    val parserLogRetentionDays: Int = 30,

    // --- Lifecycle bookkeeping ---------------------------------------------------------------------
    val onboardingCompleted: Boolean = false,
    val lastInsightRunAt: Instant? = null,
    val lastBackupAt: Instant? = null,
) {
    companion object {
        const val DEFAULT_AUTO_LOCK_TIMEOUT_SECONDS = 60
    }
}
