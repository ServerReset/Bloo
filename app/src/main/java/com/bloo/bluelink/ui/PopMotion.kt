package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.uicommon.bounceSpring
import com.bloo.uicommon.FloatingStiffness

/**
 * The app's one pop: a surface rises into place (a springy rise that overshoots a little and settles
 * softly), and leaves with a small anticipatory lift, then a smooth retreat back to where it came
 * from. Dialogs and toasts both ride it, so everything that appears over the app arrives and leaves
 * the same way.
 */
internal val PopEnterSpec = bounceSpring<Float>(stiffness = FloatingStiffness)

/** The exit's two beats: an anticipatory lift, then the retreat. Springs, not linear tweens, so the
 *  end of each eases in instead of stopping hard (the "stiff end"). */
private val PopLiftSpec = bounceSpring<Float>(stiffness = FloatingStiffness)
private val PopExitSpec = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/**
 * Pop progress for one surface: 0 = hidden at its origin, 1 = resting. Starts rising on first composition; once
 * [leaving] flips true it plays the exit and then calls [onGone] so the owner can drop it.
 */
@Composable
internal fun rememberPop(leaving: Boolean, onGone: () -> Unit): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(Unit) { progress.animateTo(1f, PopEnterSpec) }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        progress.animateTo(1.06f, PopLiftSpec)
        progress.animateTo(0f, PopExitSpec)
        gone()
    }
    return progress
}

/**
 * Draws a surface at pop [progress]: it travels from ([fromX], [fromY]) px away to its own place, grows from
 * [minScale] and fades in over the first stretch ([alphaGain] times faster than the travel). All read in the
 * layer block, so the animation never recomposes anything.
 */
internal fun Modifier.popGraphics(
    progress: () -> Float,
    fromX: () -> Float = { 0f },
    fromY: () -> Float = { 0f },
    minScale: Float = 0.94f,
    alphaGain: Float = 4f,
): Modifier = graphicsLayer {
    val p = progress()
    translationX = fromX() * (1f - p)
    translationY = fromY() * (1f - p)
    val s = minScale + (1f - minScale) * p.coerceIn(0f, 1f)
    scaleX = s
    scaleY = s
    alpha = (p * alphaGain).coerceIn(0f, 1f)
}
