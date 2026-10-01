package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * Marks a control as the thing a sideways drag belongs to: the drag is consumed here, so the
 * pager that holds the page never sees it and does not flip. Everything WITHOUT this modifier
 * lets a horizontal swipe change pages, which is the point -- the app pages from anywhere, and
 * only buttons, sliders, toggles, segmented controls, scrubbers and text fields opt out, because
 * a finger that lands on one of those is aiming at it, not at the page.
 *
 * Put it OUTSIDE the control's own gesture handling: pointer events reach inner modifiers first,
 * so a control that claims the drag itself (a slider) still does, and this only takes what is
 * left. Vertical drags are untouched, so a scrollable parent still scrolls over the control.
 */
fun Modifier.blockPageSwipe(): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures { change, _ -> change.consume() }
}
