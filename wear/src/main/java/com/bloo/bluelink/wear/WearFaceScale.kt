package com.bloo.bluelink.wear

import com.bloo.uicommon.LocalPowerPolicy
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * The face's sizes and its press-and-give: the numbers every piece of the face shares, scaled to the display, and the state of
 * which quarter is held down.
 */

/** The ring's stroke and the gap between it and the tiles inside it. */
internal val RingStroke = 8.dp
internal val RingGap = 5.dp

/** The gap between the four quarter tiles (the cross through the middle of the face). */
internal val TileGap = 6.dp

/** The hub's share of the inner circle's diameter. */
internal const val HubFraction = 0.36f

/** The display the face's fixed sizes (ring, gaps, glyphs) were drawn for; every size scales by (real display / this). */
internal val ReferenceFace = 227.dp

/** How far the cross through the face moves, as a share of the face, when a quarter is pressed (the quarter grows). */
private const val PressGrow = 0.11f

/**
 * The face's size relative to the 227dp it was designed on, so a 192dp watch and a 240dp watch (and any display
 * size setting, which changes the dp the display has) get the same face, proportionally.
 */
internal val LocalFaceUnit = androidx.compose.runtime.compositionLocalOf { 1f }

/**
 * Which quarter is held down, and the cross through the middle of the face that moves because of it: pressing a
 * quarter makes it grow and the other three give way, and the hub (which sits on the cross) moves with it -- the
 * same push-and-give of a row of buttons on the phone. The cross is two springs, one per axis; they are read where
 * the face is laid out, so a press re-lays-out the tiles without recomposing them.
 */
@androidx.compose.runtime.Stable
internal class QuadrantPress(
    internal val x: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    internal val y: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    var pressed by androidx.compose.runtime.mutableIntStateOf(NONE)
        private set

    /** The cross's offset from the middle, as a share of the face's width and height. */
    val dx: Float get() = x.value
    val dy: Float get() = y.value

    fun press(quadrant: Int, down: Boolean) {
        if (down) pressed = quadrant else if (pressed == quadrant) pressed = NONE
    }

    companion object { const val NONE = -1 }
}

@Composable
internal fun rememberQuadrantPress(): QuadrantPress {
    val policy = LocalPowerPolicy.current
    val state = androidx.compose.runtime.remember {
        QuadrantPress(androidx.compose.animation.core.Animatable(0f), androidx.compose.animation.core.Animatable(0f))
    }
    val q = state.pressed
    androidx.compose.runtime.LaunchedEffect(q, policy.bouncySprings) {
        // The pressed quarter's own corner stays put; the cross moves AWAY from it, which is what makes it bigger.
        val tx = when (q) { 0, 2 -> PressGrow; 1, 3 -> -PressGrow; else -> 0f }
        val ty = when (q) { 0, 1 -> PressGrow; 2, 3 -> -PressGrow; else -> 0f }
        val spec: androidx.compose.animation.core.AnimationSpec<Float> =
            if (policy.bouncySprings) spring(dampingRatio = 0.62f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium) else snap()
        kotlinx.coroutines.coroutineScope {
            launch { state.x.animateTo(tx, spec) }
            launch { state.y.animateTo(ty, spec) }
        }
    }
    return state
}
