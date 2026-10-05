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
 * A row of buttons where the pressed one takes ~15% more width and its neighbours give up exactly
 * that much, so the group's own width never changes and no parent remeasures.
 */
/** Extra room a symbol-only group needs before it goes back to showing its labels. */
private val CompactHysteresis = 28.dp

@Composable
fun ExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    spacing: Dp = 3.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    /**
     * Give every member an equal share of the row instead of its natural width. Lives here because
     * `Modifier.weight` cannot reach this layout: weight is RowScope parent data.
     */
    equalWidths: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    /** Gap between wrapped lines. The group wraps like a FlowRow and redistributes per line. */
    lineSpacing: Dp = spacing,
    /**
     * Whether this group may break onto more than one line. False for anything that reads as one
     * object (connected segments, split pill); those compact to glyphs instead of wrapping.
     */
    wrap: Boolean = true,
    /**
     * For an equal-width row compacted to icon-only (see `wrap`): keep the true equal share instead
     * of capping each member at its glyph width, so the row still spans its full width.
     */
    stretchCompact: Boolean = false,
    content: @Composable ExpressiveButtonGroupScope.() -> Unit,
) {
    // Natural (unpressed) child widths from the last resting pass. Deliberately not snapshot state:
    // writing state during measure invalidates the running pass.
    val naturals = remember { NaturalWidths() }
    // Invalidate the natural-width cache whenever the composed content re-runs.
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
            // Once collapsed to symbols a group needs this much spare room before it shows words
            // again.
            val hysteresisPx = CompactHysteresis.roundToPx()
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)

            // Press fractions are read at layout time so a press invalidates layout only. Only
            // children carrying ExpressiveGroupData are members; Spacers, labels and icons keep
            // their natural width and stay out of the redistribution.
            val member = BooleanArray(n) { measurables[it].parentData is ExpressiveGroupData }
            val press = FloatArray(n) { i ->
                (measurables[i].parentData as? ExpressiveGroupData)?.pressFraction?.invoke() ?: 0f
            }
            // Which members absorb the line's leftover space (parent data, free per frame).
            val weight = FloatArray(n) { i ->
                (measurables[i].parentData as? ExpressiveGroupData)?.weight ?: 0f
            }
            // Natural widths come from maxIntrinsicWidth, not a trial measure: a child can be
            // measured once per pass, and a trial inside a width-forcing parent would record the
            // forced width.
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            // Re-read whenever at rest, cached only while a press animates: labels can change and
            // stale widths would mis-size neighbours.
            val resting = press.all { it == 0f }
            if (resting || naturals.content == null || naturals.content!!.size != n) {
                val fresh = IntArray(n) { measurables[it].maxIntrinsicWidth(h).coerceAtLeast(0) }
                if (naturals.content?.contentEquals(fresh) != true) {
                    naturals.content = fresh
                    // Invalidated, not recomputed here.
                    naturals.compact = null
                }
            }
            // What each child's content actually asked for.
            val full = naturals.content!!
            // Proportional members take spare room in proportion to their natural width. A lone
            // member rests at its natural width (see SafeExpansiveButton's fillOnPress).
            val memberCount = member.count { it }
            for (i in 0 until n) {
                if (weight[i] < 0f) weight[i] = if (memberCount > 1) full[i].toFloat().coerceAtLeast(1f) else 0f
            }
            // The icon-only fallback is lazy: minIntrinsicWidth walks each member's subtree, so it
            // is computed once per resting generation, only when some line fails the full-width
            // test.
            var compact: IntArray? = naturals.compact

            // Break into lines as a FlowRow would.
            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
            val lines = ArrayList<IntArray>()
            // True when the labels do not fit on one line but every member has a symbol it can
            // shrink to, and those symbols all fit together on one line.
            fun collapsesInsteadOfWrapping(): Boolean {
                if (n < 2 || maxW == Int.MAX_VALUE || member.any { !it }) return false
                if (full.sum() + gapPx * (n - 1) <= maxW - (if (naturals.compacted) hysteresisPx else 0)) return false
                val c = compact ?: IntArray(n) { i -> measurables[i].minIntrinsicWidth(h).coerceIn(0, full[i]) }
                    .also { compact = it; naturals.compact = it }
                return (0 until n).all { c[it] < full[it] } && c.sum() + gapPx * (n - 1) <= maxW
            }
            if (!wrap || collapsesInsteadOfWrapping()) {
                // A row whose buttons all have a symbol never wraps: it compacts to symbols
                // instead.
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
                // Non-members keep their natural size and are measured first; the rest is the
                // budget.
                var nonMemberWidth = 0
                for (i in idx) if (!member[i]) {
                    val p = measurables[i].measure(childConstraints)
                    out[i] = p
                    nonMemberWidth += p.width
                }
                val memberIdx = idx.filter { member[it] }
                // A lone button rests at its own width (start-aligned) and only fills the line
                // while pressed.
                if (memberIdx.size == 1) weight[memberIdx[0]] = 0f
                if (memberIdx.isEmpty()) {
                    lineWidth[li] = nonMemberWidth + gapsHere
                    lineHeight[li] = idx.maxOf { out[it]?.height ?: 0 }
                    continue
                }

                // Per-seam reserve: each member's half of a seam is sized off its own content, and
                // a member's total is the sum of its halves across the seams it borders.
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

                // FIT RULE, all-or-nothing: if the line cannot give every member the room its label
                // needs, every member drops to its glyph so the choice never depends on text or
                // language. Tested against content, not content plus press reserve.
                val collapse = constraints.hasBoundedWidth &&
                    memberIdx.sumOf { full[it] } > room - (if (naturals.compacted) hysteresisPx else 0)
                if (constraints.hasBoundedWidth) naturals.compacted = collapse
                val basis = if (collapse) {
                    val c = compact ?: IntArray(n) { i ->
                        if (member[i]) measurables[i].minIntrinsicWidth(h).coerceIn(0, full[i]) else full[i]
                    }.also { compact = it; naturals.compact = it }
                    if (memberIdx.any { c[it] < full[it] }) c else full
                } else {
                    full
                }
                // Resting width for the winning basis: content (or glyph) plus its seams' reserve.
                // Named apart from the later `reserve` local to avoid shadowing.
                val memberReserve = seamReserve(basis)
                val natLine = IntArray(n) { if (member[it]) basis[it] + memberReserve[it] else full[it] }
                val naturalTotal = memberIdx.sumOf { natLine[it] }

                // Equal shares need two or more members; a lone button keeps its natural width.
                // Once the fit rule picks `compact`, each share is capped at the member's own
                // natLine so a generous share cannot let a label back in.
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
                    // Weighted members stretch to fill the line's leftover, inside the budget, so a
                    // split pill spans its row and still redistributes on press.
                    val wSum = memberIdx.sumOf { weight[it].toDouble() }
                    for (i in memberIdx) base[i] = natLine[i].toDouble()
                    // Stretch only when the group is GIVEN its width (min == max); a loose bounded
                    // width is only a maximum and stretching would starve a weighted sibling.
                    val handedItsWidth = constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth
                    val spareRaw = room - naturalTotal
                    val spare = if (!handedItsWidth && spareRaw > 0) 0 else spareRaw
                    total = when {
                        // Room to spare: weighted members stretch.
                        spare > 0 && wSum > 0.0 -> {
                            for (i in memberIdx) {
                                if (weight[i] > 0f) base[i] += spare * (weight[i] / wSum)
                            }
                            naturalTotal + spare
                        }
                        // Too tight for resting widths: give up reserve first, proportionally,
                        // never the basis.
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

                // INVARIANT: a line's total member width never changes. A pressed member draws only
                // on the seams it shares with real neighbours; each seam delta is zero-sum, so the
                // total holds and a squeezed member's far edge stays put.
                val exact = DoubleArray(n)
                for (i in memberIdx) exact[i] = base[i]
                for (k in 0 until memberIdx.size - 1) {
                    val a = memberIdx[k]; val b = memberIdx[k + 1]
                    // Bilateral: each side can only give its own half of the seam's reserve.
                    val bHalf = (ExpressivePressGrowth * basis[b]) / 2.0
                    val aHalf = (ExpressivePressGrowth * basis[a]) / 2.0
                    // Each side's gain is capped by the smaller of the two halves, so a small
                    // chevron beside a large action cannot balloon to the action's scale.
                    val seamCapacity = minOf(aHalf, bHalf)
                    // A press takes only a neighbour's slack above its content need, never the
                    // content, so the floor below never fires and nothing overflows.
                    val bSlack = (base[b] - basis[b]).coerceAtLeast(0.0)
                    val aSlack = (base[a] - basis[a]).coerceAtLeast(0.0)
                    val delta = press[a] * minOf(seamCapacity, bSlack) - press[b] * minOf(seamCapacity, aSlack)
                    exact[a] += delta
                    exact[b] -= delta
                }
                // Defensive floor: content never shrinks below what it needs.
                for (i in memberIdx) {
                    exact[i] = exact[i].coerceAtLeast(basis[i].toDouble())
                }
                // FINAL GUARANTEE: placed width never exceeds the budget; claw back from members
                // with slack first, then proportionally, so the last member is never pushed
                // off-screen.
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

                // A lone button has no neighbour to take from, so it expands to the line's budget
                // while pressed (start-aligned at rest). Runs after the budget clamp, which must
                // not undo it.
                if (memberIdx.size == 1) {
                    val i = memberIdx[0]
                    if (press[i] > 0f) {
                        val fill = room.coerceAtLeast(basis[i])
                        exact[i] = base[i] + (fill - base[i]) * press[i]
                    }
                }

                // Largest-remainder rounding so integer widths sum to `total` exactly (no pixel
                // breathing).
                val target = IntArray(n)
                for (i in memberIdx) target[i] = exact[i].toInt()
                var remainder = total - memberIdx.sumOf { target[it] }
                if (remainder > 0) {
                    val order = memberIdx.sortedByDescending { exact[it] - exact[it].toInt() }
                    // Bounded by construction: a spinning measure pass would freeze the app.
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

            // Fill the given width only when min == max (e.g. direct fillMaxWidth()).
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
                    // Aligned as if LTR: placeRelative already mirrors for RTL.
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
