package com.bloo.bluelink.ui

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    /**
     * Whether this group may break onto more than one line.
     *
     * True suits a set of independent buttons: when they cannot all fit, wrapping is kinder
     * than shrinking them. It is FALSE for anything that is visually one object -- a connected
     * segment group, a split pill, an action-plus-chevron header. Those read as a single
     * control with seams in it, and a seam that appears on the next line down is not a control
     * at all. They compact to their glyphs instead, which is what the fit rule is for.
     */
    wrap: Boolean = true,
    /**
     * For an equal-width row that has been compacted to icon-only (see `wrap`): keep the true
     * equal share rather than capping each member at its own glyph width, so the compacted
     * row still spans its full width with the icons spread evenly. The map's bottom toolbar
     * uses this -- it wants one full-width icon-only strip, and without this a compacted row
     * shrank to a small cluster instead.
     */
    stretchCompact: Boolean = false,
    content: @Composable ExpressiveButtonGroupScope.() -> Unit,
) {
    // Natural (unpressed) child widths, cached from the last resting measure pass. A plain
    // holder, deliberately NOT snapshot state: this is written from inside the measure block,
    // and writing snapshot state during layout invalidates the pass that is running (the
    // stutter an earlier version of this shipped). Nothing needs to react to it -- the very
    // next measure pass reads it directly.
    val naturals = remember { NaturalWidths() }
    // Invalidate the natural-width cache whenever the CONTENT this group composes re-runs.
    //
    // The members' intrinsic widths depend only on that content, never on the constraints of a
    // given measure pass -- but the measure block below used to recompute them on every RESTING
    // pass (`resting || ...`), and resting is true for essentially every pass that is not
    // mid-press. So every measure of every group in the app re-walked each member's intrinsic
    // width, and a button's intrinsic width runs a real text layout for its label (that is what
    // `MorphButtonLabel.maxIntrinsicWidth` is). Inside a lazy layout -- which re-measures on
    // every scroll and every content-size change -- that was a text layout per member per frame,
    // forever: the allocation that filled the heap (the OOM dump is dominated by
    // MeasuredParagraph/MeasuredText/TextPaint). SideEffect fires after every successful
    // (re)composition, which is exactly when the content could have changed.
    SideEffect {
        naturals.content = null
        naturals.compact = null
    }
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
            // Natural widths come from maxIntrinsicWidth rather than from a trial measure.
            // That is not a micro-optimisation, it is what makes filling possible at all: a
            // child may only be measured once per pass, so measuring to learn the natural width
            // leaves nothing with which to place the child at a different one. It also fixes a
            // bug the trial measure had -- inside a parent that forces a width, the "natural"
            // width recorded WAS that forced width, so the button had nothing to grow from.
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            if (naturals.content == null || naturals.content!!.size != n) {
                naturals.content = IntArray(n) { measurables[it].maxIntrinsicWidth(h).coerceAtLeast(0) }
                // Invalidated, not recomputed here -- see the lazy read below for why.
                naturals.compact = null
            }
            // What each child's content actually asked for -- the only intrinsic width this
            // group pays for by default.
            val full = naturals.content!!
            // Proportional-to-label members take spare room in proportion to their own natural
            // width, so a row of buttons fills edge to edge with the wider label getting the wider
            // button. A group with a single member is a lone button, which rests at its natural
            // width instead (see SafeExpansiveButton's fillOnPress) -- so it opts back out here.
            val memberCount = member.count { it }
            for (i in 0 until n) {
                if (weight[i] < 0f) weight[i] = if (memberCount > 1) full[i].toFloat().coerceAtLeast(1f) else 0f
            }
            // The icon-only fallback is genuinely lazy, not just cached: minIntrinsicWidth asks
            // a full intrinsic pass down each member's own subtree, and querying it for EVERY
            // member on EVERY resting pass -- which the first version of the fit rule did
            // unconditionally -- meant every group in the app paid that cost on first layout
            // whether or not any line was ever going to be tight enough to need it. On a normal-
            // width phone almost none are. It is computed at most once per resting generation,
            // the first time some line actually fails the full-width test below, and reused by
            // every later line and frame until the members themselves change.
            var compact: IntArray? = naturals.compact

            // Break into lines exactly as a FlowRow would, so this is a drop-in for one. A
            // group that fits on one line takes this path with a single line and behaves
            // exactly as before.
            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
            val lines = ArrayList<IntArray>()
            // True when the labels do not fit on one line but every member has a symbol it can shrink to,
            // and those symbols all fit together on one line.
            fun collapsesInsteadOfWrapping(): Boolean {
                if (n < 2 || maxW == Int.MAX_VALUE || member.any { !it }) return false
                if (full.sum() + gapPx * (n - 1) <= maxW) return false
                val c = compact ?: IntArray(n) { i -> measurables[i].minIntrinsicWidth(h).coerceIn(0, full[i]) }
                    .also { compact = it; naturals.compact = it }
                return (0 until n).all { c[it] < full[it] } && c.sum() + gapPx * (n - 1) <= maxW
            }
            if (!wrap || collapsesInsteadOfWrapping()) {
                // A row whose buttons all have a symbol never breaks into lines: when the labels do not
                // fit, every button drops to its symbol (the fit rule below) and the one line fills the
                // width. Wrapping is for buttons that have nothing to collapse to.
                lines.add(IntArray(n) { it })
            } else {
                val broken = balancedLineBreaks(full, gapPx, maxW)
                lines.addAll(broken)
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
                // A lone button on its line has nobody to share with: it rests at its own width
                // (aligned to the start) and only fills the line while pressed -- see below.
                if (memberIdx.size == 1) weight[memberIdx[0]] = 0f
                if (memberIdx.isEmpty()) {
                    lineWidth[li] = nonMemberWidth + gapsHere
                    lineHeight[li] = idx.maxOf { out[it]?.height ?: 0 }
                    continue
                }

                // Per-seam reserve for a content basis (full or compact): each member's half of a seam is
                // sized off ITS OWN content (a small chevron beside a wide action keeps a small
                // reserve), and a member's total is the sum of its halves across the seams it borders.
                fun seamReserve(basis: IntArray): IntArray {
                    val out = IntArray(n)
                    for (k in 0 until memberIdx.size - 1) {
                        val a = memberIdx[k]; val b = memberIdx[k + 1]
                        out[a] += ((ExpressivePressGrowth * basis[a]) / 2f).roundToInt()
                        out[b] += ((ExpressivePressGrowth * basis[b]) / 2f).roundToInt()
                    }
                    return out
                }

                val room = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth - gapsHere - nonMemberWidth).coerceAtLeast(0)
                } else {
                    val r = seamReserve(full)
                    memberIdx.sumOf { full[it] + r[it] }
                }

                // THE FIT RULE, all-or-nothing: if the line cannot give every member the room its label
                // needs, EVERY member drops to its glyph (see MorphButtonLabel), never just the longest,
                // so which buttons lose their words can't change with the text, font size or language.
                // The test is against CONTENT, not content plus reserve: the reserve is press headroom,
                // not something that has to fit.
                val basis = if (constraints.hasBoundedWidth && memberIdx.sumOf { full[it] } > room) {
                    val c = compact ?: IntArray(n) { i ->
                        if (member[i]) measurables[i].minIntrinsicWidth(h).coerceIn(0, full[i]) else full[i]
                    }.also { compact = it; naturals.compact = it }
                    if (memberIdx.any { c[it] < full[it] }) c else full
                } else {
                    full
                }
                // Resting width for whichever basis won: content (or glyph) plus its own seams'
                // share of reserve. Named distinctly from the `reserve` local a few lines down
                // (the spare < 0 trim branch) -- Kotlin would silently let that one shadow this,
                // which is exactly the kind of same-name-different-thing mixup worth a distinct
                // name for.
                val memberReserve = seamReserve(basis)
                val natLine = IntArray(n) { if (member[it]) basis[it] + memberReserve[it] else full[it] }
                val naturalTotal = memberIdx.sumOf { natLine[it] }

                // Equal shares: the Material 3 connected-group look. TWO or more members --
                // "an equal share" of a line is meaningless for a single button, and taking it
                // literally is what turned a lone action into a pill spanning a whole panel.
                //
                // Capped at the member's OWN natLine (its real content need, full or compact)
                // once the fit rule has picked `compact` for this line -- an equal share of the
                // FULL room can easily still be wider than one compacted button's own glyph+
                // padding needs, and MorphButtonLabel measures each member against exactly the
                // width it is actually given, with no notion of "the group meant this to stay
                // icon-only" -- so a generous equal share quietly let the label back in on
                // whichever buttons had one short enough to fit their own slice, even though the
                // line-wide fit rule had just decided none of them should show one. Reported
                // directly: a button's label reappearing once its equal share (or a press's
                // small growth on top of it) happened to clear its own text width, contradicting
                // the compact verdict the whole row had just agreed to. Uncapped when the line
                // fits its labels (`basis === full`): every member already wants its own real
                // content width there, so equal-sharing UP to fill the row is the intended
                // Material 3 look, not a bug to guard against.
                val base = DoubleArray(n)
                val total: Int
                if (equalWidths && constraints.hasBoundedWidth && memberIdx.size > 1) {
                    val each = room.toDouble() / memberIdx.size
                    val isCompact = basis !== full
                    for (i in memberIdx) {
                        base[i] = if (isCompact && !stretchCompact) minOf(each, natLine[i].toDouble()) else each
                    }
                    total = if (isCompact && !stretchCompact) memberIdx.sumOf { base[it] }.roundToInt() else room
                } else {
                    // Members that declare a weight stretch to fill whatever the line leaves
                    // over. This is what lets a split pill span its row AND still redistribute
                    // on press: the label half carries the weight, the nub keeps its natural
                    // size, and the stretch is part of the budget rather than something a Row
                    // does outside it.
                    val wSum = memberIdx.sumOf { weight[it].toDouble() }
                    for (i in memberIdx) base[i] = natLine[i].toDouble()
                    // Stretching to fill is only for a group GIVEN its width (fillMaxWidth: min == max).
                    // A group in a Row beside a weighted label is handed a loose, bounded width as the
                    // most it may take -- stretching to that claimed the whole row and starved the
                    // label to nothing (the Logs header: one letter per line, buttons on top of it).
                    val handedItsWidth = constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth
                    val spareRaw = room - naturalTotal
                    val spare = if (!handedItsWidth && spareRaw > 0) 0 else spareRaw
                    total = when {
                        // Room to spare, and someone to take it: the weighted members stretch.
                        spare > 0 && wSum > 0.0 -> {
                            for (i in memberIdx) {
                                if (weight[i] > 0f) base[i] += spare * (weight[i] / wSum)
                            }
                            naturalTotal + spare
                        }
                        // Not enough room even for the resting widths. Give up the RESERVE
                        // first, proportionally, and never a pixel of the basis -- so a tight
                        // line loses its squash allowance before it loses anything you can see.
                        // Only if the basis itself does not fit does the line overflow, and by
                        // then the fit rule above has already traded the labels away.
                        spare < 0 -> {
                            val reserve = memberIdx.sumOf { (natLine[it] - basis[it]).toDouble() }
                            if (reserve > 0.0) {
                                val trim = minOf(-spare.toDouble(), reserve)
                                for (i in memberIdx) {
                                    base[i] = natLine[i] - trim * (natLine[i] - basis[i]) / reserve
                                }
                                (naturalTotal - trim).roundToInt()
                            } else {
                                naturalTotal
                            }
                        }
                        else -> naturalTotal
                    }
                }

                // ONE invariant: the members' total width on a line never changes. Everything
                // else is redistribution inside that budget. But WHERE a pressed member's growth
                // comes from is now LOCAL, not a global pool: it draws only on the seam(s) it
                // shares with a real neighbour, exactly like the reserve those seams hold was
                // built. Each seam's own delta is zero-sum between its two members by
                // construction (whatever one side gains, the other loses, exactly), so summing
                // local seam deltas on top of `base` -- whatever the equalWidths/stretch/trim
                // step above already put there -- can never change the line's own total, without
                // needing a separate global want/capacity accounting. This is also what makes a
                // squeezed member's FAR edge stay put: only the shared seam moves, because only
                // the shared seam's own delta touches that member at all.
                val exact = DoubleArray(n)
                for (i in memberIdx) exact[i] = base[i]
                for (k in 0 until memberIdx.size - 1) {
                    val a = memberIdx[k]; val b = memberIdx[k + 1]
                    // Bilateral, not a shared pooled seam: each side can only ever GIVE what it
                    // holds as its OWN half of this seam's reserve (see seamReserve's own doc for
                    // why that half is sized off its own content, not its neighbour's). At rest
                    // (both 0) neither side has taken anything from the other yet, so nothing
                    // here moves -- each side is still just sitting on the half it already
                    // reserved for itself.
                    val bHalf = (ExpressivePressGrowth * basis[b]) / 2.0
                    val aHalf = (ExpressivePressGrowth * basis[a]) / 2.0
                    // The amount EITHER side can gain from this seam is capped by the SMALLER of
                    // the two halves, not by the size of whichever neighbour happens to be giving.
                    // `a` pressing used to draw on `b`'s own half outright, and `b` pressing on
                    // `a`'s -- fine when the two members are close in size, but for a small
                    // icon-only chevron sitting next to a much LARGER labelled action, pressing
                    // the small chevron let it draw on the action's own (much bigger) half and
                    // grow by an amount scaled to the ACTION's size, not its own -- ballooning
                    // far past what the chevron's own footprint would ever ask for. Reported
                    // directly on the AI pebble's "Summarize" action + chevron, the longest
                    // header-action label in the app: pressing the chevron visibly overshot.
                    // Capping both directions at the smaller half keeps typical same-sized pairs
                    // (the overwhelming majority) identical to before, since their two halves are
                    // already close, and bounds the size-mismatched case to the smaller member's
                    // own comfortable growth instead of its bigger neighbour's.
                    val seamCapacity = minOf(aHalf, bHalf)
                    // A press may only take a neighbour's SLACK above its own content need, never
                    // the content itself. Without this cap the floor below had to push a squeezed
                    // member back UP to its content width, which grew the line past its budget and
                    // pushed the last member off-screen -- worst on the map's two-button row,
                    // where one label is wider than its equal share. Capping each side's take by
                    // the other's slack keeps the line total fixed AND every member at or above its
                    // content width, so the floor never has to fire and nothing overflows.
                    val bSlack = (base[b] - basis[b]).coerceAtLeast(0.0)
                    val aSlack = (base[a] - basis[a]).coerceAtLeast(0.0)
                    val delta = press[a] * minOf(seamCapacity, bSlack) - press[b] * minOf(seamCapacity, aSlack)
                    exact[a] += delta
                    exact[b] -= delta
                }
                // Defensive floor only -- content itself never shrinks below what it needs, even
                // in the two-sided-press edge case above. Left uncorrected on the other side of
                // that same rare case; a pixel of slack in the line's own total there is a far
                // smaller cost than a truncated label.
                for (i in memberIdx) {
                    exact[i] = exact[i].coerceAtLeast(basis[i].toDouble())
                }
                // FINAL GUARANTEE: the line's placed width never exceeds its budget, whatever the
                // equal-share or the floor above produced. Claw any excess back from the members
                // with slack first, then proportionally, so the last member can never be pushed
                // past the edge of the row and off-screen.
                val exactSum = memberIdx.sumOf { exact[it] }
                if (exactSum > total && total > 0) {
                    var excess = exactSum - total
                    val slack = memberIdx.map { (exact[it] - basis[it]).coerceAtLeast(0.0) }
                    val slackTotal = slack.sum()
                    if (slackTotal > 0.0) {
                        val take = minOf(excess, slackTotal)
                        memberIdx.forEachIndexed { k, i -> exact[i] -= take * slack[k] / slackTotal }
                        excess -= take
                    }
                    if (excess > 0.0) {
                        val sum = memberIdx.sumOf { exact[it] }
                        if (sum > 0.0) {
                            val scale = (sum - excess) / sum
                            for (i in memberIdx) exact[i] *= scale
                        }
                    }
                }

                // A LONE button on a line has no neighbour to take press width from, so the seam
                // redistribution above left it unchanged and its press would be a corner-only
                // change with NO growth. Instead it EXPANDS to fill the line's budget: laid out
                // left-aligned at rest, full-width while pressed. Done AFTER the budget clamp --
                // filling the row is the whole point, so the clamp must not claw it straight back.
                if (memberIdx.size == 1) {
                    val i = memberIdx[0]
                    if (press[i] > 0f) {
                        val fill = room.coerceAtLeast(basis[i])
                        exact[i] = base[i] + (fill - base[i]) * press[i]
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

            // Bounded (e.g. Modifier.fillMaxWidth()) means the caller handed this group a real
            // width to occupy -- [horizontalAlignment]'s own doc is explicit that this is exactly
            // the "content narrower than the space it was given" case it exists to handle. Using
            // `lineWidth` (the members' own summed content width) here instead of the given room
            // was the bug: whenever the fit rule above compacted members down to icon-only (or
            // any other case that leaves room unclaimed, e.g. no weighted member to soak up spare
            // space), this Layout reported itself as content-sized, so a `fillMaxWidth()` row of
            // buttons silently shrank to a narrow content-hugging cluster instead of actually
            // spanning the width its parent gave it -- reported directly as the expanded map's
            // bottom button row (which compacts to icon-only once a third feature is added) no
            // longer stretching full width.
            //
            // `minWidth == maxWidth`, not just [hasBoundedWidth] -- that first fix over-claimed:
            // a bounded-but-loose maxWidth (minWidth 0) is exactly what a plain Row hands its own
            // NON-weighted children while measuring them against its total budget, not a command
            // to consume all of it. Treating that as "fill everything" starved this group's own
            // weighted SIBLING in that same Row of any space at all once this group sat next to
            // one -- e.g. the Settings "Logs" card's header (an icon + a weight(1f) summary label
            // + this Copy/Clear button pair): the label collapsed to zero width and wrapped every
            // character onto its own line, the same failure mode the very first version of this
            // function had, just from the opposite direction. A genuinely tight constraint (what
            // `Modifier.fillMaxWidth()` applied DIRECTLY to this group actually produces, min ==
            // max) is the only case that means "you were given exactly this width, use it."
            val width = if (constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth) {
                constraints.maxWidth
            } else {
                (lineWidth.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
            }
            val height = (lineHeight.sum() + lineGapPx * (lines.size - 1).coerceAtLeast(0))
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                for ((li, idx) in lines.withIndex()) {
                    // Aligned as if LTR: placeRelative below already mirrors for RTL, so passing the real
                    // direction here flipped it twice and a lone button rested on the wrong edge.
                    var x = horizontalAlignment.align(lineWidth[li], width, androidx.compose.ui.unit.LayoutDirection.Ltr)
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
