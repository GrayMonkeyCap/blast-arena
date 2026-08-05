package com.lifeledger.core.common.log

import android.util.Log

/**
 * Logging front end.
 *
 * Two rules hold everywhere in Life Ledger: nothing is logged in release builds, and SMS
 * bodies, account numbers and UPI ids are never passed to a logger even in debug. Use
 * [redact] whenever an identifier has to appear in a message at all.
 */
object AppLog {

    /** Flipped on by the app module for debug builds only. */
    @Volatile
    var enabled: Boolean = false

    fun d(tag: String, message: () -> String) {
        if (enabled) Log.d(tag, message())
    }

    fun i(tag: String, message: () -> String) {
        if (enabled) Log.i(tag, message())
    }

    fun w(tag: String, message: () -> String) {
        if (enabled) Log.w(tag, message())
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (enabled) Log.e(tag, message(), throwable)
    }

    /** Keeps only the last [visible] characters: `XXXX4521`. */
    fun redact(value: String?, visible: Int = 4): String {
        if (value.isNullOrEmpty()) return "?"
        if (value.length <= visible) return "X".repeat(value.length)
        return "X".repeat(value.length - visible) + value.takeLast(visible)
    }
}
