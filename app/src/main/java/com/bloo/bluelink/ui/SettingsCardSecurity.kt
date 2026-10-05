package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.key
import android.content.Context
import androidx.compose.material.icons.filled.LockReset
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setLockTiming

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
    val status = when {
        !canBio -> "No biometrics enrolled"
        locked -> "Locked · ${appearance.lockTiming.label}"
        else -> "Not locked"
    }
    var pinDialog by remember { mutableStateOf<String?>(null) }
    val pinSet = state.appPinSet
    SettingsCard("Security", AppIcons.Lock, vm, status = status) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("App lock") {
                if (canBio) {
                    AppLockRow(state, appearance, vm, context)
                } else {
                    BodySmallText("No biometrics are enrolled on this device.")
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
                        SafeMorphTextButton("Remove", onClick = { pinDialog = "remove" }, emphasis = ButtonEmphasis.Destructive)
                    }
                }
            }
        }
        PinDialogs(mode = pinDialog, onDismiss = { pinDialog = null }, vm = vm, state = state, canBio = canBio)
    }
}


/**
 * The app-lock control: Off / Screen off / Immediate. Shared by the Security card and settings search, so
 * the two can never disagree about what turning the lock off means.
 *
 * Turning it OFF or ON needs a biometric confirmation (otherwise one tap from an already-unlocked app could
 * remove the lock for good); failing to confirm keeps things as they were. "Off" also removes the app PIN,
 * since the cold-start lock is "biometrics on OR a PIN set" and leaving the PIN would keep asking on every
 * launch. Changing only WHEN it re-locks needs no extra proof.
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
                        subtitle = "Confirm to require it on launch",
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
