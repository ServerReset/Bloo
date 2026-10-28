package com.bloo.bluelink.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.uicommon.rememberChargeFillColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.bloo.uicommon.LocalPowerPolicy

/**
 * The charge or fuel as a ring that hugs the display's edge, drawn as the phone's charge bar is: separate, rounded segments with
 * a small gap between -- the fill up to the current charge (green, or blue once it has reached its limit), a track up to the
 * charge limit, and a dimmer one past it -- or a single track when the car has no limit. Springs to a new level, the limit
 * glides to a new one, the colour crosses from green to blue, and a light travels the fill while it charges; under the watch's
 * lean mode the motion stops and the ring jumps.
 */
@Composable
fun WearEdgeRing(
    progress: Float?,
    /** The charge limit in percent, or null for a car without one. */
    limitPct: Int?,
    modifier: Modifier = Modifier,
    stroke: Dp = RingStroke,
    /** Charging: a lighter piece keeps filling out of the end of the fill on a bouncy spring (not under the lean mode). */
    charging: Boolean = false,
    /** A refresh is running: a short light travels along the filled arc (not under the lean mode). */
    refreshing: Boolean = false,
) {
    val policy = LocalPowerPolicy.current
    val limit = limitPct?.takeIf { it in 1..99 }
    val frac = (progress ?: 0f).coerceIn(0f, 1f)
    val stuck = progress != null && limit != null && frac * 100f >= limit
    // A slight sheen over the charged part while the car charges, and the same for a refresh.
    val sheenOn = (charging || refreshing) && policy.ambientMotion && progress != null
    val sheenPhase = remember { Animatable(0f) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Only while the watch is in front of the wearer: a charging car leaves the face composed all night, and a sheen that outlived
    // the screen would redraw the whole ring 60 times a second for nobody.
    LaunchedEffect(sheenOn, lifecycleOwner) {
        if (!sheenOn) {
            sheenPhase.snapTo(0f)
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                sheenPhase.snapTo(0f)
                sheenPhase.animateTo(1f, tween(1000, easing = LinearEasing))
            }
        }
    }
    val sheen = if (sheenOn) sheenPhase.asState() else null
    // The level lands with a bounce (it overshoots the new charge and settles), as the phone's bar does.
    val sweep by animateFloatAsState(
        frac,
        animationSpec = if (policy.bouncySprings) spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "edgeRing",
    )
    // The phone's two stops of the fill: the darker end where it starts, the lighter where it ends.
    val fill = rememberChargeFillColors(stuck, if (policy.bouncySprings) tween(500) else snap())
    // The limit glides when it changes; the first value is where it starts, not an animation from nothing.
    val limitAnim = remember { Animatable(0f) }
    var limitSeen by remember { mutableStateOf(false) }
    LaunchedEffect(limit) {
        val target = (limit ?: return@LaunchedEffect) / 100f
        if (limitSeen && policy.bouncySprings) limitAnim.animateTo(target, tween(450)) else { limitSeen = true; limitAnim.snapTo(target) }
    }
    val track = Color.White.copy(alpha = 0.22f)
    val dim = Color.White.copy(alpha = 0.09f)
    Box(
        modifier.drawBehind {
            val w = stroke.toPx()
            val arc = Size(size.width - w, size.height - w)
            val topLeft = Offset(w / 2, w / 2)
            val radius = arc.width / 2f
            // The round caps stand out half a stroke past each arc's end, so a gap is that plus the visible space.
            val capDeg = Math.toDegrees((w / 2f / radius).toDouble()).toFloat()
            val half = capDeg + GapDegrees / 2f
            fun segment(from: Float, to: Float, color: Color? = null, brush: Brush? = null) {
                // Degrees from 12 o'clock, shrunk by half a gap at each end; a segment too short for its caps is dropped.
                val start = from + half
                val length = (to - from) - 2f * half
                if (length <= 0f) return
                if (brush != null) {
                    rotate(-90f, center) { drawArc(brush, start, length, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round)) }
                } else if (color != null) {
                    drawArc(color, start - 90f, length, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            val filled = 360f * sweep
            // A fill too short to show its own caps is still a dot, as on the phone's bar.
            val filledEnd = if (sweep > 0f) maxOf(filled, 2f * half + 1f) else 0f
            if (filledEnd > 0f) {
                // Dark where the fill starts, light where it ends: the gradient is as long as the fill, as on the phone's bar.
                val gradient = Brush.sweepGradient(
                    0f to fill.fillDark, (filledEnd / 360f).coerceIn(0.02f, 1f) to fill.fillLight, 1f to fill.fillLight,
                    center = center,
                )
                segment(0f, filledEnd, brush = gradient)
                // Read in the draw phase only: the light repaints the ring, nothing recomposes.
                sheen?.value?.let { phase ->
                    val span = minOf(26f, filledEnd - 2f * half)
                    if (span > 0f) {
                        val at = half + (filledEnd - 2f * half - span) * phase
                        drawArc(Color.White.copy(alpha = 0.45f), at - 90f, span, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
            }
            if (limit == null || stuck) {
                segment(filledEnd, 360f, color = if (limit != null) dim else track)
            } else {
                val limitAt = (360f * limitAnim.value).coerceIn(filledEnd, 360f)
                segment(filledEnd, limitAt, color = track)
                segment(limitAt, 360f, color = dim)
            }
        },
    )
}

/** The visible space between two segments of the ring, in degrees (the caps' own reach is added on top). */
private const val GapDegrees = 3.5f
