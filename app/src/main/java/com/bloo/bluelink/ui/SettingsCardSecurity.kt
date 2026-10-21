package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setLockTiming
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.SegmentOption

/** The "Security" card: the app lock and the PIN that backs it up. */
@Composable
internal fun SecurityCardContent(
    state: UiState,
    vm: AppViewModel,
    canBio: Boolean,
    context: Context,
    appearance: SettingsStore.Appearance,
) {
    val locked = canBio && appearance.biometricLock
    val pinSet = state.appPinSet
    // With no biometrics the PIN IS the lock -- the lock gate arms off a PIN alone
    // ((biometricLock && canUseBiometrics()) || pinInstalled) -- so the status is about the PIN, not
    // about a biometric toggle this device cannot offer.
    val status = when {
        !canBio -> if (pinSet) "Locked · PIN" else "Set a PIN to lock Bloo"
        locked -> "Locked · ${appearance.lockTiming.label}"
        else -> "Not locked"
    }
    var pinDialog by remember { mutableStateOf<String?>(null) }
    SettingsCard("Security", AppIcons.Lock, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("App lock") {
                if (canBio) {
                    AppLockRow(state, appearance, vm, context)
                } else {
                    // No biometrics: the PIN is the only mechanism, so say so plainly rather than
                    // leaving a note that reads like the lock is unavailable. The App PIN group below
                    // is the control.
                    BodySmallText(
                        if (pinSet) "No biometrics on this device. Bloo locks with your app PIN below."
                        else "No biometrics on this device. Set an app PIN below and Bloo will lock with it.",
                    )
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
            SettingsGroup("App PIN") {
                BodySmallText(
                    if (canBio) "A 4-8 digit PIN that works as a backup when biometrics aren't available."
                    else "This device has no biometrics, so the app unlocks with this PIN.",
                )
                ActionRow {
                    MorphActionButton(
                        label = if (pinSet) "Change PIN" else "Set up PIN",
                        icon = if (pinSet) Icons.Filled.LockReset else AppIcons.Lock,
                        onClick = { pinDialog = "set" },
                    )
                    if (pinSet) {
                        MorphTextButton("Remove", onClick = { pinDialog = "remove" }, emphasis = ButtonEmphasis.Deny)
                    }
                }
            }
        }
        PinDialogs(mode = pinDialog, onDismiss = { pinDialog = null }, vm = vm, state = state, canBio = canBio)
    }
}

/**
 * The app-lock control: Off / Screen off / Immediate. Shared by the Security card and settings
 * search, so the two can never disagree about what turning the lock off means.
 */
@Composable
internal fun AppLockRow(state: UiState, appearance: SettingsStore.Appearance, vm: AppViewModel, context: Context) {
    val lockOn = appearance.biometricLock
    SettingsSegmentedRow(
        label = "Ask for biometrics",
        options = listOf(
            SegmentOption("off", LockTiming.OFF.label, null),
            SegmentOption("screen_off", LockTiming.SCREEN_OFF.label, null),
            SegmentOption("immediate", LockTiming.IMMEDIATE.label, null),
        ),
        selectedKey = when {
            !lockOn -> "off"
            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
            else -> "screen_off"
        },
        onSelect = { key ->
            val activity = context.findFragmentActivity()
            if (key == "off") {
                val pinAlsoSet = state.appPinSet
                if (activity == null) {
                    vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                } else {
                    showBiometricPrompt(
                        activity = activity,
                        title = "Turn off app lock",
                        subtitle = if (pinAlsoSet) "Confirm to stop requiring it. This also removes your PIN." else "Confirm to stop requiring it",
                        onSuccess = {
                            vm.setBiometricLock(false)
                            if (pinAlsoSet) vm.removeAppPin()
                        },
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
                        title = "Enable biometric lock",
                        subtitle = "Confirm to require it when opening Bloo",
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
