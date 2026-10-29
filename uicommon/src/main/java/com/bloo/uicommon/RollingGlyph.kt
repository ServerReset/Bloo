package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds

/**
 * A glyph that rolls when it changes, inside its own circle: a press pushes the current glyph out one way and the next one in from the
 * other side. [direction] +1 rolls it up (the plus), -1 rolls it down (the minus). A new [trigger] starts a roll, and the roll ramps and
 * lands with a little overshoot.
 */
@Composable
fun RollingGlyph(
    icon: ImageVector,
    trigger: Int,
    direction: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    // 1 at rest. A press restarts it from 0; the spring runs past 1 and settles back, which is the overshoot.
    val roll = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            roll.snapTo(0f)
            roll.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val d = direction.toFloat()
    Box(
        modifier.size(ButtonIconSize).clipToBounds().onSizeChanged { heightPx = it.height.toFloat() },
        contentAlignment = Alignment.Center,
    ) {
        // The glyph that is leaving: it goes the way the press pushes (up for the plus, down for the minus).
        Icon(
            icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(ButtonIconSize).graphicsLayer { translationY = -d * roll.value * heightPx },
        )
        // The glyph that is arriving: it comes in from the opposite side and settles into place.
        Icon(
            icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(ButtonIconSize).graphicsLayer { translationY = d * (1f - roll.value) * heightPx },
        )
    }
}
