package com.bloo.bluelink.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Walks the Context wrapper chain to find the hosting FragmentActivity. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** The activity whose biometric prompt is on screen, if any. See [showBiometricPrompt]. */
private var inFlight: java.lang.ref.WeakReference<FragmentActivity>? = null

fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    /**
     * Only what the encrypted vault's key accepts: a STRONG biometric or the phone's own PIN, pattern or password. A weak biometric
     * (most face unlocks) passes the prompt but does not unseal the vault, so a sign-in that must open the vault asks this way.
     */
    strong: Boolean = false,
) {
    // One prompt at a time: a second request while one is on screen is ignored, so the lock never stacks two fingerprint prompts.
    // The guard clears when that prompt succeeds, fails or is cancelled, or when its activity is gone.
    val showing = inFlight?.get()
    if (showing != null && !showing.isDestroyed && !showing.isFinishing) {
        onError("A prompt is already open")
        return
    }
    inFlight = java.lang.ref.WeakReference(activity)
    val done = { inFlight = null }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                done()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                done()
                onError(errString.toString())
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        // The device's own lock (fingerprint, face, or the lock screen's PIN, pattern or password). The device lock is a
        // fallback the prompt offers itself, so there is no negative button: DEVICE_CREDENTIAL does not allow one.
        .setAllowedAuthenticators(
            (if (strong) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK) or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    prompt.authenticate(info)
}

/** [showBiometricPrompt] as a suspending call: true once the person passes it, false if they cancel or it fails. */
suspend fun awaitBiometricPrompt(activity: FragmentActivity, title: String, subtitle: String, strong: Boolean = false): Boolean =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        showBiometricPrompt(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = { if (cont.isActive) cont.resumeWith(Result.success(true)) },
            onError = { if (cont.isActive) cont.resumeWith(Result.success(false)) },
            strong = strong,
        )
    }
