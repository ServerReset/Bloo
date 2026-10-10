package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Stable holder returned by [rememberConfirmArm]: [armed] is the current arm
 * state and [arm] arms it. Callers use the pattern
 * `if (confirm.armed) doAction() else confirm.arm()`.
 */
data class ConfirmArm(val armed: Boolean, val arm: () -> Unit)

/**
 * Two-tap confirm gate. The first [ConfirmArm.arm] call arms the gate; a second
 * tap within [resetMillis] (while [ConfirmArm.armed] is true) is the confirmed
 * action. The gate auto-disarms after [resetMillis].
 */
@Composable
fun rememberConfirmArm(resetMillis: Long = 4000L): ConfirmArm {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(resetMillis)
            armed = false
        }
    }
    return ConfirmArm(armed = armed, arm = { armed = true })
}
