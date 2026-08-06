package com.lifeledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeledger.core.datastore.PreferencesRepository
import com.lifeledger.core.datastore.UserPreferences
import com.lifeledger.core.security.lock.AppLockManager
import com.lifeledger.core.security.lock.LockState
import com.lifeledger.sms.ingest.SmsIngestScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the three things that decide what the whole app shows before any screen renders:
 * whether preferences have loaded, whether the app is locked, and whether onboarding is
 * still outstanding.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appLockManager: AppLockManager,
    private val ingestScheduler: SmsIngestScheduler,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        preferencesRepository.preferences,
        appLockManager.lockState,
    ) { preferences, lockState ->
        when {
            lockState is LockState.Locked -> AppUiState.Locked(preferences)
            !preferences.onboardingCompleted -> AppUiState.Onboarding(preferences)
            else -> AppUiState.Ready(preferences)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppUiState.Loading,
    )

    fun shouldUseDarkTheme(state: AppUiState): Boolean = when (val prefs = state.preferencesOrNull) {
        null -> isSystemInDarkThemeFallback
        else -> when (prefs.theme) {
            UserPreferences.Theme.LIGHT -> false
            UserPreferences.Theme.DARK -> true
            UserPreferences.Theme.SYSTEM -> isSystemInDarkThemeFallback
        }
    }

    fun shouldUseDynamicColor(state: AppUiState): Boolean =
        state.preferencesOrNull?.dynamicColor ?: true

    fun unlock() {
        appLockManager.unlock()
    }

    fun onUserInteraction() {
        appLockManager.onUserInteraction()
    }

    /**
     * Ingestion is scheduled here — after the user has actually granted permission —
     * rather than at process start, so the app never enqueues work it cannot perform.
     */
    fun onSmsPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.update { it.copy(smsIngestionEnabled = granted) }
            if (!granted) return@launch
            val preferences = uiState.value.preferencesOrNull
            if (preferences?.backfillCompleted == true) {
                ingestScheduler.scheduleIncremental()
            } else {
                ingestScheduler.scheduleBackfill()
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.update { it.copy(onboardingCompleted = true) }
        }
    }

    /**
     * The system dark-mode flag is a composition-local concern, so the Activity resolves it
     * and the ViewModel only needs a value it can fall back to before composition starts.
     */
    var isSystemInDarkThemeFallback: Boolean = false

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Everything the app shell needs before any destination is shown. */
sealed interface AppUiState {
    data object Loading : AppUiState
    data class Locked(val preferences: UserPreferences) : AppUiState
    data class Onboarding(val preferences: UserPreferences) : AppUiState
    data class Ready(val preferences: UserPreferences) : AppUiState

    val preferencesOrNull: UserPreferences?
        get() = when (this) {
            is Loading -> null
            is Locked -> preferences
            is Onboarding -> preferences
            is Ready -> preferences
        }
}
