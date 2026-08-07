package com.lifeledger.core.security.lock

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.datastore.PreferencesRepository
import com.lifeledger.core.datastore.UserPreferences
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.core.testing.FakeTimeProvider
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockManagerTest {

    private val timeProvider = FakeTimeProvider(initial = Instant.parse("2026-01-01T00:00:00Z"))

    @Test
    fun `starts locked on process start when app lock is enabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakePreferencesRepository(UserPreferences(appLockEnabled = true))

        val manager = AppLockManager(repository, timeProvider, this)

        assertThat(manager.lockState.value).isEqualTo(LockState.Locked)
    }

    @Test
    fun `starts unlocked on process start when app lock is disabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakePreferencesRepository(UserPreferences(appLockEnabled = false))

        val manager = AppLockManager(repository, timeProvider, this)

        assertThat(manager.lockState.value).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `unlock reveals the app`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakePreferencesRepository(UserPreferences(appLockEnabled = true))
        val manager = AppLockManager(repository, timeProvider, this)
        check(manager.lockState.value == LockState.Locked)

        manager.unlock()

        assertThat(manager.lockState.value).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `checkIdleTimeout locks once idle time exceeds the configured timeout`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakePreferencesRepository(
                UserPreferences(appLockEnabled = true, autoLockTimeoutSeconds = 60),
            )
            val manager = AppLockManager(repository, timeProvider, this)
            manager.unlock()

            timeProvider.advanceBy(Duration.ofSeconds(59))
            manager.checkIdleTimeout()
            assertThat(manager.lockState.value).isEqualTo(LockState.Unlocked)

            timeProvider.advanceBy(Duration.ofSeconds(2))
            manager.checkIdleTimeout()
            assertThat(manager.lockState.value).isEqualTo(LockState.Locked)
        }

    @Test
    fun `onUserInteraction resets the idle clock so the timeout does not fire`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakePreferencesRepository(
                UserPreferences(appLockEnabled = true, autoLockTimeoutSeconds = 60),
            )
            val manager = AppLockManager(repository, timeProvider, this)
            manager.unlock()

            timeProvider.advanceBy(Duration.ofSeconds(50))
            manager.onUserInteraction()
            timeProvider.advanceBy(Duration.ofSeconds(50))
            manager.checkIdleTimeout()

            // 50s elapsed since the last interaction, under the 60s timeout, even though
            // 100s elapsed in total since unlock().
            assertThat(manager.lockState.value).isEqualTo(LockState.Unlocked)
        }

    @Test
    fun `checkIdleTimeout is a no-op when app lock is disabled`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakePreferencesRepository(
            UserPreferences(appLockEnabled = false, autoLockTimeoutSeconds = 1),
        )
        val manager = AppLockManager(repository, timeProvider, this)

        timeProvider.advanceBy(Duration.ofHours(1))
        manager.checkIdleTimeout()

        assertThat(manager.lockState.value).isEqualTo(LockState.Unlocked)
    }

    @Test
    fun `checkIdleTimeout is a no-op while already locked`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakePreferencesRepository(
            UserPreferences(appLockEnabled = true, autoLockTimeoutSeconds = 60),
        )
        val manager = AppLockManager(repository, timeProvider, this)
        check(manager.lockState.value == LockState.Locked)

        timeProvider.advanceBy(Duration.ofSeconds(1))
        manager.checkIdleTimeout()

        assertThat(manager.lockState.value).isEqualTo(LockState.Locked)
    }

    /** A minimal in-memory [PreferencesRepository] double, sufficient for [AppLockManager]. */
    private class FakePreferencesRepository(initial: UserPreferences) : PreferencesRepository {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<UserPreferences> = state

        override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
            state.value = transform(state.value)
        }

        override suspend fun setTheme(theme: UserPreferences.Theme) = update { it.copy(theme = theme) }
        override suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
        override suspend fun setAppLockEnabled(enabled: Boolean) = update { it.copy(appLockEnabled = enabled) }
        override suspend fun setBiometricEnabled(enabled: Boolean) =
            update { it.copy(biometricEnabled = enabled) }
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
        override suspend fun setDefaultCurrency(currency: String) =
            update { it.copy(defaultCurrency = currency) }
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
    }
}
