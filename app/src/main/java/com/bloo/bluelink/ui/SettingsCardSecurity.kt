package com.bloo.bluelink.ui

import com.bloo.uicommon.segmentsOf
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setLockTiming
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup

/** The "Security" card: the app lock, which the phone's own lock unlocks. */
@Composable
internal fun SecurityCardContent(
    vm: AppViewModel,
    canBio: Boolean,
    context: Context,
    appearance: SettingsStore.Appearance,
) {
    val locked = canBio && appearance.biometricLock
    // The app has no PIN of its own: the phone's lock (fingerprint, face, PIN, pattern or password) is the lock.
    val status = when {
        !canBio -> "Set a screen lock to lock Bloo"
        locked -> "Locked · ${appearance.lockTiming.label}"
        else -> "Not locked"
    }
    SettingsCard("Security", AppIcons.Lock, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("App lock") {
                if (canBio) {
                    AppLockRow(appearance, vm, context)
                } else {
                    // No screen lock on the phone: there is nothing for the app to unlock with, so say so plainly.
                    BodySmallText("Bloo locks with your phone's own lock. Set a screen lock (fingerprint, face, PIN, pattern or password) to turn it on.")
                }
            }
            SettingsGroup {
                var requireAuth by remember { mutableStateOf(com.bloo.bluelink.data.BootGate.enabled(context)) }
                ToggleRow(
                    "Sign in after a restart",
                    requireAuth,
                    description = "after your phone restarts, Bloo stays sealed (no alerts, AutoLock or refreshes) until you sign in and unlock. A notification reminds you.",
                ) {
                    requireAuth = it
                    com.bloo.bluelink.data.BootGate.setEnabled(context, it)
                }
            }
        }
    }
}

/**
 * The app-lock control: Off / Screen off / Immediate. Shared by the Security card and settings
 * search, so the two can never disagree about what turning the lock off means.
 */
@Composable
internal fun AppLockRow(appearance: SettingsStore.Appearance, vm: AppViewModel, context: Context) {
    val lockOn = appearance.biometricLock
    SettingsSegmentedRow(
        label = "Ask for your phone's lock",
        options = segmentsOf("off" to LockTiming.OFF.label, "screen_off" to LockTiming.SCREEN_OFF.label, "immediate" to LockTiming.IMMEDIATE.label),
        selectedKey = when {
            !lockOn -> "off"
            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
            else -> "screen_off"
        },
        onSelect = { key ->
            val activity = context.findFragmentActivity()
            if (key == "off") {
                if (activity == null) {
                    vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                } else {
                    showBiometricPrompt(
                        activity = activity,
                        title = "Turn off app lock",
                        subtitle = "Confirm to stop requiring it",
                        onSuccess = { vm.setBiometricLock(false) },
                        onError = { },
                    )
                }
            } else {
                val timing = if (key == "immediate") LockTiming.IMMEDIATE else LockTiming.SCREEN_OFF
                if (lockOn) {
                    vm.setLockTiming(timing)
                } else if (activity == null) {
                    vm.reportInfo("Couldn't verify it's you. The lock wasn't turned on.")
                } else {
                    showBiometricPrompt(
                        activity = activity,
                        title = BiometricLockWording.ENABLE_TITLE,
                        subtitle = BiometricLockWording.ENABLE_SUBTITLE,
                        onSuccess = {
                            vm.setBiometricLock(true)
                            vm.setLockTiming(timing)
                        },
                        onError = { },
                    )
                }
            }
        },
    )
}

/** The words of the app-lock prompt that turns the lock on, said the same way in onboarding and in Settings. */
internal object BiometricLockWording {
    const val ENABLE_TITLE = "Enable app lock"
    const val ENABLE_SUBTITLE = "Confirm to require your phone's lock when opening Bloo"
}
