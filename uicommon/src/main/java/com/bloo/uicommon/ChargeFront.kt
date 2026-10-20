package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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
