package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// --- App PIN dialogs ------------------------------------------------------

/**
 * The Security card's PIN dialogs, one [GlassAlertDialog] shell with three stages: verify the
 * CURRENT PIN, then either enter a new one (set/change) or confirm removal.
 */
@Composable
internal fun PinDialogs(
    mode: String?,
    onDismiss: () -> Unit,
    vm: AppViewModel,
    state: UiState,
    canBio: Boolean,
) {
    if (mode == null) return
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val title = when (mode) {
        "set" -> if (state.appPinSet) "Change PIN" else "Set up PIN"
        else -> "Remove PIN"
    }

    // current-PIN gate -> (new PIN entry | remove confirm) A fresh setup (no PIN installed yet) has
    // no "current PIN" to prove -- jump straight to choosing the new one. Change and Remove always
    // gate on knowing the existing PIN first.
    var stage by remember(mode) {
        mutableStateOf(if (mode == "set" && !state.appPinSet) "finish" else "current")
    }
    var currentPin by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    // Baselines, so the effects below react to CHANGES only -- the initial composition must not
    // treat a stale flag (left by an earlier lock screen session, say) as a fresh event.
    var seenRejected by remember(mode) { mutableStateOf(state.pinAttemptRejected) }
    var seenTick by remember(mode) { mutableIntStateOf(state.pinAcceptedTick) }
    // Watch the verify outcome: a wrong PIN flags pinAttemptRejected (shown as an inline error
    // here, then acknowledged), a right one advances.
    LaunchedEffect(state.pinAttemptRejected) {
        if (state.pinAttemptRejected && !seenRejected) {
            seenRejected = true
            rejected = true
            currentPin = ""
            vm.acknowledgePinRejection()
        }
    }
    LaunchedEffect(state.pinAcceptedTick) {
        if (state.pinAcceptedTick != seenTick) {
            seenTick = state.pinAcceptedTick
            if (stage == "current") stage = "finish"
        }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Filled.Lock,
        text = {
            when (stage) {
                "current" -> {
                    Text(
                        "Enter your current PIN to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(GapGroup))
                    PinField(
                        value = currentPin,
                        onValueChange = { currentPin = it; rejected = false },
                        placeholder = "Current PIN",
                        onDone = {
                            if (currentPin.length >= 4) {
                                haptics?.click()
                                vm.verifyAppPin(currentPin)
                            }
                        },
                        isError = rejected,
                        supportingText = if (rejected) {
                            {
                                Text(
                                    "Incorrect PIN. Wrong attempts count toward a lockout.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "finish" -> {
                    when (mode) {
                        "set" -> {
                            Text(
                                "Choose a new 4-8 digit PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(GapGroup))
                            OnboardingPinForm(
                                existing = state.appPinSet,
                                onSet = { pin -> vm.setAppPin(pin) },
                            )
                        }
                        else -> {
                            Text(
                                if (canBio)
                                    "Removing the PIN leaves biometrics as the only way to lock the app."
                                else
                                    "This device has no biometrics, so removing the PIN means the app can never lock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(GapSection))
                        }
                    }
                }
            }
        },
        buttons = {
            when (stage) {
                "current" -> ExpressiveButtonRow(
                    // Same conversion as the "finish" pair below, and for the same reason:
                    // Modifier.weight is a Row's parent data and means nothing to this group.
                    modifier = Modifier.fillMaxWidth(),
                    spacing = GapRow,
                    equalWidths = true,
                ) {
                    MorphTextButton("Cancel", onDismiss, emphasis = ButtonEmphasis.Deny)
                    // MorphTextButton, not a hand-rolled MorphButton{Text(...)} -- that Text had no
                    // `style`, so it rendered at a different size than "Cancel" right beside it in
                    // this same row, confirmed by audit.
                    MorphTextButton(
                        "Continue",
                        onClick = {
                            haptics?.click()
                            vm.verifyAppPin(currentPin)
                        },
                        enabled = currentPin.length >= 4,
                        emphasis = ButtonEmphasis.Confirm,
                    )
                }
                "finish" -> {
                    if (mode == "remove") {
                        // equalWidths on the group, NOT Modifier.weight on each child.
                        // RowScope.weight is parent data read by a Row's own measure policy; inside
                        // this group's policy it is not read at all, so it would have silently done
                        // nothing while the two halves went back to hugging their labels. See
                        // ExpressiveButtons.kt.
                        ExpressiveButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                            spacing = GapRow,
                            equalWidths = true,
                        ) {
                            MorphTextButton("Keep PIN", onDismiss, emphasis = ButtonEmphasis.Confirm)
                            // MorphTextButton, not a hand-rolled MorphButton{Text(...)} -- same
                            // missing-style issue as "Continue" above.
                            MorphTextButton(
                                "Remove PIN",
                                onClick = { haptics?.heavy(); vm.removeAppPin(); onDismiss() },
                                emphasis = ButtonEmphasis.Deny,
                            )
                        }
                    }
                    Spacer(Modifier.height(GapRow))
                    MorphTextButton(
                        "Done",
                        onClick = { haptics?.click(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = ButtonEmphasis.Confirm,
                    )
                }
            }
        },
    )
}
