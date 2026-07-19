package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bloo.bluelink.data.BlooColors

/**
 * Maps a climate setpoint (default 62-82°F) to a blue -> green -> warm-red
 * accent, for the temperature slider's fill/label colour. Was defined
 * separately on phone and watch, each using a different one-off hex triple
 * that had drifted from [BlooColors]' own cool/tempMid/tempHot constants
 * (which turn out to be exactly the intended canonical values -- phone's
 * cool and watch's mid/warm had each drifted a stop away from them). The
 * watch's version also wasn't animated at all; both now spring the same way.
 */
@Composable
fun tempColor(tempF: Int, rangeStart: Float = 62f, rangeEnd: Float = 82f): Color {
    val t = ((tempF - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
    val cool = Color(BlooColors.cool)
    val mid = Color(BlooColors.tempMid)
    val warm = Color(BlooColors.tempHot)
    val target = if (t < 0.5f) lerp(cool, mid, t * 2f) else lerp(mid, warm, (t - 0.5f) * 2f)
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tempColor",
    )
    return animated
}
