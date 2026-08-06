package com.lifeledger.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.TxnCategory
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newDataStore(fileName: String = "test.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { File(temporaryFolder.root, fileName) },
        )

    @Test
    fun `preferences emits defaults when nothing has been written`() = runTest {
        val repository = DataStorePreferencesRepository(newDataStore())

        val prefs = repository.preferences.first()

        assertThat(prefs).isEqualTo(UserPreferences())
    }

    @Test
    fun `update round-trips every field`() = runTest {
        val repository = DataStorePreferencesRepository(newDataStore())

        val expected = UserPreferences(
            theme = UserPreferences.Theme.DARK,
            dynamicColor = false,
            appLockEnabled = true,
            biometricEnabled = true,
            autoLockTimeoutSeconds = 120,
            smsIngestionEnabled = false,
            backfillCompleted = true,
            lastBackfillAt = Instant.parse("2026-01-10T08:15:00Z"),
            notificationsEnabled = false,
            billRemindersEnabled = false,
            insightNotificationsEnabled = false,
            dataRetentionDays = 365,
            keepRawSms = false,
            defaultCurrency = "USD",
            startOfWeek = DayOfWeek.SUNDAY,
            financialYearStartMonth = 1,
            hiddenCategories = setOf(TxnCategory.GIFTS, TxnCategory.CHARITY),
            dashboardCardOrder = listOf("cashflow", "bills", "insights"),
            developerModeEnabled = true,
            parserLogRetentionDays = 7,
            onboardingCompleted = true,
            lastInsightRunAt = Instant.parse("2026-01-14T00:00:00Z"),
            lastBackupAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        repository.update { expected }

        assertThat(repository.preferences.first()).isEqualTo(expected)
    }

    @Test
    fun `each focused setter updates only its own field`() = runTest {
        val repository = DataStorePreferencesRepository(newDataStore())
        val defaults = UserPreferences()

        repository.setTheme(UserPreferences.Theme.LIGHT)
        repository.setDynamicColor(false)
        repository.setAppLockEnabled(true)
        repository.setBiometricEnabled(true)
        repository.setAutoLockTimeoutSeconds(30)
        repository.setSmsIngestionEnabled(false)
        repository.setBackfillCompleted(true)
        repository.setLastBackfillAt(Instant.EPOCH)
        repository.setNotificationsEnabled(false)
        repository.setBillRemindersEnabled(false)
        repository.setInsightNotificationsEnabled(false)
        repository.setDataRetentionDays(90)
        repository.setKeepRawSms(false)
        repository.setDefaultCurrency("EUR")
        repository.setStartOfWeek(DayOfWeek.SATURDAY)
        repository.setFinancialYearStartMonth(7)
        repository.setHiddenCategories(setOf(TxnCategory.FEES))
        repository.setDashboardCardOrder(listOf("bills"))
        repository.setDeveloperModeEnabled(true)
        repository.setParserLogRetentionDays(14)
        repository.setOnboardingCompleted(true)
        repository.setLastInsightRunAt(Instant.EPOCH)
        repository.setLastBackupAt(Instant.EPOCH)

        val result = repository.preferences.first()

        assertThat(result).isEqualTo(
            defaults.copy(
                theme = UserPreferences.Theme.LIGHT,
                dynamicColor = false,
                appLockEnabled = true,
                biometricEnabled = true,
                autoLockTimeoutSeconds = 30,
                smsIngestionEnabled = false,
                backfillCompleted = true,
                lastBackfillAt = Instant.EPOCH,
                notificationsEnabled = false,
                billRemindersEnabled = false,
                insightNotificationsEnabled = false,
                dataRetentionDays = 90,
                keepRawSms = false,
                defaultCurrency = "EUR",
                startOfWeek = DayOfWeek.SATURDAY,
                financialYearStartMonth = 7,
                hiddenCategories = setOf(TxnCategory.FEES),
                dashboardCardOrder = listOf("bills"),
                developerModeEnabled = true,
                parserLogRetentionDays = 14,
                onboardingCompleted = true,
                lastInsightRunAt = Instant.EPOCH,
                lastBackupAt = Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `preferences falls back to defaults when the DataStore flow throws IOException`() = runTest {
        val repository = DataStorePreferencesRepository(FailingDataStore())

        val result = repository.preferences.first()

        assertThat(result).isEqualTo(UserPreferences())
    }

    /** A [DataStore] whose `data` flow always fails, simulating a corrupted preferences file. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw IOException("simulated corrupted preferences file")
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw UnsupportedOperationException("not used by this test")
    }
}
