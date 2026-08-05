package com.lifeledger.sms.ingest

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.lifeledger.core.model.SmsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads messages out of the system SMS provider.
 *
 * Reading is chunked rather than "select everything": inboxes of 100,000+ messages are
 * common on phones people have kept for years, and materialising that in one cursor pass
 * is the difference between a backfill that completes and one the system kills. Callers
 * page with [readChunk] and persist the cursor position between chunks so an interrupted
 * backfill resumes instead of restarting.
 */
@Singleton
class SmsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Total messages in the inbox, for backfill progress reporting. */
    fun count(): Int {
        if (!hasPermission()) return 0
        return context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null,
        )?.use { it.count } ?: 0
    }

    /**
     * Reads up to [limit] messages received strictly after [afterMillis], oldest first.
     *
     * Ordering ascending by date is deliberate: it means the caller's "high-water mark" is
     * simply the timestamp of the last row it processed, which survives a crash without
     * needing a separate durable cursor.
     */
    fun readChunk(afterMillis: Long, limit: Int): List<SmsRecord> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.THREAD_ID,
        )

        return context.contentResolver.queryCompat(
            selection = "${Telephony.Sms.DATE} > ?",
            selectionArgs = arrayOf(afterMillis.toString()),
            projection = projection,
            sortOrder = "${Telephony.Sms.DATE} ASC LIMIT $limit",
        )?.use { cursor -> cursor.toRecords() } ?: emptyList()
    }

    private fun ContentResolver.queryCompat(
        selection: String?,
        selectionArgs: Array<String>?,
        projection: Array<String>,
        sortOrder: String?,
    ): Cursor? = query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, sortOrder)

    private fun Cursor.toRecords(): List<SmsRecord> {
        val idIndex = getColumnIndex(Telephony.Sms._ID)
        val addressIndex = getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIndex = getColumnIndex(Telephony.Sms.BODY)
        val dateIndex = getColumnIndex(Telephony.Sms.DATE)
        val threadIndex = getColumnIndex(Telephony.Sms.THREAD_ID)

        val records = ArrayList<SmsRecord>(count)
        while (moveToNext()) {
            val body = bodyIndex.takeIf { it >= 0 }?.let { getString(it) } ?: continue
            val sender = addressIndex.takeIf { it >= 0 }?.let { getString(it) } ?: continue
            val millis = dateIndex.takeIf { it >= 0 }?.let { getLong(it) } ?: continue
            records += SmsRecord(
                fingerprint = fingerprintOf(sender, body, millis),
                sender = sender,
                body = body,
                receivedAt = Instant.ofEpochMilli(millis),
                threadId = threadIndex.takeIf { it >= 0 }?.let { getLong(it) },
                providerId = idIndex.takeIf { it >= 0 }?.let { getLong(it) },
            )
        }
        return records
    }

    companion object {
        /**
         * Identity of a message, independent of the provider row id.
         *
         * Provider ids are not stable across a device restore, and the same message can be
         * re-delivered; hashing sender + body + timestamp means re-importing an inbox is
         * idempotent no matter how the messages got there.
         */
        fun fingerprintOf(sender: String, body: String, receivedAtMillis: Long): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(sender.trim().uppercase().toByteArray())
            digest.update(0)
            digest.update(body.trim().toByteArray())
            digest.update(0)
            // Second precision: some providers jitter the millisecond field on re-delivery.
            digest.update((receivedAtMillis / 1000).toString().toByteArray())
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
