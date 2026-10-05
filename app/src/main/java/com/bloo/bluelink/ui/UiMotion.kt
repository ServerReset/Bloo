
package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
// `motionScheme` is a MaterialTheme member, so it needs no import.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
// Explicit import required: State<T>'s `by` delegate resolves to this file-scope operator extension.
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine

/** Shared animation tokens and helpers: springs, expand/collapse transitions and the per-row stagger. */

// ---- Motion -------------------------------------------------------------------

// Aliases onto :uicommon so the phone reads the shared motion tokens.
internal val SoftDamping get() = com.bloo.uicommon.SoftDamping

// The morph button's two corner states, aliased like SoftDamping above.
internal val PillCornerPercent get() = com.bloo.uicommon.PillCornerPercent

internal val MorphedCornerPercent get() = com.bloo.uicommon.MorphedCornerPercent

/**
 * The app's collapse/expand transition, supplied by the Material theme.
 *
 * Height is spatial and the fade is effects, per the M3 Expressive split; both are springs
 * because a collapse toggle is re-tappable and springs preserve velocity on interruption.
 *
 * [expandFrom] is a layout fact, not a timing one: a body under a header grows from its top,
 * a bubble above the bottom bar reveals from its bottom.
 *
 * AnimatedVisibility + shrinkVertically is used instead of animateContentSize because it
 * measures the child once at unchanged constraints (content is sliced, not reflowed) and removes
 * the node from composition at the end, so it is not left transparent and reachable by TalkBack.
 *
 * Do not simplify either token to fade-only: fadeOut has a null changeSize, so the child reports
 * its full size every frame and then drops in a single frame. scaleIn/scaleOut keep the full
 * footprint and do not help.
 *
 * AnimatedVisibility only waits for animations in its own Transition, so an independent
 * animate*AsState (such as heroT) can be cut off before it finishes.
 *
 * The open half uses a dedicated bounce spring ([PebbleBounceDamping]): one spring tuned to
 * overshoot keeps a single source of truth for the card's bounds. The close half is critically
 * damped ([PebbleCloseDamping] = 1.0) because there is nothing past "gone" for an overshoot to
 * mean; it shares [PebbleBounceStiffness] so the timing still reads as one card.
 *
 * Overshoot is set by damping ratio alone; stiffness only changes speed.
 */
// internal: PebbleShell's corner-radius morph shares these springs so height and corners move as one.
internal val PebbleBounceDamping = 0.68f

// Slightly underdamped (0.95) for a smoother close that isn't stiff.
internal val PebbleCloseDamping = 0.95f

internal val PebbleBounceStiffness = Spring.StiffnessLow

/** Standard enter animation for expanding surfaces: a fade-in plus a slide from the direction of expansion. */
internal fun expandEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(tween(MotionShort)) + slideInVertically {
        if (expandFrom == Alignment.Top) -it / 3 else it / 3
    }

/**
 * Standard exit animation for expanding surfaces: mirrors [expandEnter] in reverse.
 *
 * [fade] false suits nested content with its own per-row fades (e.g. StaggeredRevealColumn).
 */
internal fun expandExit(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    (if (fade) fadeOut(tween(MotionFast)) else ExitTransition.None) + slideOutVertically {
        if (shrinkTowards == Alignment.Top) -it / 3 else it / 3
    }

/**
 * [expandEnter] plus real container-height growth via [expandVertically].
 *
 * For plain `AnimatedVisibility` disclosure sites inline in a Column; without a size transition
 * the layout snaps to the revealed height and, on exit, holds full height until the end.
 * Not folded into [expandEnter]/[expandExit] because AnimatedContent owns its own size
 * interpolation via `sizeTransform`. Use only with `AnimatedVisibility`'s `enter=`/`exit=`.
 */
internal fun expandEnterSized(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    expandEnter(expandFrom) + expandVertically(expandFrom = expandFrom)

/** Mirror of [expandEnterSized]. */
internal fun expandExitSized(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    expandExit(shrinkTowards, fade) + shrinkVertically(shrinkTowards = shrinkTowards)

/** Transition spec for [AnimatedContent] pairing [expandEnter] and [expandExit]. */
internal fun expandContentTransform(): ContentTransform =
    expandEnter() togetherWith expandExit()

/**
 * Independent pop-in/pop-out for ONE row-level element that appears while its pebble is open.
 *
 * Each call site has its own [AnimatedVisibility], so rows never wait on or share alpha with
 * each other (unlike [collapseEnter]/[collapseExit], which animate the body as one block).
 *
 * Scale-and-fade, not height-based: the pebble body already animates its size, and a second
 * height animation would double-animate and stutter. Scale uses [PebbleBounceDamping] to match
 * the card's open bounce.
 */
@Composable
internal fun PopVisible(
    visible: Boolean,
    modifier: Modifier = Modifier,
    /**
     * True also interpolates the container's height, not just fade+scale in place.
     *
     * Use it where the container reflows siblings when this content's height changes (e.g. the
     * reorderable list, whose siblings animate via `Modifier.animatePlacement()`); otherwise the
     * two visibly overlap while the sibling is still mid-spring.
     */
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
 * (a real spring would be a third timed animation beside the shared [Transition] progress).
 * [overshoot] stays small (the canonical 1.70158 is too showy for a row-sized element).
 */
fun pebbleRowOvershoot(t: Float, overshoot: Float = 1.15f): Float {
    val c3 = overshoot + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + overshoot * x * x
}

/**
 * How much of the shared progress each row's stagger window is offset by, end to end (see
 * [StaggeredRevealColumn]). 0.85 gives each row a narrow 15% window so up to 5 rows never
 * overlap and each reads as a distinct step.
 */
const val PebbleStaggerSpan = 0.85f

/**
 * The app's one "working" spin for icons that turn while something loads: accelerates from rest,
 * holds a fast spin, and when [spinning] ends decelerates to the next full turn and resets.
 * Idle icons hold no live animation; apply in a layer (`graphicsLayer { rotationZ = angle.value }`).
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
