package com.lifeledger.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifeledger.core.model.TxnCategory
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads and writes [UserPreferences].
 *
 * Exposes one [preferences] stream plus a general-purpose [update], and a focused setter per
 * field for call sites that only ever touch one setting (a settings toggle shouldn't need to
 * know the whole shape of [UserPreferences] just to flip a boolean).
 */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    /** Reads-modifies-writes the whole snapshot; the building block every setter below uses. */
    suspend fun update(transform: (UserPreferences) -> UserPreferences)

    suspend fun setTheme(theme: UserPreferences.Theme)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setAutoLockTimeoutSeconds(seconds: Int)
    suspend fun setSmsIngestionEnabled(enabled: Boolean)
    suspend fun setBackfillCompleted(completed: Boolean)
    suspend fun setLastBackfillAt(at: Instant?)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setBillRemindersEnabled(enabled: Boolean)
    suspend fun setInsightNotificationsEnabled(enabled: Boolean)
    suspend fun setDataRetentionDays(days: Int)
    suspend fun setKeepRawSms(keep: Boolean)
    suspend fun setDefaultCurrency(currency: String)
    suspend fun setStartOfWeek(day: DayOfWeek)
    suspend fun setFinancialYearStartMonth(month: Int)
    suspend fun setHiddenCategories(categories: Set<TxnCategory>)
    suspend fun setDashboardCardOrder(order: List<String>)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setParserLogRetentionDays(days: Int)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLastInsightRunAt(at: Instant?)
    suspend fun setLastBackupAt(at: Instant?)
}

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { cause ->
            // DataStore's `data` flow documents IOException as the failure mode for a
            // corrupted or unreadable preferences file (bad permissions, a half-written file
            // after a crash, disk corruption). There is no user-recoverable action for any of
            // those on a settings read, and every field already has a safe default, so the
            // correct behaviour is to fall back to defaults rather than crash the app on
            // startup. Any other exception type is a programming error and is rethrown.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs -> prefs[PREFERENCES_KEY]?.let(::decode) ?: UserPreferences() }

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { prefs ->
            val current = prefs[PREFERENCES_KEY]?.let(::decode) ?: UserPreferences()
            prefs[PREFERENCES_KEY] = encode(transform(current))
        }
    }

    override suspend fun setTheme(theme: UserPreferences.Theme) = update { it.copy(theme = theme) }
    override suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    override suspend fun setAppLockEnabled(enabled: Boolean) = update { it.copy(appLockEnabled = enabled) }
    override suspend fun setBiometricEnabled(enabled: Boolean) = update { it.copy(biometricEnabled = enabled) }
    override suspend fun setAutoLockTimeoutSeconds(seconds: Int) =
        update { it.copy(autoLockTimeoutSeconds = seconds) }
    override suspend fun setSmsIngestionEnabled(enabled: Boolean) =
        update { it.copy(smsIngestionEnabled = enabled) }
    override suspend fun setBackfillCompleted(completed: Boolean) =
        update { it.copy(backfillCompleted = completed) }
    override suspend fun setLastBackfillAt(at: Instant?) = update { it.copy(lastBackfillAt = at) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        update { it.copy(notificationsEnabled = enabled) }
    override suspend fun setBillRemindersEnabled(enabled: Boolean) =
        update { it.copy(billRemindersEnabled = enabled) }
    override suspend fun setInsightNotificationsEnabled(enabled: Boolean) =
        update { it.copy(insightNotificationsEnabled = enabled) }
    override suspend fun setDataRetentionDays(days: Int) = update { it.copy(dataRetentionDays = days) }
    override suspend fun setKeepRawSms(keep: Boolean) = update { it.copy(keepRawSms = keep) }
    override suspend fun setDefaultCurrency(currency: String) = update { it.copy(defaultCurrency = currency) }
    override suspend fun setStartOfWeek(day: DayOfWeek) = update { it.copy(startOfWeek = day) }
    override suspend fun setFinancialYearStartMonth(month: Int) =
        update { it.copy(financialYearStartMonth = month) }
    override suspend fun setHiddenCategories(categories: Set<TxnCategory>) =
        update { it.copy(hiddenCategories = categories) }
    override suspend fun setDashboardCardOrder(order: List<String>) =
        update { it.copy(dashboardCardOrder = order) }
    override suspend fun setDeveloperModeEnabled(enabled: Boolean) =
        update { it.copy(developerModeEnabled = enabled) }
    override suspend fun setParserLogRetentionDays(days: Int) =
        update { it.copy(parserLogRetentionDays = days) }
    override suspend fun setOnboardingCompleted(completed: Boolean) =
        update { it.copy(onboardingCompleted = completed) }
    override suspend fun setLastInsightRunAt(at: Instant?) = update { it.copy(lastInsightRunAt = at) }
    override suspend fun setLastBackupAt(at: Instant?) = update { it.copy(lastBackupAt = at) }

    private fun decode(json: String): UserPreferences = try {
        Json.decodeFromString<PreferencesDto>(json).toDomain()
    } catch (_: Exception) {
        // A value that fails to parse (format change, hand-edited file) is treated the same
        // way as no value at all: fall back to defaults rather than fail a settings read.
        UserPreferences()
    }

    private fun encode(preferences: UserPreferences): String =
        Json.encodeToString(PreferencesDto.serializer(), PreferencesDto.fromDomain(preferences))

    companion object {
        /**
         * The whole [UserPreferences] snapshot lives under one JSON-encoded key rather than
         * one Preferences key per field. This keeps [update] atomic — every setter above is a
         * read-modify-write of a single value inside one `dataStore.edit` transaction, so two
         * concurrent setters can never interleave and leave one field from each write.
         */
        val PREFERENCES_KEY: Preferences.Key<String> = stringPreferencesKey("user_preferences")
    }
}

/**
 * The on-disk shape of [UserPreferences]. Kept separate from the domain type so the domain
 * model never has to carry `@Serializable`/converter concerns, and so this file — not
 * [UserPreferences] — is the one place that has to change when the persisted format evolves.
 */
@Serializable
private data class PreferencesDto(
    val theme: String = UserPreferences.Theme.SYSTEM.name,
    val dynamicColor: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutSeconds: Int = UserPreferences.DEFAULT_AUTO_LOCK_TIMEOUT_SECONDS,
    val smsIngestionEnabled: Boolean = true,
    val backfillCompleted: Boolean = false,
    val lastBackfillAtEpochMillis: Long? = null,
    val notificationsEnabled: Boolean = true,
    val billRemindersEnabled: Boolean = true,
    val insightNotificationsEnabled: Boolean = true,
    val dataRetentionDays: Int = 0,
    val keepRawSms: Boolean = true,
    val defaultCurrency: String = "INR",
    val startOfWeek: String = DayOfWeek.MONDAY.name,
    val financialYearStartMonth: Int = 4,
    val hiddenCategories: Set<String> = emptySet(),
    val dashboardCardOrder: List<String> = emptyList(),
    val developerModeEnabled: Boolean = false,
    val parserLogRetentionDays: Int = 30,
    val onboardingCompleted: Boolean = false,
    val lastInsightRunAtEpochMillis: Long? = null,
    val lastBackupAtEpochMillis: Long? = null,
) {
    fun toDomain(): UserPreferences = UserPreferences(
        theme = runCatching { UserPreferences.Theme.valueOf(theme) }.getOrDefault(UserPreferences.Theme.SYSTEM),
        dynamicColor = dynamicColor,
        appLockEnabled = appLockEnabled,
        biometricEnabled = biometricEnabled,
        autoLockTimeoutSeconds = autoLockTimeoutSeconds,
        smsIngestionEnabled = smsIngestionEnabled,
        backfillCompleted = backfillCompleted,
        lastBackfillAt = lastBackfillAtEpochMillis?.let(Instant::ofEpochMilli),
        notificationsEnabled = notificationsEnabled,
        billRemindersEnabled = billRemindersEnabled,
        insightNotificationsEnabled = insightNotificationsEnabled,
        dataRetentionDays = dataRetentionDays,
        keepRawSms = keepRawSms,
        defaultCurrency = defaultCurrency,
        startOfWeek = runCatching { DayOfWeek.valueOf(startOfWeek) }.getOrDefault(DayOfWeek.MONDAY),
        financialYearStartMonth = financialYearStartMonth,
        hiddenCategories = hiddenCategories.mapNotNull { TxnCategory.fromNameOrNull(it) }.toSet(),
        dashboardCardOrder = dashboardCardOrder,
        developerModeEnabled = developerModeEnabled,
        parserLogRetentionDays = parserLogRetentionDays,
        onboardingCompleted = onboardingCompleted,
        lastInsightRunAt = lastInsightRunAtEpochMillis?.let(Instant::ofEpochMilli),
        lastBackupAt = lastBackupAtEpochMillis?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun fromDomain(preferences: UserPreferences): PreferencesDto = PreferencesDto(
            theme = preferences.theme.name,
            dynamicColor = preferences.dynamicColor,
            appLockEnabled = preferences.appLockEnabled,
            biometricEnabled = preferences.biometricEnabled,
            autoLockTimeoutSeconds = preferences.autoLockTimeoutSeconds,
            smsIngestionEnabled = preferences.smsIngestionEnabled,
            backfillCompleted = preferences.backfillCompleted,
            lastBackfillAtEpochMillis = preferences.lastBackfillAt?.toEpochMilli(),
            notificationsEnabled = preferences.notificationsEnabled,
            billRemindersEnabled = preferences.billRemindersEnabled,
            insightNotificationsEnabled = preferences.insightNotificationsEnabled,
            dataRetentionDays = preferences.dataRetentionDays,
            keepRawSms = preferences.keepRawSms,
            defaultCurrency = preferences.defaultCurrency,
            startOfWeek = preferences.startOfWeek.name,
            financialYearStartMonth = preferences.financialYearStartMonth,
            hiddenCategories = preferences.hiddenCategories.map { it.name }.toSet(),
            dashboardCardOrder = preferences.dashboardCardOrder,
            developerModeEnabled = preferences.developerModeEnabled,
            parserLogRetentionDays = preferences.parserLogRetentionDays,
            onboardingCompleted = preferences.onboardingCompleted,
            lastInsightRunAtEpochMillis = preferences.lastInsightRunAt?.toEpochMilli(),
            lastBackupAtEpochMillis = preferences.lastBackupAt?.toEpochMilli(),
        )
    }
}
