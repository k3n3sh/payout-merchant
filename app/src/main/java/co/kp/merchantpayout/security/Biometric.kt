package co.kp.merchantpayout.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// ─── State types ──────────────────────────────────────────────────────

// can we even prompt right now? each state need a different UI message.
sealed class BiometricAvailability {
    object Ready : BiometricAvailability()
    object NotEnrolled : BiometricAvailability()
    object NoHardware : BiometricAvailability()
    object TemporarilyUnavailable : BiometricAvailability()
    object SecurityUpdateRequired : BiometricAvailability()
    object Unknown : BiometricAvailability()
}

// outcome of a single prompt.
sealed class BiometricResult {
    object Success : BiometricResult()
    object UserCanceled : BiometricResult()
    object LockedOut : BiometricResult()
    data class Error(val code: Int, val message: String) : BiometricResult()
}

// ─── Gate interface + impl ────────────────────────────────────────────

interface BiometricGate {
    fun availability(): BiometricAvailability
}

// use BiometricManager to check if we can prompt. class 3 (STRONG) — hardware backed,
// required for payment apps under PSD2.
@Singleton
class AndroidBiometricGate @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricGate {

    override fun availability(): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val status = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (status == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricAvailability.Ready
        }
        if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return BiometricAvailability.NotEnrolled
        }
        if (status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            return BiometricAvailability.NoHardware
        }
        if (status == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            return BiometricAvailability.TemporarilyUnavailable
        }
        if (status == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED) {
            return BiometricAvailability.SecurityUpdateRequired
        }
        return BiometricAvailability.Unknown
    }
}

// ─── Reach into Hilt from a composable ────────────────────────────────

// composables cant use @Inject. EntryPoint let us grab the gate from the singleton graph.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BiometricGateEntryPoint {
    fun biometricGate(): BiometricGate
}

@Composable
fun rememberBiometricGate(): BiometricGate {
    val context = LocalContext.current
    return remember(context) {
        val app = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(app, BiometricGateEntryPoint::class.java)
        entryPoint.biometricGate()
    }
}

// ─── The prompt itself (a suspend function you can await) ─────────────

@Composable
fun rememberBiometricPromptLauncher(
    title: String,
    subtitle: String,
    negativeText: String = "Cancel",
): suspend () -> BiometricResult {
    val context = LocalContext.current
    val activity = remember(context) { findFragmentActivity(context) }
    return remember(activity, title, subtitle, negativeText) {
        {
            if (activity == null) {
                BiometricResult.Error(-1, "No FragmentActivity host — biometric prompt cant show.")
            } else {
                showPrompt(activity, title, subtitle, negativeText)
            }
        }
    }
}

private suspend fun showPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    negativeText: String,
): BiometricResult {
    return suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive)
                    continuation.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val mapped: BiometricResult
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                )
                    mapped = BiometricResult.UserCanceled
                 else if (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                    errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                )
                    mapped = BiometricResult.LockedOut
                 else
                    mapped = BiometricResult.Error(errorCode, errString.toString())



                if (continuation.isActive)
                    continuation.resume(mapped)

            }

            override fun onAuthenticationFailed() {
                // huhh I used wrong finger and it failed. doing nothing here.
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()
        prompt.authenticate(info)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}

// walk the Context chain to find the FragmentActivity that hosts the current window.
private fun findFragmentActivity(context: Context): FragmentActivity? {
    var current: Context = context
    while (true) {
        if (current is FragmentActivity) {
            return current
        }
        if (current is ContextWrapper) {
            current = current.baseContext
        } else {
            return null
        }
    }
}