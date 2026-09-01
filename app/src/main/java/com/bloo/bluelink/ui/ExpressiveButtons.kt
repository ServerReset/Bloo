package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Material 3 Expressive press feedback, in two flavours that exist for one reason: a press
 * effect that changes real LAYOUT size is only safe when something contains that size change.
 *
 * - [SafeExpansiveButton] -- the general one, used by ~80 call sites all over the app. Paint
 *   only: it scales what is DRAWN and never touches layout, so it is safe absolutely anywhere,
 *   including inside a `LazyVerticalStaggeredGrid` item (which every Settings card is).
 * - [ExpressiveButtonGroup] -- for buttons that sit side by side and should genuinely shove
 *   each other around on press. Real widths, but redistributed WITHIN the group, whose own
 *   outer footprint never changes.
 *
 * **Why the split, in detail, because two earlier attempts got this wrong and broke the app:**
 *
 * Growing one button's real width pushes its neighbours only because the size change propagates
 * outward -- the Row remeasures, then its parent, and so on up. That is the whole point of the
 * effect and also exactly what makes it dangerous applied indiscriminately: on the Settings
 * screen these buttons live inside `LazyVerticalStaggeredGrid` items, and an item that changes
 * its own measured size during a scroll is a well-known way to crash a lazy staggered grid
 * (reported here as "the logs pebble crashes when scrolled over" -- the Logs card's
 * Copy/Clear/Show buttons are these). A second attempt cached the natural width in a
 * `mutableIntStateOf` and wrote it from inside the measure block; writing snapshot state during
 * the layout phase invalidates the layout that is currently running, which is what the reported
 * stutter was.
 *
 * So: the general wrapper does not change layout at all, and the buttons that actually have a
 * neighbour worth pushing opt into [ExpressiveButtonGroup], which keeps the size change bottled
 * up inside itself -- its children's widths are redistributed against each other so their total
 * is unchanged, and nothing outside the group ever sees a different size. Both use the same
 * spring, so the two read as one effect.
 */

/** 1.0 -> 1.15: the pressed button claims 15% more, per Material 3 Expressive. */
internal const val ExpressivePressGrowth = 0.15f

@Composable
private fun expressivePressFraction(interactionSource: InteractionSource, enabled: Boolean): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "expressivePress",
    )
}

/**
 * Press feedback for a button with no neighbour to push: the drawn button swells 15% and springs
 * back, via a draw-phase `graphicsLayer {}` lambda.
 *
 * The lambda form matters and is not a style preference. The non-lambda
 * `graphicsLayer(scaleX = value)` overload reads the animation in COMPOSITION, so every one of
 * these recomposed on every frame of every press -- ~80 buttons' worth of composition churn for
 * an effect that only ever needed to redraw. Read inside the lambda, the animation invalidates
 * draw and nothing else.
 *
 * Layout is untouched, so this is safe in a lazy item, inside `AnimatedVisibility`, anywhere.
 * If you want the press to actually displace a neighbour, that is [ExpressiveButtonGroup].
 */
@Composable
fun SafeExpansiveButton(
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val press by expressivePressFraction(interactionSource, enabled)
    // Inside an [ExpressiveButtonRow]/[ExpressiveButtonGroup] this button joins the group and
    // takes REAL width from its neighbours, which is the effect that was asked for; on its own it
    // falls back to the paint-only scale, which is safe anywhere. Self-registering is what makes
    // adopting the real effect a one-line change at a call site (swap the `Row` for an
    // `ExpressiveButtonRow`) instead of rewriting every button inside it.
    if (LocalExpressiveGroup.current) {
        Box(modifier.then(ExpressiveGroupData { press }), propagateMinConstraints = true) {
            content()
        }
        return
    }
    Box(
        modifier.graphicsLayer {
            val s = 1f + ExpressivePressGrowth * press
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin.Center
        },
    ) {
        content()
    }
}

/**
 * True inside an [ExpressiveButtonGroup], which is how [SafeExpansiveButton] knows to hand its
 * press fraction to the group's layout instead of scaling itself.
 */
internal val LocalExpressiveGroup = staticCompositionLocalOf { false }

/**
 * A drop-in replacement for `Row(horizontalArrangement = Arrangement.spacedBy(spacing))` whose
 * [SafeExpansiveButton] children shove each other aside on press, with the row's own width
 * unchanged.
 *
 * Children that are not buttons (a Spacer, a label) are left at their natural width and take no
 * part in the redistribution, so this is safe to drop onto a mixed row.
 */
@Composable
fun ExpressiveButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    ExpressiveButtonGroup(modifier = modifier, spacing = spacing, verticalAlignment = verticalAlignment) {
        content()
    }
}

/**
 * A row of buttons that shove each other aside on press: the pressed one takes ~15% more width
 * and its neighbours give exactly that much up, so the group's own width never changes by a
 * single pixel and no parent ever remeasures. This is the Material 3 Expressive button-group
 * press, and the same shape sameerasw/essentials' floating toolbar uses -- each item animating a
 * real width inside a container whose footprint is fixed.
 *
 * Children go through [ExpressiveButtonGroupScope.GroupButton], which is what carries a child's
 * live press fraction down to this layout (as parent data, read at LAYOUT time -- so a press
 * animates widths without recomposing anything).
 */
@Composable
fun ExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    spacing: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable ExpressiveButtonGroupScope.() -> Unit,
) {
    // Natural (unpressed) child widths, cached from the last resting measure pass. A plain
    // holder, deliberately NOT snapshot state: this is written from inside the measure block,
    // and writing snapshot state during layout invalidates the pass that is running (the
    // stutter an earlier version of this shipped). Nothing needs to react to it -- the very
    // next measure pass reads it directly.
    val naturals = remember { NaturalWidths() }
    Layout(
        content = {
            CompositionLocalProvider(LocalExpressiveGroup provides true) {
                ExpressiveButtonGroupScope.content()
            }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val n = measurables.size
            if (n == 0) return@Layout layout(0, 0) {}
            val gapPx = spacing.roundToPx()
            val gaps = gapPx * (n - 1)
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            // Press fractions come from parent data and are READ HERE, at layout time, so a
            // press invalidates layout only -- composition never re-runs for the animation.
            // Only children that carry ExpressiveGroupData are group MEMBERS. Anything else in
            // the row -- a Spacer, a label, a plain icon -- keeps its natural width and is left
            // out of the redistribution entirely, so dropping this in place of a Row cannot
            // squash the non-button content that happens to share it.
            val member = BooleanArray(n) { measurables[it].parentData is ExpressiveGroupData }
            val press = FloatArray(n) { i ->
                (measurables[i].parentData as? ExpressiveGroupData)?.pressFraction?.invoke() ?: 0f
            }
            val resting = press.all { it <= 0.001f }
            val cached = naturals.widths
            // Only MEMBER widths have to be sane: a legitimately zero-width non-member (an
            // empty Spacer, a label that renders nothing) must not disable the whole effect.
            val usable = cached != null && cached.size == n &&
                (0 until n).all { !member[it] || cached[it] > 0 }

            val placeables: List<Placeable> = if (resting || !usable) {
                // At rest (and on the very first pass, which is always at rest since nothing
                // can be pressed before it exists): measure naturally and record those widths
                // as the budget every later pressed pass redistributes.
                measurables.map { it.measure(childConstraints) }.also { measured ->
                    naturals.widths = IntArray(n) { measured[it].width }
                }
            } else {
                // Pressed: hand out the SAME total width as at rest, just shared differently --
                // pressed children ask for `growth` more, everyone is then normalised back down
                // to the original total, so the growth comes out of the neighbours and the
                // group's own width is bit-for-bit unchanged.
                // The budget is the MEMBERS' resting total; non-members are held at natural
                // width and neither give nor take.
                val total = (0 until n).sumOf { if (member[it]) cached[it] else 0 }
                val desired = FloatArray(n) {
                    if (member[it]) cached[it] * (1f + ExpressivePressGrowth * press[it]) else 0f
                }
                val desiredTotal = desired.sum()
                val norm = if (desiredTotal > 0f) total / desiredTotal else 1f
                val target = IntArray(n) {
                    if (member[it]) (desired[it] * norm).roundToInt().coerceAtLeast(0) else cached[it]
                }
                // Rounding drift lands on the widest MEMBER, where a pixel cannot be seen.
                val drift = total - (0 until n).sumOf { if (member[it]) target[it] else 0 }
                if (drift != 0) {
                    val widest = (0 until n).filter { member[it] }.maxByOrNull { target[it] }
                    if (widest != null) target[widest] = (target[widest] + drift).coerceAtLeast(0)
                }
                measurables.mapIndexed { i, m ->
                    val w = target[i].coerceAtMost(
                        if (constraints.hasBoundedWidth) constraints.maxWidth else target[i],
                    )
                    m.measure(childConstraints.copy(minWidth = w, maxWidth = w))
                }
            }

            val width = (placeables.sumOf { it.width } + gaps)
                .coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = (placeables.maxOfOrNull { it.height } ?: 0)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var x = 0
                placeables.forEach { p ->
                    p.placeRelative(x, verticalAlignment.align(p.height, height))
                    x += p.width + gapPx
                }
            }
        },
    )
}

/** Cache of resting child widths for one [ExpressiveButtonGroup]. See its own comment for why
 *  this is a plain object and not snapshot state. */
private class NaturalWidths {
    var widths: IntArray? = null
}

/** Carries a child's live press fraction to [ExpressiveButtonGroup]'s measure policy. The
 *  fraction is a lambda, not a value, so the group reads it during layout instead of the child
 *  having to recompose to report it. */
private data class ExpressiveGroupData(val pressFraction: () -> Float) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ExpressiveGroupData
}

/** Receiver for [ExpressiveButtonGroup]'s children. */
@Stable
object ExpressiveButtonGroupScope {
    /**
     * One button in the group. Its own press grows it and squeezes its neighbours; being
     * squeezed by a neighbour is what makes the effect read as buttons physically pushing each
     * other rather than each one popping in isolation.
     */
    @Composable
    fun GroupButton(
        interactionSource: InteractionSource,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val press by expressivePressFraction(interactionSource, enabled)
        Box(
            modifier
                // propagateMinConstraints (below) so the button itself fills the width the
                // group hands it -- otherwise it would sit at its own natural width inside a
                // slot growing and shrinking around it, and nothing would appear to move.
                .then(ExpressiveGroupData { press }),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
