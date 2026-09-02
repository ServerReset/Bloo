package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * A group member's resting width is its content plus [ExpressivePressGrowth] again.
 *
 * This is the whole basis of the effect, so it is worth stating plainly: a button that hugs its
 * content has NO slack. Its width IS its label plus its padding, so a neighbour pressing beside
 * it has nothing to give. Earlier rules tried to find slack inside that width -- shrink to the
 * minimum intrinsic width (the longest word, so labels ellipsized, since a button label is one
 * line that never wraps), or shrink into the padding (nothing at all for a button with tight
 * padding, and nothing for a fixed-size icon).
 *
 * So the slack is not found, it is RESERVED: a member rests one growth-step wider than it needs,
 * and squeezing it returns it to exactly the width its content asked for. Nothing can truncate,
 * nothing can wrap to a second line, and every button has capacity regardless of what it holds.
 *
 * Deliberately expressed as the growth constant rather than a number of its own -- the reserve
 * and the growth are the same quantity seen from either end, and they can only stay in step if
 * there is one of them.
 */
internal const val ExpressiveRestingScale = 1f + ExpressivePressGrowth

/** Damping for the press spring. High enough not to ring: see expressivePressFraction. */
private const val PressDamping = 0.88f

/**
 * 0 at rest, 1 fully pushed. Driven by the interaction stream rather than by a held/not-held
 * boolean, because a TAP is the common case and a boolean cannot express one.
 *
 * With `targetValue = if (pressed) 1 else 0`, a quick tap flips the target to 1 and back within
 * a few milliseconds, so the spring is already heading home before it has travelled anywhere:
 * you press a button and almost nothing happens, which is the reported "it only does it when I
 * hold it". Here a press runs the push to completion FIRST and only then lets the release run
 * it back -- the collector suspends while each leg animates, so the interactions queue behind
 * it. Tap and hold therefore differ only in how long the button dwells at full push, which is
 * what a physical button does.
 */
@Composable
private fun expressivePressFraction(interactionSource: InteractionSource, enabled: Boolean): State<Float> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) {
            anim.snapTo(0f)
            return@LaunchedEffect
        }
        // Barely-bouncy and quick, because this fraction drives real WIDTH. A bouncy, slow
        // spring is the right feel for something that only paints -- it was the original
        // graphicsLayer scale -- but on width every overshoot frame re-measures the row and
        // drags the neighbours back and forth with it, which reads as wobble rather than as
        // life. The push still springs; it just does not ring.
        val spec = spring<Float>(dampingRatio = PressDamping, stiffness = Spring.StiffnessMedium)
        // A press can be released before its own Release event is even collected (that is the
        // whole point above), so held-ness is tracked by identity rather than by a count that
        // a cancelled gesture could leave unbalanced.
        val held = mutableSetOf<PressInteraction.Press>()
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> held.add(interaction)
                is PressInteraction.Release -> held.remove(interaction.press)
                is PressInteraction.Cancel -> held.remove(interaction.press)
                else -> return@collect
            }
            if (held.isNotEmpty()) anim.animateTo(1f, spec) else anim.animateTo(0f, spec)
        }
    }
    return anim.asState()
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
    /** See [ExpressiveGroupData.weight]. Ignored outside a group. */
    groupWeight: Float = 0f,
    content: @Composable () -> Unit,
) {
    val press by expressivePressFraction(interactionSource, enabled)
    // Inside an [ExpressiveButtonRow]/[ExpressiveButtonGroup] this button joins the group, which
    // takes the extra width off its NEIGHBOURS so the row's own footprint never changes.
    if (LocalExpressiveGroup.current) {
        Box(
            modifier.then(ExpressiveGroupData({ press }, groupWeight)),
            propagateMinConstraints = true,
        ) {
            // Providing FALSE inside makes joining a group idempotent. MorphButton now joins on
            // its own when it finds itself in one (that is what stopped whole rows of buttons
            // from ever animating), and without this every call site that already wraps its
            // button explicitly would wrap it a second time -- a redundant layout node and a
            // second press spring per button, whose parent data the group would not even read,
            // since it belongs to a Box inside this one rather than to the group itself.
            CompositionLocalProvider(LocalExpressiveGroup provides false) { content() }
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
    /** See [ExpressiveButtonGroup]'s own `lineSpacing` -- this wraps like a FlowRow. */
    lineSpacing: Dp = spacing,
    content: @Composable () -> Unit,
) {
    ExpressiveButtonGroup(
        modifier = modifier,
        spacing = spacing,
        verticalAlignment = verticalAlignment,
        equalWidths = equalWidths,
        horizontalAlignment = horizontalAlignment,
        lineSpacing = lineSpacing,
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
    /**
     * Gap between wrapped lines.
     *
     * The group lays out in lines like a FlowRow, and redistributes within each line
     * independently, so it is a drop-in wherever a row of buttons might not fit on one line --
     * the car-info pebble's link buttons, the Weather card's pair. Those were FlowRows, which
     * meant a pressed button grew for real and simply shoved its neighbours along, since a
     * FlowRow has no notion of a shared budget. A group that does fit on one line takes the
     * same path with a single line and behaves exactly as it did.
     */
    lineSpacing: Dp = spacing,
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
            val lineGapPx = lineSpacing.roundToPx()
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
            // Which members absorb the line's leftover space. Parent data, so it costs nothing
            // per frame.
            val weight = FloatArray(n) { i ->
                (measurables[i].parentData as? ExpressiveGroupData)?.weight ?: 0f
            }
            val resting = press.all { it <= 0.001f }

            // Natural widths come from maxIntrinsicWidth rather than from a trial measure.
            // That is not a micro-optimisation, it is what makes filling possible at all: a
            // child may only be measured once per pass, so measuring to learn the natural width
            // leaves nothing with which to place the child at a different one. It also fixes a
            // bug the trial measure had -- inside a parent that forces a width, the "natural"
            // width recorded WAS that forced width, so the button had nothing to grow from.
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            val cached = naturals.widths
            // Both arrays are written together and read together, so both are checked.
            if (resting || cached == null || cached.size != n || naturals.content?.size != n) {
                val c = IntArray(n) { measurables[it].maxIntrinsicWidth(h).coerceAtLeast(0) }
                naturals.content = c
                // The reserve: a MEMBER rests one growth-step wider than its content needs, so
                // that being squeezed returns it to exactly `content` and never below. A
                // non-member is not part of the redistribution and keeps its own width.
                naturals.widths = IntArray(n) {
                    if (member[it]) (c[it] * ExpressiveRestingScale).roundToInt() else c[it]
                }
            }
            val nat = naturals.widths!!
            // What each member's content actually asked for -- its floor, and the width it lands
            // on when a neighbour takes everything it has to give.
            val content = naturals.content!!

            // Break into lines exactly as a FlowRow would, so this is a drop-in for one. A
            // group that fits on one line takes this path with a single line and behaves
            // exactly as before.
            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
            val lines = ArrayList<IntArray>()
            run {
                var cur = ArrayList<Int>()
                var used = 0
                for (i in 0 until n) {
                    val add = nat[i] + if (cur.isEmpty()) 0 else gapPx
                    if (cur.isNotEmpty() && used + add > maxW) {
                        lines.add(cur.toIntArray()); cur = ArrayList(); used = 0
                    }
                    cur.add(i)
                    used += if (cur.size == 1) nat[i] else add
                }
                if (cur.isNotEmpty()) lines.add(cur.toIntArray())
            }

            val out = arrayOfNulls<Placeable>(n)
            val lineWidth = IntArray(lines.size)
            val lineHeight = IntArray(lines.size)

            for ((li, idx) in lines.withIndex()) {
                val gapsHere = gapPx * (idx.size - 1)
                // Non-members on this line keep their natural size and are measured first, so
                // what remains is the members' budget.
                var nonMemberWidth = 0
                for (i in idx) if (!member[i]) {
                    val p = measurables[i].measure(childConstraints)
                    out[i] = p
                    nonMemberWidth += p.width
                }
                val memberIdx = idx.filter { member[it] }
                if (memberIdx.isEmpty()) {
                    lineWidth[li] = nonMemberWidth + gapsHere
                    lineHeight[li] = idx.maxOf { out[it]?.height ?: 0 }
                    continue
                }

                val naturalTotal = memberIdx.sumOf { nat[it] }
                val room = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth - gapsHere - nonMemberWidth).coerceAtLeast(0)
                } else {
                    naturalTotal
                }

                // Equal shares: the Material 3 connected-group look. TWO or more members --
                // "an equal share" of a line is meaningless for a single button, and taking it
                // literally is what turned a lone action into a pill spanning a whole panel.
                val base = DoubleArray(n)
                val total: Int
                if (equalWidths && constraints.hasBoundedWidth && memberIdx.size > 1) {
                    val each = room.toDouble() / memberIdx.size
                    for (i in memberIdx) base[i] = each
                    total = room
                } else {
                    // Members that declare a weight stretch to fill whatever the line leaves
                    // over. This is what lets a split pill span its row AND still redistribute
                    // on press: the label half carries the weight, the nub keeps its natural
                    // size, and the stretch is part of the budget rather than something a Row
                    // does outside it.
                    val wSum = memberIdx.sumOf { weight[it].toDouble() }
                    val leftover = (room - naturalTotal).coerceAtLeast(0)
                    val stretching = leftover > 0 && wSum > 0.0
                    for (i in memberIdx) base[i] = nat[i].toDouble()
                    if (stretching) {
                        for (i in memberIdx) {
                            if (weight[i] > 0f) base[i] += leftover * (weight[i] / wSum)
                        }
                    }
                    total = if (stretching) naturalTotal + leftover else naturalTotal
                }

                // ONE invariant: the members' total width on a line never changes. Everything
                // else is redistribution inside that budget -- a pressed button takes width,
                // the unpressed ones give exactly that much back, and the line's own footprint
                // is identical on every frame of the press and the release.
                val growers = memberIdx.filter { press[it] > 0.001f }
                val donors = memberIdx.filter { press[it] <= 0.001f }
                // A donor bottoms out at its own content width -- the reserve, and any stretch
                // it was handed above that, but never a pixel of the label or the glyph. So a
                // fully squeezed button is exactly the button you would have drawn with no
                // effect at all, and nothing can truncate or wrap to a second line.
                val floorOf = DoubleArray(n)
                for (i in memberIdx) {
                    floorOf[i] = content[i].toDouble().coerceAtMost(base[i])
                }
                // What the pressed buttons ask for, and what the others can actually spare.
                // Both sides are the same fraction of CONTENT, which is what makes the two
                // balance: a lone donor's reserve is exactly one grower's growth. Whichever is
                // smaller is what moves -- so with nothing to take from (a lone button on the
                // line) NOTHING moves, rather than the line quietly growing.
                // toDouble() explicitly: content is an IntArray, so Int * Float * Float is a
                // Float, and sumOf has no Float overload to resolve to.
                val want = growers.sumOf { content[it].toDouble() * ExpressivePressGrowth * press[it] }
                val capacity = donors.sumOf { base[it] - floorOf[it] }
                val give = minOf(want, capacity)

                val exact = DoubleArray(n)
                for (i in memberIdx) exact[i] = base[i]
                if (give > 0.0) {
                    for (i in growers) {
                        exact[i] = base[i] +
                            give * (content[i].toDouble() * ExpressivePressGrowth * press[i]) / want
                    }
                    for (i in donors) {
                        exact[i] = base[i] - give * (base[i] - floorOf[i]) / capacity
                    }
                }

                // Largest-remainder rounding, so the integer widths sum to `total` EXACTLY
                // rather than approximately. Without this the group breathes by a pixel or two
                // as the spring runs, which on a connected pill is a seam that will not sit
                // still.
                val target = IntArray(n)
                for (i in memberIdx) target[i] = exact[i].toInt()
                var remainder = total - memberIdx.sumOf { target[it] }
                if (remainder > 0) {
                    val order = memberIdx.sortedByDescending { exact[it] - exact[it].toInt() }
                    // Bounded by construction, not by trusting the arithmetic: truncation can
                    // only leave a remainder >= 0 smaller than the member count. But this runs
                    // inside a measure pass, and a measure pass that can spin is a frozen app
                    // rather than a wrong pixel.
                    var k = 0
                    while (remainder > 0 && k < order.size) {
                        target[order[k]]++; remainder--; k++
                    }
                }
                for (i in memberIdx) {
                    val w = target[i].coerceIn(0, maxW)
                    out[i] = measurables[i].measure(childConstraints.copy(minWidth = w, maxWidth = w))
                }
                lineWidth[li] = idx.sumOf { out[it]!!.width } + gapsHere
                lineHeight[li] = idx.maxOf { out[it]!!.height }
            }

            val width = (lineWidth.maxOrNull() ?: 0)
                .coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = (lineHeight.sum() + lineGapPx * (lines.size - 1).coerceAtLeast(0))
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                for ((li, idx) in lines.withIndex()) {
                    var x = horizontalAlignment.align(lineWidth[li], width, layoutDirection)
                    for (i in idx) {
                        val p = out[i]!!
                        p.placeRelative(x, y + verticalAlignment.align(p.height, lineHeight[li]))
                        x += p.width + gapPx
                    }
                    y += lineHeight[li] + lineGapPx
                }
            }
        },
    )
}

/** Cache of resting child widths for one [ExpressiveButtonGroup]. See its own comment for why
 *  this is a plain object and not snapshot state. */
private class NaturalWidths {
    /** Resting widths: a member's content plus the reserve. See [ExpressiveRestingScale]. */
    var widths: IntArray? = null

    /** What each child's content asked for, before the reserve -- the floor a donor stops at. */
    var content: IntArray? = null
}

/** Carries a child's live press fraction to [ExpressiveButtonGroup]'s measure policy. The
 *  fraction is a lambda, not a value, so the group reads it during layout instead of the child
 *  having to recompose to report it. */
private data class ExpressiveGroupData(
    val pressFraction: () -> Float,
    /**
     * Share of the row's leftover space this member takes, 0 to opt out.
     *
     * The group's own answer to Modifier.weight, which cannot reach it: weight is RowScope
     * parent data read by a Row's measure policy, and a child of this group is not a child of a
     * Row. Every attempt to size a group member with Modifier.weight in this app was therefore
     * silently dead. Declaring it here means filling the row and redistributing on press are
     * the same calculation, instead of a Row doing one and the group doing the other.
     */
    val weight: Float,
) : ParentDataModifier {
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
        /** See [ExpressiveGroupData.weight]. */
        groupWeight: Float = 0f,
        content: @Composable () -> Unit,
    ) {
        val press by expressivePressFraction(interactionSource, enabled)
        Box(
            modifier
                // propagateMinConstraints (below) so the button itself fills the width the
                // group hands it -- otherwise it would sit at its own natural width inside a
                // slot growing and shrinking around it, and nothing would appear to move.
                .then(ExpressiveGroupData({ press }, groupWeight)),
            propagateMinConstraints = true,
        ) {
            // FALSE inside, exactly as SafeExpansiveButton's own group branch does it: this
            // slot has already joined the group on the child's behalf, and without this the
            // MorphButton inside would join a second time (it does that itself now), adding a
            // redundant layout node and a second press spring per half whose parent data the
            // group would never read.
            CompositionLocalProvider(LocalExpressiveGroup provides false) { content() }
        }
    }
}
