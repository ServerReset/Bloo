package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

/**
 * Draws a vertical scrim behind the content: fully [color] at the top fading to
 * transparent over [heightDp]. Foundation-only (no Material). The caller supplies
 * the color and the total height (including any status-bar inset) and positions
 * the scrim via its own [Box] alignment.
 */
fun Modifier.topFadeScrim(color: Color, heightDp: Dp): Modifier = this.drawBehind {
    val h = heightDp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color, Color.Transparent),
            startY = 0f,
            endY = h,
        ),
        size = Size(size.width, h),
    )
}

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

// LabelValueRow was removed. It had zero call sites -- not one, in any module, ever
// -- while sitting in the shared module presenting itself as the obvious base for
// the phone's and the watch's StatusRow. It would have been a bad one, and that is
// the actual reason for deleting it rather than wiring it up:
//
//  - It called AnimatedValue without reduceMotion, back when that had a default. So
//    adopting it would have spread the accessibility bug that default caused (see
//    AnimatedValue's KDoc) to both surfaces at once, including the watch, which was
//    getting it right.
//  - Arrangement.SpaceBetween with no weight, maxLines or ellipsis on either cell
//    is precisely the layout both real StatusRows were rewritten to STOP using; the
//    comments at Screens.kt's and Components.kt's StatusRow record what overflowed.
//
// Sharing these two is still worth doing, but it is a behaviour decision and not a
// mechanical lift: they currently disagree about the value cell (the phone
// deliberately fills with weight(1f) + CenterEnd, having documented
// weight(1f, fill = false) -- what the watch does -- as a bug it fixed), about
// vertical alignment (Top vs CenterVertically), and about where the label colour
// comes from (an alpha on inherited content colour vs an onSurfaceVariant role).
// Any shared core has to take all of that as parameters, in the theme-neutral style
// this module already uses for AnimatedValue and AnimatedSlider, since :uicommon
// has no Material dependency at all and cannot see either MaterialTheme.
