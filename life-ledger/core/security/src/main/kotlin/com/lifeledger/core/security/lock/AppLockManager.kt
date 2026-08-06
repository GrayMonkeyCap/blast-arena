package com.lifeledger.core.security.lock

import com.lifeledger.core.common.di.ApplicationScope
import com.lifeledger.core.common.time.TimeProvider
import com.lifeledger.core.datastore.PreferencesRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Whether the app is currently showing its lock screen. */
sealed interface LockState {
    data object Locked : LockState
    data object Unlocked : LockState
}

/**
 * Tracks whether Life Ledger is locked behind the app-lock screen, and when it should
 * auto-lock again after a period of inactivity.
 *
 * The idle timeout is enforced by [checkIdleTimeout], a *pull* the app calls on every
 * foreground resume, rather than a `delay()`-scheduled push. A scheduled coroutine timer
 * would not fire while the process is frozen in the background or the device is asleep, so
 * it cannot be trusted to lock the app "N seconds after the user stopped touching it" — by
 * the time the process resumes, far more than N seconds of wall-clock time may have passed.
 * Recomputing the idle duration against [TimeProvider.now] on every resume is exact
 * regardless of how long the process was actually suspended, and it is what makes this class
 * testable with a fake clock instead of real sleeps.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val _lockState = MutableStateFlow<LockState>(LockState.Unlocked)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    @Volatile
    private var lastActiveAt: Instant = timeProvider.now()

    init {
        // Locks immediately on process start whenever app lock is enabled: a killed-and-
        // restarted process must not resume already unlocked just because the user
        // authenticated before the process died.
        applicationScope.launch {
            val enabled = preferencesRepository.preferences.first().appLockEnabled
            _lockState.value = if (enabled) LockState.Locked else LockState.Unlocked
        }
    }

    /** Records user activity, resetting the idle-timeout clock. Does not itself unlock. */
    fun onUserInteraction() {
        lastActiveAt = timeProvider.now()
    }

    /** Reveals the app. Call only after a successful biometric/device-credential check. */
    fun unlock() {
        lastActiveAt = timeProvider.now()
        _lockState.value = LockState.Unlocked
    }

    /** Locks immediately — used by a "Lock now" setting and by [checkIdleTimeout]. */
    fun lock() {
        _lockState.value = LockState.Locked
    }

    /**
     * Re-evaluates the idle timeout against [TimeProvider.now] and [lock]s if it has
     * elapsed. See the class doc for why this is a pull rather than a scheduled timer.
     */
    suspend fun checkIdleTimeout() {
        if (_lockState.value is LockState.Locked) return
        val prefs = preferencesRepository.preferences.first()
        if (!prefs.appLockEnabled || prefs.autoLockTimeoutSeconds <= 0) return
        val idleSeconds = Duration.between(lastActiveAt, timeProvider.now()).seconds
        if (idleSeconds >= prefs.autoLockTimeoutSeconds) {
            lock()
        }
    }
}
