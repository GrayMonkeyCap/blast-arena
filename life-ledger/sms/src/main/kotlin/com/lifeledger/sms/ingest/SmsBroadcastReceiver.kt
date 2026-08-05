package com.lifeledger.sms.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lifeledger.core.common.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Wakes the ingestion pipeline when a new message arrives.
 *
 * The receiver does as close to nothing as possible: a broadcast receiver runs on the main
 * thread with a hard time budget, and parsing a message there would risk an ANR on a slow
 * device receiving a burst. It hands off to WorkManager and returns immediately.
 *
 * It deliberately does *not* read the message out of the intent. The provider is the single
 * source of truth, and reading from it in the worker keeps one code path for both backfill
 * and incremental ingestion — half as much code to get right, and no risk of the two
 * disagreeing about how a message is fingerprinted.
 */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: SmsIngestScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        AppLog.d(TAG) { "SMS received; scheduling incremental ingestion" }
        scheduler.scheduleIncremental()
    }

    private companion object {
        const val TAG = "SmsBroadcastReceiver"
    }
}
