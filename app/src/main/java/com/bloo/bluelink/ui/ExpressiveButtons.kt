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

/** The least of its resting width a squeezed neighbour keeps, so it compresses rather than
 *  collapsing -- a button that shrank to nothing would read as the group losing a member. It is
 *  also the cap on how much a pressed button can actually take: growth is limited by what the
 *  others can spare, never by letting the row grow. */
private const val MinDonorFraction = 0.72f

/** Damping for the press spring. High enough not to ring: see expressivePressFraction. */
private const val PressDamping = 0.88f

@Composable
private fun expressivePressFraction(interactionSource: InteractionSource, enabled: Boolean): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        // Barely-bouncy and quick, because this fraction drives real WIDTH. A bouncy, slow
        // spring is the right feel for something that only paints -- it was the original
        // graphicsLayer scale -- but on width every overshoot frame re-measures the row and
        // drags the neighbours back and forth with it, which reads as wobble rather than as
        // life. The press still springs; it just does not ring.
        animationSpec = spring(
            dampingRatio = PressDamping,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "expressivePress",
    )
}

/**
 * Press feedback for a standalone button: it is re-measured ~15% wider and springs back, so the
 * pill genuinely grows and anything beside it in a Row is pushed along.
 *
 * The press fraction is read inside the measure block, so a press invalidates LAYOUT only --
 * composition never re-runs for the animation, which is what keeps this affordable at the ~80
 * call sites that use it.
 *
 * For a row of buttons that should share a fixed footprint and shove each other instead of
 * pushing the row wider, use [ExpressiveButtonRow]; these buttons detect it and join in.
 */
@Composable
fun SafeExpansiveButton(
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val press by expressivePressFraction(interactionSource, enabled)
    // Inside an [ExpressiveButtonRow]/[ExpressiveButtonGroup] this button joins the group, which
    // takes the extra width off its NEIGHBOURS so the row's own footprint never changes.
    if (LocalExpressiveGroup.current) {
        Box(modifier.then(ExpressiveGroupData { press }), propagateMinConstraints = true) {
            content()
        }
        return
    }
    // On its own, it grows for real: the button is re-measured at a wider width, so the pill
    // itself gets wider and whatever sits next to it in a Row is pushed aside. That IS the
    // requested effect, and it is what a graphicsLayer scale could never deliver -- a scale
    // stretches the pixels of a button whose measured size never changed, so nothing moves and
    // the label distorts. MorphButtonCore already applies its own press scale internally, so the
    // old wrapper was in any case only ever adding a second scale on top of one.
    //
    // The earlier objection to real growth was that a size change inside a Settings lazy item
    // crashes the grid. That reasoning came from the reported Logs-card crash -- which turns out
    // to be DebugSettingsPanel's unbounded LazyColumn one card further down, not a size change at
    // all. Lazy layouts remeasure on content size changes constantly (every AnimatedVisibility in
    // these same cards does it); there was never anything here to be afraid of.
    val naturals = remember { NaturalWidths() }
    Layout(
        content = { content() },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(0, 0) {}
            // Read at LAYOUT time, so a press invalidates layout only and never composition.
            val p = press
            val cached = naturals.widths?.firstOrNull() ?: 0
            // One measure per child per pass, never two: measuring the same Measurable twice in
            // a single pass throws, so the resting width is CACHED on the resting passes and the
            // pressed passes size themselves from that cache.
            // minWidth ZEROED for the resting measure. Measuring with the incoming constraints
            // meant that in any parent that forces a width -- a fillMaxWidth row, most of them --
            // the button was recorded at that forced width, and the pressed pass then computed
            // `cached * 1.15` and coerced it straight back down to maxWidth. Target == cached,
            // so the button never moved. That is why these still did not animate. A child that
            // genuinely wants the full width still gets it from its own fillMaxWidth; one that
            // does not now records its real natural size, which is what there is room to grow
            // from.
            val naturalConstraints = constraints.copy(minWidth = 0)
            val placeables = if (p <= 0.001f || cached <= 0) {
                measurables.map { it.measure(naturalConstraints) }
                    .also { naturals.widths = intArrayOf(it.maxOf { pl -> pl.width }) }
            } else {
                val target = (cached * (1f + ExpressivePressGrowth * p)).roundToInt()
                    .coerceAtMost(if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
                    .coerceAtLeast(0)
                measurables.map { it.measure(constraints.copy(minWidth = target, maxWidth = target)) }
            }
            // Stacked at the origin, like the Box this replaced: a handful of call sites emit
            // more than one root into this slot (a button with a label and an AnimatedVisibility
            // beside it), and measuring only the first would make the rest silently vanish.
            val w = placeables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val h = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(w, h) { placeables.forEach { it.place(0, 0) } }
        },
    )
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
    equalWidths: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    ExpressiveButtonGroup(
        modifier = modifier,
        spacing = spacing,
        verticalAlignment = verticalAlignment,
        equalWidths = equalWidths,
        horizontalAlignment = horizontalAlignment,
    ) {
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
    /**
     * Give every member an equal share of the row instead of its natural width -- the Material 3
     * connected-group look, and what a `Row` of weighted children used to be asked for.
     *
     * It belongs here rather than at the call site because `Modifier.weight` cannot reach this
     * layout: weight is RowScope parent data, and a child of this group is not a child of a Row.
     * That is not hypothetical -- the cover action bar carried a `weight(1f)`, applied inside the
     * button's own content where the parent is the wrapper rather than the row, so it did nothing
     * at all and those buttons never filled the equal shares their own doc claimed.
     */
    equalWidths: Boolean = false,
    /**
     * Where the group sits when its content is narrower than the space it was given -- which
     * happens whenever it is asked to fill a width it does not need (a single button in a
     * full-width action bar).
     */
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
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
            // Intrinsics are asked against a real height where we have one; Infinity is not a
            // number a child can reason about.
            val intrinsicHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            val resting = press.all { it <= 0.001f }
            val cached = naturals.widths
            // Only MEMBER widths have to be sane: a legitimately zero-width non-member (an
            // empty Spacer, a label that renders nothing) must not disable the whole effect.
            val cachedFloors = naturals.floors
            val usable = cached != null && cached.size == n &&
                cachedFloors != null && cachedFloors.size == n &&
                (0 until n).all { !member[it] || cached[it] > 0 }

            // Equal shares are computed from the row itself, so there is nothing to cache and
            // no first-pass-at-rest requirement: the base width is the same on every pass and a
            // press redistributes from it exactly as it would from natural widths.
            val equalBase: IntArray? = if (equalWidths && constraints.hasBoundedWidth) {
                val members = (0 until n).count { member[it] }
                // TWO or more. "An equal share" of a row is meaningless for a single button, and
                // taking it literally is what turned a lone action into a pill spanning the whole
                // panel. One member falls through to its natural width instead, and
                // horizontalAlignment decides where it sits in the space left over.
                if (members > 1) {
                    val room = (constraints.maxWidth - gaps).coerceAtLeast(0)
                    val each = room / members
                    // The remainder goes to the first member rather than being dropped, so the
                    // group fills its row exactly instead of leaving up to n-1 pixels bare.
                    var extra = room - each * members
                    IntArray(n) { i ->
                        if (!member[i]) 0 else each + (if (extra > 0) { extra--; 1 } else 0)
                    }
                } else null
            } else null

            val placeables: List<Placeable> = if (equalBase != null) {
                val desired = FloatArray(n) {
                    if (member[it]) equalBase[it] * (1f + ExpressivePressGrowth * press[it]) else 0f
                }
                val total = equalBase.sum()
                val desiredTotal = desired.sum()
                val norm = if (desiredTotal > 0f) total / desiredTotal else 1f
                measurables.mapIndexed { i, m ->
                    if (!member[i]) {
                        m.measure(childConstraints)
                    } else {
                        val w = (desired[i] * norm).roundToInt().coerceAtLeast(0)
                        m.measure(childConstraints.copy(minWidth = w, maxWidth = w))
                    }
                }
            } else if (resting || !usable) {
                // At rest (and on the very first pass, which is always at rest since nothing
                // can be pressed before it exists): measure naturally and record those widths
                // as the budget every later pressed pass redistributes.
                measurables.map { it.measure(childConstraints) }.also { measured ->
                    naturals.widths = IntArray(n) { measured[it].width }
                    naturals.floors = IntArray(n) { i ->
                        if (member[i]) measurables[i].minIntrinsicWidth(intrinsicHeight) else 0
                    }
                }
            } else {
                // ONE invariant: the members' total width never changes. Everything else is
                // a redistribution inside that fixed budget -- a pressed button takes width,
                // the unpressed ones give exactly that much back, and the group's own footprint
                // is identical on every frame of the press and the release.
                //
                // Held as floats until the very last step. The previous version rounded each
                // button as it went and then patched the leftover onto whichever one happened
                // to be last, so a single pixel could hop between neighbours from frame to
                // frame while the spring ran -- which is the jank on the collapse: not the
                // motion, the rounding underneath it.
                val total = (0 until n).sumOf { if (member[it]) cached[it] else 0 }
                val growers = (0 until n).filter { member[it] && press[it] > 0.001f }
                val donors = (0 until n).filter { member[it] && press[it] <= 0.001f }

                // What the pressed buttons are asking for, and what the others can actually
                // spare above their floor. Whichever is smaller is what moves -- so with
                // nothing to take from (a lone button in the row) NOTHING moves, rather than
                // the row quietly growing to accommodate it.
                // What a donor may give up is bounded by its own CONTENT, not just by a
                // fraction. A fraction alone is why squeezed buttons wrapped their labels onto a
                // second line: at 28% off, any label wider than about 92dp no longer fits the
                // padding it had, and Text does the only thing it can. minIntrinsicWidth is the
                // width below which this child cannot lay out without breaking -- for a row of
                // icon + label, exactly the point the label would have to wrap -- so a donor
                // stops there and the pressed button simply grows by less.
                val floorOf = IntArray(n) { i ->
                    if (!member[i]) cached[i]
                    else maxOf(
                        (cached[i] * MinDonorFraction).roundToInt(),
                        cachedFloors[i],
                    ).coerceAtMost(cached[i])
                }
                val want = growers.sumOf { (cached[it] * ExpressivePressGrowth * press[it]).toDouble() }
                val capacity = donors.sumOf { (cached[it] - floorOf[it]).toDouble() }
                val give = minOf(want, capacity)

                val exact = DoubleArray(n) { cached[it].toDouble() }
                if (give > 0.0) {
                    for (i in growers) {
                        val share = (cached[i] * ExpressivePressGrowth * press[i]).toDouble() / want
                        exact[i] = cached[i] + give * share
                    }
                    for (i in donors) {
                        val share = (cached[i] - floorOf[i]).toDouble() / capacity
                        exact[i] = cached[i] - give * share
                    }
                }

                // Largest-remainder rounding across the members, so the integer widths sum to
                // `total` EXACTLY rather than approximately. Without this the group breathes by
                // a pixel or two as the spring runs, which on a connected pill is visible as a
                // seam that will not sit still.
                val target = IntArray(n) { if (member[it]) exact[it].toInt() else cached[it] }
                var slack = total - (0 until n).sumOf { if (member[it]) target[it] else 0 }
                if (slack != 0) {
                    val order = (0 until n).filter { member[it] }
                        .sortedByDescending { exact[it] - exact[it].toInt() }
                    // Bounded by construction, not by trusting the arithmetic: truncation can
                    // only ever leave slack >= 0 and smaller than the member count, but this
                    // runs inside a measure pass, and a measure pass that can spin is a frozen
                    // app rather than a wrong pixel.
                    var k = 0
                    while (slack > 0 && k < order.size) {
                        target[order[k]]++; slack--; k++
                    }
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
                val content = placeables.sumOf { it.width } + gaps
                var x = horizontalAlignment.align(content, width, layoutDirection)
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

    /**
     * The per-child intrinsic floor, recorded on the same resting pass as [widths].
     *
     * Cached for cost, not for correctness: minIntrinsicWidth measures the child's subtree, and
     * asking for it inside the measure block meant paying for every member on every frame of
     * the press spring. It depends only on the child's content -- the same thing [widths]
     * already assumes holds still between resting passes -- so the resting pass is the right
     * and only place to ask.
     */
    var floors: IntArray? = null
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
