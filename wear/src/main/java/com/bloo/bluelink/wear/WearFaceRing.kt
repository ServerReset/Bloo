package com.bloo.bluelink.wear

import com.bloo.uicommon.LocalPowerPolicy
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * The charge or fuel as a ring that hugs the display's edge: a soft track all the way round, and the level
 * from 12 o'clock clockwise. Springs to a new level; under the watch's lean mode it jumps.
 */
@Composable
fun WearEdgeRing(
    progress: Float?,
    tint: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = RingStroke,
    /** A short light travels along the filled arc while charging (not under the watch's lean mode). */
    charging: Boolean = false,
) {
    val policy = LocalPowerPolicy.current
    val sheenOn = charging && policy.ambientMotion && progress != null
    val sheen = if (sheenOn) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "chargeSheen").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)),
            label = "chargeSheenPhase",
        )
    } else {
        null
    }
    val sweep by animateFloatAsState(
        (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = if (policy.bouncySprings) spring() else snap(),
        label = "edgeRing",
    )
    val track = tint.copy(alpha = 0.18f)
    Box(
        modifier.drawBehind {
            val w = stroke.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            if (progress != null && sweep > 0f) {
                drawArc(tint, -90f, 360f * sweep, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                // Read in the draw phase only: the sheen repaints the ring, nothing recomposes.
                sheen?.value?.let { phase ->
                    val span = minOf(28f, 360f * sweep)
                    val start = -90f + (360f * sweep - span) * phase
                    drawArc(Color.White.copy(alpha = 0.45f), start, span, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
        },
    )
}
