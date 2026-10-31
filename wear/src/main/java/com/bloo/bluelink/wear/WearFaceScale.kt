package com.bloo.bluelink.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.LocalPowerPolicy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*
 * The face's sizes: the numbers every piece of the face shares, scaled to the display.
 */

/** The ring's stroke and the gap between it and the tiles inside it. */
internal val RingStroke = 8.dp
internal val RingGap = 5.dp

/** The gap between the four quarter tiles. */
internal val TileGap = 3.dp

/** The background-coloured halo round the hub, which the tiles curve round. */
internal val HubHalo = 5.dp

/** The hub's share of the inner circle's diameter. */
internal const val HubFraction = 0.34f

/** The display the face's fixed sizes (ring, gaps, glyphs) were drawn for; every size scales by (real display / this). */
internal val ReferenceFace = 227.dp

/**
 * The face's size relative to the 227dp it was designed on, so a 192dp watch and a 240dp watch (and any display size setting, which changes
 * the dp the display has) get the same face, proportionally.
 */
internal val LocalFaceUnit = compositionLocalOf { 1f }

/** How far the cross through the face moves when a quarter is pressed, as a share of the face: the pressed quarter grows by it. */
private const val PressGrow = 0.07f

/** The press spring's damping: below 1 the cross overshoots and settles, so a press has a bounce. Lower is bouncier. */
private const val PressBounce = 0.42f

/**
 * Which quarter is held down, and the cross through the middle of the face that moves because of it: the pressed quarter grows and the
 * other three give way, and the hub (which sits on the cross) moves with it. The cross is two springs, one per axis; they are read where the
 * face is laid out, so a press re-lays-out the tiles without recomposing them.
 */
@Stable
internal class QuadrantPress(
    internal val x: Animatable<Float, AnimationVector1D>,
    internal val y: Animatable<Float, AnimationVector1D>,
) {
    var pressed by mutableIntStateOf(NONE)
        private set

    /** The cross's offset from the middle, as a share of the face's width and height. */
    val dx: Float get() = x.value
    val dy: Float get() = y.value

    fun press(quadrant: Int, down: Boolean) {
        if (down) pressed = quadrant else if (pressed == quadrant) pressed = NONE
    }

    companion object { const val NONE = -1 }
}

/** The face's press state, with the cross's springs running for it. */
@Composable
internal fun rememberQuadrantPress(): QuadrantPress {
    val policy = LocalPowerPolicy.current
    val state = remember { QuadrantPress(Animatable(0f), Animatable(0f)) }
    val bouncy = rememberUpdatedState(policy.bouncySprings)
    // `pressed` is read in a flow, not in composition, so a press moves the cross without recomposing the face.
    LaunchedEffect(state) {
        snapshotFlow { state.pressed }.collectLatest { q ->
            // The pressed quarter's own corner stays put; the cross moves away from it, which is what makes it bigger.
            val tx = when (q) { 0, 2 -> PressGrow; 1, 3 -> -PressGrow; else -> 0f }
            val ty = when (q) { 0, 1 -> PressGrow; 2, 3 -> -PressGrow; else -> 0f }
            val spec: AnimationSpec<Float> =
                if (bouncy.value) spring(dampingRatio = PressBounce, stiffness = Spring.StiffnessMediumLow) else snap()
            coroutineScope {
                launch { state.x.animateTo(tx, spec) }
                launch { state.y.animateTo(ty, spec) }
            }
        }
    }
    return state
}
