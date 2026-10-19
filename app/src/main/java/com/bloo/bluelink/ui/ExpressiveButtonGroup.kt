package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.bloo.uicommon.NaturalWidths
import com.bloo.uicommon.ExpressiveGroupData
import com.bloo.uicommon.MaxSingleButtonWidth
import com.bloo.uicommon.ExpressiveButtonGroupScope
import com.bloo.uicommon.LocalExpressiveGroup
import com.bloo.uicommon.balancedLineBreaks
import com.bloo.uicommon.content

/**
 * How much EXTRA room a collapsed line must find before its labels come back. This is the margin
 * that stops a row sitting right at the threshold from flickering word/icon/word: dropping to glyphs
 * happens the moment the labels do not fit, and coming back needs this much slack on top.
 */
private val CollapseReturnMargin = 24.dp

/**
 * A row of buttons where the pressed one takes up to ~35% more width and its neighbours give up exactly
 * that much, so the group's own width never changes and no parent remeasures. (The two numbers behind
 * that: [RestingPressReserve] is what a neighbour can give up, [ExpressivePressGrowth] the ceiling.)
 */
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
     * Whether a member alone on its line may expand to fill the line while pressed. True for a
     * labelled action alone on its row (a lone "Sync now"); FALSE for a connected cluster (the
     * pebble header chevron, the lock group), where a single icon button must never balloon.
     */
    growWhenAlone: Boolean = true,
    content: @Composable ExpressiveButtonGroupScope.() -> Unit,
) {
    // Natural (unpressed) child widths from the last resting pass. Deliberately not snapshot state:
    // writing state during measure invalidates the running pass.
    val naturals = remember { NaturalWidths() }
    // Invalidate the natural-width cache whenever the CONTENT this group composes re-runs. So every
    // measure of every group in the app re-walked each member's intrinsic width, and a button's
    // intrinsic width runs a real text layout for its label (that is what
    // `MorphButtonLabel.maxIntrinsicWidth` is).
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
            // A lone button's press-to-fill is capped here, not stretched across a whole row.
            val maxSinglePx = MaxSingleButtonWidth.roundToPx()
            // See CollapseReturnMargin: how much extra room a collapsed line needs before its labels
            // come back. Read once per pass; the sticky state it reads is updated at the end.
            val collapseReturnMarginPx = CollapseReturnMargin.roundToPx()

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
            // Natural widths come from maxIntrinsicWidth rather than from a trial measure. That is
            // not a micro-optimisation, it is what makes filling possible at all: a child may only
            // be measured once per pass, so measuring to learn the natural width leaves nothing
            // with which to place the child at a different one.
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
            // What each child's content actually asked for -- the only intrinsic width this group
            // pays for by default.
            val full = naturals.content!!
            // Proportional members take spare room in proportion to their natural width. A lone
            // member rests at its natural width (see SafeExpansiveButton's fillOnPress).
            val memberCount = member.count { it }
            for (i in 0 until n) {
                if (weight[i] < 0f) weight[i] = if (memberCount > 1) full[i].toFloat().coerceAtLeast(1f) else 0f
            }
            // The symbol-only widths, read lazily: minIntrinsicWidth walks each member's subtree, so it is
            // read once per resting generation and only when some line cannot hold its labels.
            var compact: IntArray? = naturals.compact
            fun compactWidths(): IntArray = compact ?: IntArray(n) { i ->
                if (member[i]) measurables[i].minIntrinsicWidth(h).coerceIn(0, full[i]) else full[i]
            }.also { compact = it; naturals.compact = it }

            val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
            // Stretching to fill is only for a group GIVEN its width (fillMaxWidth: min == max). A group in a
            // Row beside a weighted label is handed a loose, bounded width as the most it may take; stretching
            // to that claimed the whole row and starved the label.
            val handedItsWidth = constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth
            // LINES. A wrapping row keeps its labels and breaks onto another line instead (balanced, like a
            // FlowRow) -- a label is worth more than one line of height. Only a row that reads as one object
            // (wrap = false: connected segments, a split pill, a header cluster) stays on a single line.
            val lines: List<IntArray> =
                if (!wrap || maxW == Int.MAX_VALUE) listOf(IntArray(n) { it }) else balancedLineBreaks(full, gapPx, maxW)
            val multiLine = lines.size > 1

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
                // A lone button on a line of a WRAPPED row fills that line like its neighbours do, so no line
                // is left half empty; a row that is just one button sits at its own width (it fills only
                // while pressed, see below).
                if (memberIdx.size == 1) weight[memberIdx[0]] = if (multiLine && handedItsWidth) 1f else 0f
                if (memberIdx.isEmpty()) {
                    lineWidth[li] = nonMemberWidth + gapsHere
                    lineHeight[li] = idx.maxOf { out[it]?.height ?: 0 }
                    continue
                }

                val room = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth - gapsHere - nonMemberWidth).coerceAtLeast(0)
                } else {
                    val r = seamPressReserve(memberIdx, full)
                    memberIdx.sumOf { full[it] + r[it] }
                }

                // FIT RULE, per button. A button keeps its label unless the line truly cannot hold it. When a
                // line's labels do not fit, the buttons that save the most by dropping to their symbol drop
                // first, one at a time, until the line fits; every other button keeps its label. The smallest a
                // button ever gets is its basis (a press only borrows spare room), so the choice holds through a
                // press: it is made from a resting pass and reused mid-press, and made again only when the room
                // or the buttons change. A line that already dropped some needs a small margin before labels
                // come back, so room hovering at the threshold cannot flip a label per pixel.
                val prior = naturals.decided[li]
                val collapsed: BooleanArray = when {
                    !constraints.hasBoundedWidth -> BooleanArray(n)
                    !resting && prior != null && prior.size == n -> prior
                    else -> {
                        val margin = if (prior != null && prior.size == n && prior.any { it }) collapseReturnMarginPx else 0
                        chooseSymbolOnly(memberIdx, full, ::compactWidths, room, margin)
                    }
                }
                if (constraints.hasBoundedWidth) naturals.decided[li] = collapsed
                val anyCollapsed = memberIdx.any { collapsed[it] }
                val basis = if (anyCollapsed) {
                    val c = compactWidths()
                    IntArray(n) { if (collapsed[it]) c[it] else full[it] }
                } else {
                    full
                }
                // A button showing only its symbol never stretches: spare room goes to the labelled ones, so an
                // icon button is never a wide empty pill.
                for (i in memberIdx) if (collapsed[i]) weight[i] = 0f
                // Resting width for the winning basis: content (or glyph) plus its seams' reserve.
                // Named apart from the later `reserve` local to avoid shadowing.
                val memberReserve = seamPressReserve(memberIdx, basis)
                val natLine = IntArray(n) { if (member[it]) basis[it] + memberReserve[it] else full[it] }
                val naturalTotal = memberIdx.sumOf { natLine[it] }

                // Equal shares need two or more members; a lone button keeps its natural width. Only labelled
                // members share the room; a symbol-only member keeps its own compact width.
                val base = DoubleArray(n)
                val total: Int
                if (equalWidths && constraints.hasBoundedWidth && memberIdx.size > 1) {
                    val iconIdx = memberIdx.filter { collapsed[it] }
                    val labelIdx = memberIdx.filter { !collapsed[it] }
                    val iconTotal = iconIdx.sumOf { natLine[it] }
                    for (i in iconIdx) base[i] = natLine[i].toDouble()
                    // Equal shares, but never below what a label needs. A member whose label is wider than the equal
                    // share keeps its own width and the rest SHARE WHAT IS LEFT (repeated until no member is over
                    // its share): taking max(each, own) for every member let the line exceed the room (a long label
                    // beside two short ones pushed the last button off the edge).
                    var remaining = (room - iconTotal).toDouble()
                    val shared = labelIdx.toMutableList()
                    while (shared.isNotEmpty()) {
                        val each = remaining / shared.size
                        val wider = shared.filter { basis[it] > each }
                        if (wider.isEmpty()) break
                        for (i in wider) { base[i] = basis[i].toDouble(); remaining -= basis[i] }
                        shared.removeAll(wider.toSet())
                    }
                    for (i in shared) base[i] = remaining / shared.size
                    total = if (labelIdx.isEmpty()) iconTotal else maxOf(room, memberIdx.sumOf { base[it] }.roundToInt())
                } else {
                    // Weighted members stretch to fill the line's leftover, inside the budget, so a
                    // split pill spans its row and still redistributes on press.
                    val wSum = memberIdx.sumOf { weight[it].toDouble() }
                    for (i in memberIdx) base[i] = natLine[i].toDouble()
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
                        // Not enough room even for the resting widths. Give up the RESERVE first,
                        // proportionally, and never a pixel of the basis -- so a tight line loses
                        // its squash allowance before it loses anything you can see.
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

                // Press redistribution (zero-sum across seams) plus the floor and the
                // never-exceed-the-budget guarantee, all as pure math (see ExpressiveButtonGroupMath).
                val exact = redistributeForPress(memberIdx, base, basis, press)
                clampLineToBudget(memberIdx, exact, basis, total)

                // A LONE button on a line has no neighbour to take press width from, so the seam
                // redistribution above left it unchanged and its press would be a corner-only
                // change with NO growth. Instead it EXPANDS to fill the line's budget: laid out
                // left-aligned at rest, full-width while pressed.
                if (memberIdx.size == 1 && growWhenAlone) {
                    val i = memberIdx[0]
                    if (press[i] > 0f) {
                        // At most 1.25x its resting width: a lone button widening toward the whole
                        // row reads as a slab.
                        val fill = minOf(room.toDouble(), maxSinglePx.toDouble(), basis[i] * 1.25)
                            .coerceAtLeast(basis[i].toDouble())
                        exact[i] = base[i] + (fill - base[i]) * press[i]
                    }
                }

                val target = largestRemainderWidths(memberIdx, exact, total)
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
