package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset

/*
 * The watch face for one car: the charge (or fuel) as a ring around the very edge of the display, and inside it the car's four
 * actions as quarter circles, with the value in a small hub where they meet. It is only the arrangement: the pieces are
 * [WearEdgeRing] (WearFaceRing), [WearQuadrantGrid] (WearFaceGrid) and [WearFaceHub] (WearFaceHub), sized by the shared
 * metrics in WearFaceScale, so a screen that wants only one of them can use just that piece.
 */

/**
 * The whole face. Fills its square (on a round watch, the display); everything inside is sized from it, so the
 * same face works on a 192dp and a 240dp watch.
 */
@Composable
fun WearCarFace(
    value: String,
    caption: String,
    /** 0..1, or null for a car that reports no charge or fuel (the ring then shows its track only). */
    progress: Float?,
    /** The car's charge limit in percent, for the ring's split; null when it has none. */
    limitPct: Int?,
    tint: Color,
    actions: List<WearQuickAction?>,
    modifier: Modifier = Modifier,
    /** Charging: a soft light travels along the filled part of the ring. */
    charging: Boolean = false,
    /** The reading is old enough to doubt: the ring dims, so a glance does not trust it. */
    stale: Boolean = false,
    /** A short line that takes the caption's place for a moment ("Sending…", "Done", "Updated 2 h ago"). */
    status: FaceStatus? = null,
    /** A refresh is in flight: the light travels the ring as it does while charging. */
    refreshing: Boolean = false,
    /** Pressing the centre (the app opens the face editor). Null leaves the hub as plain text. */
    /** What the car is doing, under the value in the centre (see [carStateLine]). */
    carState: String = "",
    /** Holding the centre (a long press, with a buzz) changes the four quarters. */
    onHubHold: (() -> Unit)? = null,
) {
    // The aurora, at watch scale: a soft wash of the level's colour behind the tiles, as the phone's
    // background washes behind its cards. Drawn once, no animation.
    BoxWithConstraints(modifier.wearAuroraWash(tint), contentAlignment = Alignment.Center) {
        val unit = minOf(maxWidth, maxHeight) / ReferenceFace
        val press = rememberQuadrantPress()
        CompositionLocalProvider(LocalFaceUnit provides unit) {
            WearEdgeRing(
                progress = progress, limitPct = limitPct,
                modifier = Modifier.fillMaxSize().alpha(if (stale) 0.5f else 1f), stroke = RingStroke * unit,
                charging = charging, refreshing = refreshing,
            )
            Box(Modifier.fillMaxSize().padding((RingStroke + RingGap) * unit), contentAlignment = Alignment.Center) {
                WearQuadrantGrid(actions = actions, modifier = Modifier.fillMaxSize(), press = press)
                // The labels run round the inside of the ring, each along its own quarter's outer edge.
                WearRingLabels(actions)
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val hubSize = maxWidth * HubFraction
                    // The hub sits where the cross is, so it moves with the quarter that was pressed.
                    Box(Modifier.offset { IntOffset((press.dx * w).roundToInt(), (press.dy * h).roundToInt()) }) {
                        WearFaceHub(value = value, caption = caption, tint = tint, size = hubSize, state = carState, status = status, onHold = onHubHold)
                    }
                }
            }
        }
    }
}
