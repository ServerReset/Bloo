package com.bloo.bluelink.ui

import androidx.compose.runtime.remember
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine

/**
 * Shared animation tokens and helpers: springs, expand/collapse transitions and the per-row
 * stagger.
 */

// ---- Motion -------------------------------------------------------------------

// Aliases onto :uicommon so the phone reads the shared motion tokens.
internal val SoftDamping get() = com.bloo.uicommon.SoftDamping

// The morph button's two corner states, aliased like SoftDamping above.
internal val PillCornerPercent get() = com.bloo.uicommon.PillCornerPercent

internal val MorphedCornerPercent get() = com.bloo.uicommon.MorphedCornerPercent

/**
 * The app's collapse/expand transition, supplied by the Material theme. M3 Expressive delivers
 * motion as a theme subsystem: MaterialTheme.motionScheme exposes six spec factories, a 2x3 matrix
 * of SPATIAL (bounds, size, scale, shape -- allowed to overshoot) against EFFECTS (colour, alpha --
 * must not) crossed with fast/default/slow.
 */
// internal: PebbleShell's corner-radius morph shares these springs so height and corners move as
// one.
/** The springs the app settles things with, named so the same feel is one value everywhere. */
internal const val SettleDamping = 0.7f
internal const val GentleDamping = 0.9f
internal const val PopDamping = 0.6f

internal val PebbleBounceDamping = 0.68f

// Slightly underdamped (0.95) for a smoother close that isn't stiff.
internal val PebbleCloseDamping = 0.95f

internal val PebbleBounceStiffness = Spring.StiffnessLow

/**
 * Standard enter animation for expanding surfaces: a fade-in. Nothing slides: the surface grows
 * (see [expandEnterSized]) and its rows pop in as it uncovers them ([StaggeredRevealColumn]).
 */
internal fun expandEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(tween(MotionShort))

/**
 * Standard exit animation for expanding surfaces: mirrors [expandEnter] in reverse. [fade] false
 * suits nested content with its own per-row fades (e.g. StaggeredRevealColumn).
 */
internal fun expandExit(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    if (fade) fadeOut(tween(MotionFast)) else ExitTransition.None

/** [expandEnter] plus real container-height growth via [expandVertically]. */
internal fun expandEnterSized(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    expandEnter(expandFrom) + expandVertically(expandFrom = expandFrom)

/**
 * The spring a pebble's body opens with. [StaggeredRevealColumn] runs its own progress on the SAME
 * spring, so a row pops at the moment the growing body reaches it.
 */
internal val PebbleOpenStiffness = Spring.StiffnessMediumLow
internal fun pebbleOpenSizeSpec(): FiniteAnimationSpec<IntSize> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PebbleOpenStiffness)

/** Mirror of [expandEnterSized]. */
internal fun expandExitSized(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    expandExit(shrinkTowards, fade) + shrinkVertically(shrinkTowards = shrinkTowards)

/**
 * Eases content in the first time it appears (fade, slide and real height growth), for things that
 * arrive WITHOUT a visibility flag of their own -- a card that is simply composed when its answer is
 * ready. One helper, so every arriving card uses the same motion as [expandEnterSized].
 */
@Composable
internal fun EnterOnce(
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Bottom,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = appear, modifier = modifier, enter = expandEnterSized(expandFrom), content = content)
}

/** Transition spec for [AnimatedContent] pairing [expandEnter] and [expandExit]. */
internal fun expandContentTransform(): ContentTransform =
    expandEnter() togetherWith expandExit()

/** Independent pop-in/pop-out for ONE row-level element that appears while its pebble is open. */
@Composable
internal fun PopVisible(
    visible: Boolean,
    modifier: Modifier = Modifier,
    /** True also interpolates the container's height, not just fade+scale in place. */
    sizeAnimated: Boolean = false,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleIn(
                lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
                initialScale = 0.8f,
            ) +
            (if (sizeAnimated) expandVertically(lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)) else EnterTransition.None),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec<Float>(), targetScale = 0.8f) +
            (if (sizeAnimated) shrinkVertically(lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)) else ExitTransition.None),
        content = content,
    )
}

/**
 * Lets [StaggeredRevealColumn] accept a `ColumnScope` content lambda without being a Column.
 * `weight`/`align`/`alignBy` are no-ops, as a real Column.weight would be in a wrap-content column.
 */
object NoOpColumnScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    // VerticalAlignmentLine: a Column aligns children by a line carrying an X offset; baselines
    // (HorizontalAlignmentLine) are a Row concept.
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/**
 * A gentle "back ease": rises past 1.0 near the end, then settles, like a spring without being one
 * (a real spring would be a third timed animation beside the shared [Transition] progress). The
 * overshoot stays small (the canonical 1.70158 is too showy for a row-sized element).
 */
fun pebbleRowOvershoot(t: Float): Float {
    val overshoot = 1.15f
    val c3 = overshoot + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + overshoot * x * x
}

/**
 * The app's one "working" spin, for any icon that turns while something loads: the first turn
 * accelerates from rest, then it holds a steady fast spin, and when [spinning] ends it decelerates
 * to the next full turn and resets. The Animatable only runs while spinning, so idle icons hold no
 * live animation.
 */
@androidx.compose.runtime.Composable
internal fun rememberSpinAngle(spinning: Boolean): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val angle = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(spinning) {
        if (spinning) {
            angle.animateTo(angle.value + 360f, androidx.compose.animation.core.tween(850, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            while (true) {
                angle.animateTo(angle.value + 360f, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing))
            }
        } else if (angle.value != 0f) {
            val target = kotlin.math.ceil(angle.value / 360f) * 360f
            angle.animateTo(target, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            angle.snapTo(0f)
        }
    }
    return angle
}
