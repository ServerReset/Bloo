package com.bloo.uicommon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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

/**
 * A label/value row laid out [Arrangement.SpaceBetween]: the [label] on the left
 * as a [BasicText] and the [value] on the right via [AnimatedValue]. Foundation
 * only — bake the desired color into [labelStyle] and [valueStyle].
 */
@Composable
fun LabelValueRow(
    label: String,
    labelStyle: TextStyle,
    value: String,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = label, style = labelStyle)
        AnimatedValue(value = value, style = valueStyle)
    }
}
