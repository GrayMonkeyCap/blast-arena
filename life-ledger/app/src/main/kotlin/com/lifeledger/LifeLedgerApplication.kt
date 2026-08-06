package com.lifeledger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lifeledger.core.common.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Startup deliberately does almost nothing. The database key lives behind the Keystore and
 * the first read of it can involve user-presence checks on some devices, so opening the
 * database is left to the first repository call rather than blocking cold start. Ingestion
 * is scheduled by the UI once permissions are known to be granted, not from here.
 */
@HiltAndroidApp
class LifeLedgerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager is configured here instead of through its default initialiser so that
     * workers can be constructor-injected, and so no background work can start before Hilt
     * has produced the dependency graph that supplies the database passphrase.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLog.enabled = BuildConfig.DEBUG
        createNotificationChannels()
    }

    /**
     * All notifications are local: bill reminders and the occasional insight. None of them
     * are pushed, because there is no server to push from.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BILLS,
                getString(R.string.channel_bills),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.channel_bills_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSIGHTS,
                getString(R.string.channel_insights),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_insights_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INGESTION,
                getString(R.string.channel_ingestion),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = getString(R.string.channel_ingestion_description) },
        )
    }

    companion object {
        const val CHANNEL_BILLS = "bills"
        const val CHANNEL_INSIGHTS = "insights"
        const val CHANNEL_INGESTION = "ingestion"
    }
}
