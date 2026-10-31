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
import kotlinx.coroutines.flow.collectLatest

/*
 * The face's sizes: the numbers every piece of the face shares, scaled to the display.
 */

/** The ring's stroke and the gap between it and the quarters inside it. */
internal val RingStroke = 8.dp
internal val RingGap = 5.dp

/** The display the face's fixed sizes (ring, gaps, glyphs) were drawn for; every size scales by (real display / this). */
internal val ReferenceFace = 227.dp

/**
 * The face's size relative to the 227dp it was designed on, so a 192dp watch and a 240dp watch (and any display size setting, which changes
 * the dp the display has) get the same face, proportionally.
 */
internal val LocalFaceUnit = compositionLocalOf { 1f }

/** The press spring's damping: below 1 the quarter overshoots and settles, so a press has a bounce. Lower is bouncier. */
private const val PressBounce = 0.42f

/**
 * Which part of the face is held down, and how far its quarter has grown. [pressed] is a quarter (0 to 3), [HUB] for the hub, or [NONE].
 * [grow] eases from 0 to 1 while something is held, so the quarter swells out with a spring and settles back on release.
 */
@Stable
internal class QuadrantPress(internal val grow: Animatable<Float, AnimationVector1D>) {
    var pressed by mutableIntStateOf(NONE)
        private set

    fun press(quadrant: Int, down: Boolean) {
        if (down) pressed = quadrant else if (pressed == quadrant) pressed = NONE
    }

    companion object {
        const val NONE = -1
        const val HUB = 4
    }
}

/** The face's press state, with its grow spring running for it. */
@Composable
internal fun rememberQuadrantPress(): QuadrantPress {
    val policy = LocalPowerPolicy.current
    val state = remember { QuadrantPress(Animatable(0f)) }
    val bouncy = rememberUpdatedState(policy.bouncySprings)
    // `pressed` is read in a flow, not in composition, so a press grows the quarter without recomposing the face.
    LaunchedEffect(state) {
        snapshotFlow { state.pressed }.collectLatest { q ->
            val target = if (q == QuadrantPress.NONE) 0f else 1f
            val spec: AnimationSpec<Float> =
                if (bouncy.value) spring(dampingRatio = PressBounce, stiffness = Spring.StiffnessMediumLow) else snap()
            state.grow.animateTo(target, spec)
        }
    }
    return state
}
