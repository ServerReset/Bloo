package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.bloo.bluelink.data.BlooColors
import androidx.compose.runtime.Immutable

/** The charge bar's two stops, as the phone's bar and the watch's ring both draw them: darker where the fill starts, lighter at its end. */
@Immutable
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
