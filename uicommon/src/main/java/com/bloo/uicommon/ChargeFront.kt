package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.bloo.bluelink.data.BlooColors
import kotlinx.coroutines.delay

/** Loose enough to overshoot and settle; the rest keeps the loop a beat apart rather than constant motion. */
private const val FRONT_DAMPING = 0.45f
private const val FRONT_REST_MS = 350L

/**
 * The charging "fill up", shared by the phone's bar and the watch's ring: a piece grows out of the end of the fill on a bouncy
 * spring (overshoots, settles, rests a beat) and goes again, so a charging car visibly keeps filling. Read it in the draw phase
 * (it never needs a recomposition); each surface supplies only its own geometry. Rests at 0 while [on] is false.
 */
@Composable
fun rememberChargeFront(on: Boolean): State<Float> {
    val front = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (!on) { front.snapTo(0f); return@LaunchedEffect }
        while (true) {
            front.snapTo(0f)
            front.animateTo(1f, spring(dampingRatio = FRONT_DAMPING, stiffness = Spring.StiffnessLow))
            delay(FRONT_REST_MS)
        }
    }
    return front.asState()
}

/** The charge bar's two stops, as the phone's bar and the watch's ring both draw them: darker where the fill starts, lighter at its end. */
@androidx.compose.runtime.Immutable
class ChargeFillColors(private val dark: State<Color>, private val light: State<Color>) {
    val fillDark: Color get() = dark.value
    val fillLight: Color get() = light.value
}

/**
 * The fill's colours: green while it charges toward the limit, blue once it has reached it ([stuck]). They are animated with
 * [spec] so the change is a transition rather than a colour popping mid-draw; each surface passes its own feel.
 */
@Composable
fun rememberChargeFillColors(stuck: Boolean, spec: AnimationSpec<Color>): ChargeFillColors {
    val dark = animateColorAsState(
        Color(if (stuck) BlooColors.chargeBlueDark else BlooColors.chargeGreenDark), spec, label = "chargeFillDark",
    )
    val light = animateColorAsState(
        Color(if (stuck) BlooColors.chargeBlue else BlooColors.chargeGreen), spec, label = "chargeFillLight",
    )
    return remember(dark, light) { ChargeFillColors(dark, light) }
}
