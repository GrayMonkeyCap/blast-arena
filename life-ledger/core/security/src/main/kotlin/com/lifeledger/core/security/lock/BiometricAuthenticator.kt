package com.lifeledger.core.security.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Outcome of one [BiometricAuthenticator.authenticate] call. */
enum class AuthResult { SUCCESS, FAILED, CANCELLED, UNAVAILABLE, LOCKED_OUT }

/** Whether biometric (or device-credential) authentication can be offered at all right now. */
enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN,
}

/**
 * Thin wrapper over [BiometricPrompt] that turns its callback API into a single suspend call.
 *
 * Authenticators accept `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` so a user with no enrolled
 * biometric (or a temporarily unavailable sensor) can still unlock with their device PIN/
 * pattern/password — the app-lock screen should never be a dead end. Android disallows
 * combining `DEVICE_CREDENTIAL` with a custom negative-button label (the system supplies its
 * own "use PIN" affordance instead), so no negative button text is set.
 */
@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun canAuthenticate(): BiometricAvailability {
        val result = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.UNSUPPORTED
            else -> BiometricAvailability.UNKNOWN
        }
    }

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
    ): AuthResult = suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(AuthResult.SUCCESS)
            }

            override fun onAuthenticationFailed() {
                // A single failed read (e.g. an unrecognised fingerprint). The prompt stays
                // open for another attempt, so the coroutine is not resolved here.
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!continuation.isActive) return
                continuation.resume(errorCode.toAuthResult())
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let(::setSubtitle) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(promptInfo)
    }

    private fun Int.toAuthResult(): AuthResult = when (this) {
        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            AuthResult.LOCKED_OUT
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> AuthResult.CANCELLED
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        -> AuthResult.UNAVAILABLE
        else -> AuthResult.FAILED
    }

    private companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
