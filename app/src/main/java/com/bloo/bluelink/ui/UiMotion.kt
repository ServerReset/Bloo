package com.bloo.bluelink.ui

import com.bloo.uicommon.lowPowerFloating
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.delay
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.LocalReduceMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.motionBlurWhenMoving
import com.bloo.uicommon.MorphedCornerPercent
import com.bloo.uicommon.PillCornerPercent
import com.bloo.uicommon.SoftDamping
import com.bloo.uicommon.content

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
internal const val GentleDamping = 0.9f

// Slightly underdamped (0.95) for a smoother close that isn't stiff.
internal val PebbleCloseDamping = 0.95f

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

/**
 * A value that changes and swaps in place: the old one folds away and the new one opens into its space, with the app's one expand
 * motion. Every text or small value that changes under the user's eye uses this rather than spelling the AnimatedContent out.
 */
@Composable
internal fun <T> AnimatedSwap(
    value: T,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = { expandContentTransform() },
        label = label,
        modifier = modifier,
        content = content,
    )
}

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
        modifier = modifier.motionBlurWhenMoving(),
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleIn(
                lowPowerFloating(),
                initialScale = 0.8f,
            ) +
            (if (sizeAnimated) expandVertically(lowPowerFloating()) else EnterTransition.None),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec<Float>(), targetScale = 0.8f) +
            (if (sizeAnimated) shrinkVertically(lowPowerFloating()) else ExitTransition.None),
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
 * Shows [content] with the app's one expand/collapse transition: it grows [from] that edge as it appears and shrinks back
 * toward it as it goes. Every show/hide of a block of controls uses this rather than spelling out the enter/exit pair.
 */
@Composable
internal fun ExpandVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    from: Alignment.Vertical = Alignment.Top,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(visible, modifier.motionBlurWhenMoving(), enter = expandEnterSized(from), exit = expandExitSized(from), content = content)
}

/**
 * A clock for a group of elements that pop in one after another: 0 to 1 over [durationMs], starting [delayMs] after it is first composed
 * (already 1 where motion is off). Hand it to [popIn] on each element; it is read in the draw phase, so it recomposes nothing.
 */
@Composable
internal fun rememberPopClock(delayMs: Int = 0, durationMs: Int = 900): State<Float> {
    val allowed = !LocalReduceMotion.current && !LocalPowerPolicy.current.lean
    val clock = remember { Animatable(if (allowed) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (allowed) {
            delay(delayMs.toLong())
            clock.animateTo(1f, tween(durationMs, easing = LinearEasing))
        }
    }
    return clock.asState()
}

/** The share of a [rememberPopClock] over which the elements' starts are spread; each then takes the rest of it to pop. */
private const val PopSpread = 0.6f

/** How far along its own pop (0 to 1) the element in [slot] of [slots] is when the group's clock reads [clock]: later slots start later and all finish with the clock. */
internal fun popProgress(clock: Float, slot: Int, slots: Int): Float {
    val start = if (slots <= 1) 0f else slot.toFloat() / slots * PopSpread
    return ((clock - start) / (1f - PopSpread)).coerceIn(0f, 1f)
}

/**
 * Pops this element in on its turn of [clock]: it fades up and grows from [from] with the pebble rows' small overshoot. [slot] (0 until
 * [slots]) is its place in the order, so a group is a ripple, one element after the next. [pivot] is where it grows from: the middle
 * for a panel, the start edge for a line of text.
 */
internal fun Modifier.popIn(
    clock: State<Float>,
    slot: Int,
    slots: Int,
    from: Float = 0.8f,
    pivot: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    val t = popProgress(clock.value, slot, slots)
    val scale = from + (1f - from) * pebbleRowOvershoot(t)
    alpha = t
    scaleX = scale
    scaleY = scale
    transformOrigin = pivot
}
