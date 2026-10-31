package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * The watch face for one car: the charge (or fuel) as a thin ring round the display's edge, the car's four actions as the quarters of the
 * circle inside it, and the value in a hub at the middle. Flat throughout, so the edge carries nothing but the ring.
 */

/**
 * The whole face. Fills its square (on a round watch, the display); everything inside is sized from it, so the same face works on a 192dp
 * and a 240dp watch.
 */
@Composable
fun WearCarFace(
    value: String,
    caption: String,
    /** 0..1, or null for a car that reports no charge or fuel (the ring then shows its track only). */
    progress: Float?,
    /** The car's charge limit in percent, for the ring's mark; null when it has none. */
    limitPct: Int?,
    tint: Color,
    actions: List<WearQuickAction?>,
    modifier: Modifier = Modifier,
    /** The reading is old enough to doubt: the ring dims, so a glance does not trust it. */
    stale: Boolean = false,
    /** A short line that takes the caption's place for a moment ("Sending…", "Done", "Updated 2 h ago"). */
    status: FaceStatus? = null,
    /** What the car is doing, under the value in the centre (see [carStateLine]). */
    carState: String = "",
    /** Holding the centre (a long press, with a buzz) changes the four quarters. */
    onHubHold: (() -> Unit)? = null,
    /** The car's name, curved round the bottom of the face for as long as it is shown. */
    name: String = "",
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val unit = minOf(maxWidth, maxHeight) / ReferenceFace
        val press = rememberQuadrantPress()
        CompositionLocalProvider(LocalFaceUnit provides unit) {
            WearEdgeRing(
                progress = progress,
                limitPct = limitPct,
                tint = tint,
                stroke = RingStroke * unit,
                modifier = Modifier.fillMaxSize().alpha(if (stale) 0.5f else 1f),
            )
            // The quarters fill the circle inside the ring; a long press on the hub changes them.
            WearQuadrantGrid(
                actions = actions,
                modifier = Modifier.fillMaxSize(),
                press = press,
                onHold = onHubHold,
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WearFaceHub(
                    value = value, caption = caption, tint = tint,
                    size = (2f * HubRadius * unit).dp,
                    state = carState, status = status,
                    pressed = press.pressed == QuadrantPress.HUB,
                )
            }
            // Outside the quarters, so its arc sits on the ring's edge.
            CurvedName(name, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxSize())
        }
    }
}
