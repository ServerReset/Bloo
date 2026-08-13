package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The rebuilt widget's core: given a measured size and a configuration, decide
 * WHAT the tile shows and HOW MUCH ROOM each piece gets -- before anything is
 * drawn, and in plain Kotlin that a JVM test can sweep across all 42 grid
 * sizes.
 *
 * ## Why this replaces eighteen hand-written tier layouts
 *
 * The previous widget answered "what fits" eighteen separate times, once per
 * aspect-ratio tier, each with its own hand-tuned reservation constants. That
 * shape had one systemic flaw, and every layout bug this widget has ever had
 * was an instance of it: a tier's reservations were a SUM MAINTAINED BY HAND,
 * so any module that took more than its author assumed pushed the total past
 * the tile. RemoteViews does not clip an overflowing Column -- it draws the
 * excess outside the tile's bounds, where the launcher simply does not paint
 * it. An over-budget layout therefore does not look broken; it looks like
 * missing content, which is why these shipped repeatedly and were only ever
 * caught from real device screenshots.
 *
 * [plan] removes that whole class of bug by construction rather than by
 * vigilance. It allocates modules out of a REMAINING BUDGET in priority
 * order: a module is handed room only if the budget still holds its minimum,
 * and if it does not, the module is DROPPED rather than squeezed. The
 * returned [Blueprint]'s bands therefore always sum to no more than the inner
 * height -- not because someone checked, but because there is no code path
 * that subtracts more than it has.
 *
 * ## The second rule: a module's minimum is its OWN minimum
 *
 * Dropping rather than squeezing is only safe if each module's floor is what
 * that module actually needs to render something. The prior widget got this
 * wrong in a way worth naming, because it is subtle: the big-percentage hero
 * was gated on a generic "is there 24dp here" ring check, but the composable
 * drawn in that slot needed roughly 35dp before its text was legible enough
 * to show at all. Between those two numbers the layout believed it had placed
 * a hero and the tile rendered an empty gap -- every 4x3 through 7x3 tile,
 * silently. So [Band.min] is always the specific content's own floor, and the
 * allocator never hands out a band smaller than it.
 *
 * ## What it does not do
 *
 * No Glance types, no composition, no measurement -- this is a pure function
 * of (size, config, facts), which is what lets [WidgetBlueprintTest] check
 * every size and configuration combination directly instead of inferring
 * layout behaviour from rendered output nobody here can capture.
 */
internal object WidgetBlueprint {

    /** The pieces a tile can show, in the order they stack when all present. */
    enum class Module { HEADER, HERO, INFO, MAP, BUTTONS, FOOTER }

    /** How the car's charge state is drawn, which depends on how much room the
     *  hero band actually won -- not on the tier, as it used to. */
    enum class Hero {
        /** A circular gauge. Wants a squarish, generous band. */
        RING,

        /** A big percentage over a horizontal bar. Fits where a ring cannot,
         *  because a bar needs ~10-14dp of height where a ring needs its full
         *  diameter. */
        BAR,

        /** A VERTICAL bar filling from the bottom, with the percentage beside or
         *  above it.
         *
         *  The mirror of [BAR], for the mirrored shape: a tall narrow tile has
         *  height to spare and almost no width, which is the one case where a
         *  horizontal bar is the wrong instrument -- it would be a 30dp stub on a
         *  200dp-tall tile. A vertical bar uses exactly the axis that tile has,
         *  and a fill that rises with charge reads correctly without any label at
         *  all. */
        VBAR,

        /** One ordinary text line ("69% · 214 mi"). The last resort that still
         *  says something, for a band too short for even a bar. */
        LINE,

        /** Nothing -- the band was dropped, or the car has no percent to show.
         *  Never means "a hero was placed but drew nothing". */
        NONE,
    }

    /** The facts about the car and its configuration that change WHAT can be
     *  shown, separated from the car object itself so the allocator stays a
     *  pure function and the test can enumerate combinations directly. */
    data class Facts(
        val hasPercent: Boolean = true,
        val hasCoords: Boolean = true,
        val hasMapBitmap: Boolean = true,
        val actionCount: Int = 4,
        val infoFieldCount: Int = 4,
        /** More than one car is set up, so the header carries a tap-to-switch
         *  pill. It makes the header taller than its text alone, and
         *  [Scale.Frame] deliberately refuses to default the answer, so it is
         *  a fact the caller supplies rather than a guess made here. */
        val multipleCars: Boolean = false,
    )

    /** One allocated horizontal band: [module] gets exactly [height]. */
    data class Band(val module: Module, val height: Dp)

    /**
     * A complete decision about one render. [bands] is the vertical stack in
     * order; its heights plus the gaps between them are guaranteed to fit
     * [innerHeight]. Anything absent from [bands] is not drawn at all, which
     * is the point: absence is a decision the allocator made, never a module
     * that was placed and then failed to paint.
     */
    data class Blueprint(
        val grid: WidgetGrid.GridSize,
        val size: DpSize,
        val innerWidth: Dp,
        val innerHeight: Dp,
        /** The BASE gap: between a ring and the content beside it, between
         *  buttons, and anywhere else a small separation is wanted. Stays small. */
        val gap: Dp,
        /** The vertical gap between stacked BANDS, which additionally absorbs
         *  leftover tile height (see [plan]) and so can be much larger than [gap].
         *
         *  A separate field rather than a wider meaning for [gap], because [gap] is
         *  read horizontally too -- the side-by-side path spaces the mark from the
         *  content with it AND subtracts it from the available width. Widening the
         *  shared value silently changed both of those, which is the kind of quiet
         *  breakage a new name prevents outright. */
        val bandGap: Dp,
        val bands: List<Band>,
        val hero: Hero,
        /** Ring diameter when [hero] is [Hero.RING]; 0 otherwise. */
        val ringEdge: Dp,
        /** True when the hero sits BESIDE the rest of the content rather than
         *  above it -- the wide, short shapes (7x2, 7x3) where stacking would
         *  leave both halves starved but a row gives each a real slice. */
        val sideBySide: Boolean,
        /** True when this came from the one-row [strip] path rather than the
         *  stacking allocator. The canvas must not re-derive this from the band
         *  count or the row count: two places deciding the same thing is how
         *  a layout ends up arranged one way and budgeted the other, which is
         *  the entire failure mode this rebuild exists to remove. */
        val isStrip: Boolean,
        val infoRows: Int,
        val buttonCount: Int,
        val buttonsStacked: Boolean,
        /** Info fields the hero already shows, so the info stack does not
         *  print the same number twice on one tile. */
        val suppressedInfo: Set<WidgetInfoField>,
    ) {
        fun height(module: Module): Dp =
            bands.firstOrNull { it.module == module }?.height ?: 0.dp

        fun has(module: Module): Boolean = bands.any { it.module == module }

        /** Total vertical dp this blueprint commits to, gaps included. The
         *  invariant [WidgetBlueprintTest] pins: never more than
         *  [innerHeight]. */
        val committedHeight: Dp
            get() = bands.fold(0.dp) { acc, b -> acc + b.height } +
                bandGap * (bands.size - 1).coerceAtLeast(0)
    }

    // ---- Module floors -------------------------------------------------------
    // Each is what that specific module needs to draw something worth drawing.
    // These are the numbers the allocator refuses to go below, so they are the
    // only place a "placed but empty" module could come from.

    /** A ring reads as a gauge rather than a dot from here up. Matches
     *  [Scale]'s own MIN_RING so the two cannot disagree. */
    private val MIN_RING = 24.dp

    /** A bar hero is the bar itself plus its percentage line above it. Below
     *  this the percentage is smaller than body text, which looks like a
     *  mistake rather than a headline. */
    private const val MIN_BAR_SP = 15f

    /** A vertical bar needs real height to read as a fill rather than a dash --
     *  below this it says less than the percentage text alone would. */
    private val MIN_VBAR = 56.dp

    /** Above this width a ring is a real gauge rather than a token, so the
     *  vertical bar stops being the better answer. Set at the point where a ring
     *  plus its own percentage text still reads. */
    private val VBAR_MAX_WIDTH = 90.dp

    /**
     * The diameter a ring needs before it is worth CHOOSING over a bar.
     *
     * Deliberately larger than [MIN_RING], and the two answer different questions.
     * MIN_RING is "can this be drawn at all", which is what stops a ring becoming a
     * smudge. This is "is a ring the best use of this band", and on a wide short
     * tile the answer at 30dp is no: the tile has width to spare and the ring is
     * bounded by the band's HEIGHT, so it renders as a token circle floating in a
     * lot of empty card. A horizontal bar uses that width and says the same thing.
     */
    private val RING_WORTH_IT = 44.dp

    /** How tall the hero band must be relative to the tile's width before a ring
     *  beats a bar. Below it the circle cannot fill the tile horizontally however
     *  large it gets, so the bar -- which spans the full width -- says the same
     *  thing using the room that is actually there. */
    private const val RING_ASPECT = 0.45f

    private fun minHeader(size: DpSize, textScale: Float, hasSwitcher: Boolean): Dp {
        val text = Scale.lineHeight(Scale.titleSp(size).value, textScale)
        // The switcher pill is a fixed-size touch target that does not shrink
        // with the text, so on a small tile it, not the title, is what sets
        // the header's real height.
        return if (hasSwitcher) maxOf(text, Scale.pillSize(size)) else text
    }

    private fun minFooter(size: DpSize, textScale: Float): Dp =
        Scale.lineHeight(Scale.subtitleSp(size).value, textScale)

    private fun minInfoRow(size: DpSize, textScale: Float): Dp =
        Scale.lineHeight(Scale.valueSp(size).value, textScale) + 2.dp

    private fun minBarHero(size: DpSize, textScale: Float): Dp =
        Scale.lineHeight(MIN_BAR_SP, textScale) + Scale.barHeight(size) + 4.dp

    private fun minLineHero(size: DpSize, textScale: Float): Dp =
        Scale.lineHeight(Scale.subtitleSp(size).value, textScale)

    /** One button row/stack entry, floored so a button stays a real tap
     *  target rather than a stripe. */
    private fun minButtons(size: DpSize): Dp = Scale.buttonHeight(size).coerceAtMost(28.dp)

    private val MIN_MAP = 44.dp

    /** Slack kept around the button row so the pill is not flush against its band.
     *  Flush, the dp-to-px rounding at the boundary clips the corner radius and the
     *  button renders as a rectangle with its shape cut. */
    private val BUTTON_BREATHING = 6.dp

    /** Ceiling on the between-band gap once leftover height is spread into it.
     *  Past this a gap stops reading as rhythm and starts reading as the content
     *  having come apart. */
    private val MAX_SPREAD_GAP = 28.dp

    // ---- The allocator -------------------------------------------------------

    /** A module competing for room: its own floor, whether it is wanted at
     *  all, and how greedily it should absorb leftover space. */
    private data class Want(
        val module: Module,
        val min: Dp,
        val weight: Float = 0f,
        val max: Dp = Dp.Infinity,
    )

    /**
     * The whole layout decision for one render.
     *
     * Allocation runs in three passes, and the order is what makes the result
     * safe:
     *
     *  1. Every wanted module is offered its floor, in priority order. If the
     *     remaining budget cannot cover a floor (plus the gap that placing it
     *     would add), that module is dropped and the rest carry on. This is
     *     the pass that makes overflow impossible.
     *  2. Leftover height is shared among the greedy modules by weight, so a
     *     tall tile grows its map and hero instead of ending in one enormous
     *     trailing Spacer -- which on a 600x520 tile was a black void taller
     *     than the content above it.
     *  3. The hero style is chosen from the height the hero band ACTUALLY won,
     *     never from the tier: a ring if the band is deep enough to hold a
     *     circle, a bar if it is not, one text line if even that will not fit.
     */
    fun plan(
        size: DpSize,
        config: WidgetConfig,
        facts: Facts = Facts(),
    ): Blueprint {
        val grid = WidgetGrid.gridFor(size)
        val ts = config.safeTextScale
        val pillCorner = config.effectiveCorner == WidgetConfig.CORNER_PILL &&
            Scale.pillAppliesAt(size)
        val frame = Scale.Frame(
            size = size,
            textScale = ts,
            pillCorner = pillCorner,
            hasSwitcher = facts.multipleCars,
        )
        val innerW = Scale.innerWidth(frame)
        val innerH = Scale.innerHeight(frame)
        val gap = Scale.buttonGap(size)

        // A one-row strip is a fundamentally different problem: there is no
        // vertical room to divide, so everything that appears has to appear
        // side by side. It gets its own path rather than being squeezed
        // through the stacking allocator, which would drop everything but one
        // band and call that a layout.
        if (grid.rows <= 1 || innerH < 56.dp) {
            return strip(grid, size, innerW, innerH, gap, config, facts, ts)
        }

        val controls = config.priority == WidgetConfig.PRIORITY_CONTROLS
        val wantsHero = config.showRing && facts.hasPercent
        val wantsMap = config.showMap && facts.hasCoords && facts.hasMapBitmap
        val wantsButtons = facts.actionCount > 0
        val wantsInfo = facts.infoFieldCount > 0
        // Computed here, ahead of the allocator, rather than only at the hero decision
        // further down -- see that val's own comment for what it means. Used here to
        // give the hero band a bigger FLOOR up front on a wide-not-tall tile, not just
        // a bigger weighted share of whatever slack happens to be left over: reported
        // twice now, first as a ring where a bar belonged, then -- after ring was
        // simply blocked without this -- as no gauge at all, because the ordinary
        // floor (minLineHero) plus a weighted slice of slack often lands short of what
        // a bar actually needs (minBarHero), and blocking ring left nothing to fall
        // back to. Reserving the bar's own floor from the start means the allocator
        // doesn't have to get lucky with slack for this shape to get its gauge.
        //
        // Reads the real measured `size.width`/`size.height` now, not `grid.cols` vs
        // `grid.rows`. Grid-cell counts round the real measurement to the nearest whole
        // cell on WidgetGrid's own NOMINAL 70n-30 scale, which is deliberately robust to
        // launcher cell-pitch variance for classifying which layout shape applies -- but
        // that same rounding means two grids can differ by one cell either way from a
        // launcher's real px, and a tile that is genuinely wider than tall in actual
        // measured dp can still round to cols == rows or even cols < rows. Reported a
        // second time on a real device after the cols/rows version shipped: still a
        // ring on a tile that visibly reads as wider than tall. The real dimensions
        // don't have that rounding ambiguity.
        val wideNotTall = size.width > size.height

        // Second and third reports against the exact same starvation problem
        // `heroFloor` exists to fix, neither one actually wide-not-tall:
        //
        //  - A real 3x3 widget (180x180, an EXACT square under WidgetGrid's own
        //    formula) with the default config -- wideNotTall (`width > height`,
        //    strict) is false for an exact square, so it got the small floor.
        //  - A real "four wide, three tall" widget, reported wide-not-tall by eye but
        //    whose ACTUAL measured size (this launcher's real cell pitch, not
        //    WidgetGrid's nominal 70n-30 formula) turned out only a little taller than
        //    wide rather than wider than tall -- simulating the allocator's own
        //    arithmetic across a grid of nearby real sizes found dozens of them, not
        //    just that one exact point, still landing in the same trap: the hero band
        //    clears MIN_RING (24dp) but neither RING_WORTH_IT (44dp) nor the bar's own
        //    floor (~35dp), so it falls to the small-ring last resort though a bar was
        //    one weighted redistribution away from fitting. wideNotTall answering "is
        //    this SPECIFIC point wider than tall" turned out to be exactly the kind of
        //    real-device-measurement fragility its own comment already warned about
        //    for the grid-cell-rounding version it replaced.
        //
        // `barCapable` asks a different, more robust question: not "is this tile's
        // ORIENTATION landscape", but "is this tile WIDE ENOUGH for a bar to be
        // geometrically plausible at all" -- the same VBAR_MAX_WIDTH line the hero
        // decision below already draws for the opposite edge (below it a horizontal
        // bar has no room to be one and VBAR takes over instead). Above that line a
        // bar is always at least possible, regardless of whether the tile happens to
        // be a few dp taller than it is wide, so reserving its floor there is never
        // reserving room for a shape that couldn't be drawn anyway.
        //
        // Deliberately only widens the FLOOR, not RING_ASPECT's own gate (still
        // `!wideNotTall`, unchanged) -- a big, roomy tile whose hero band clears
        // RING_ASPECT on its own merits keeps its ring; only a starved one is pushed
        // to the bar instead of the token circle. Verified by simulating this exact
        // arithmetic outside the codebase across every nominal grid size crossed with
        // every config/facts combination this file's own test suite sweeps, plus a
        // dense neighbourhood of real-device sizes around the reported 4x3 -- zero
        // remaining "starved to a ring" outcomes, and nothing already passing (the
        // tall-narrow VBAR case, the wide-tile-never-VBAR case, the roomy-map-picks-
        // bar case, the wide-not-tall case) regressed.
        val barCapable = innerW >= VBAR_MAX_WIDTH

        // Priority order. Controls-priority promotes the buttons above the
        // hero and the info stack, but never above a minimal hero: a
        // controls tile that shows four buttons and NOTHING about the car --
        // no charge, no lock state, not even which car it is -- was a real
        // reported bug, and the fix belongs in the ordering, not in each
        // tier remembering to add a badge back.
        val wants = buildList {
            if (config.showHeader) {
                add(Want(Module.HEADER, minHeader(size, ts, facts.multipleCars)))
            }
            // Every module states a CEILING as well as a floor, and that is what makes
            // the tile actually fill.
            //
            // Slack was being shared by weight with no cap, so a module took height it
            // could not use and then drew smaller anyway: a ring cannot exceed the
            // tile's WIDTH however tall its band is, and a button caps at ~48dp. Both
            // sat in an over-sized band with the surplus blank -- the empty space
            // above and below the ring, and the dead strip under the buttons.
            //
            // With ceilings the surplus flows on to whatever can still use it (the map,
            // which has none), so the same total height ends up drawn instead of
            // reserved. This is the third instance of one mistake -- a module wanting
            // less than the allocator gave it -- after the map's own 110dp cap.
            // The hero's ceiling depends on which instrument the band could hold,
            // because the two are bounded by DIFFERENT axes.
            //
            // A ring cannot exceed the tile's width, so on an ordinary tile the width
            // is the honest ceiling. A VERTICAL bar is the opposite: it is bounded by
            // height and wants more of it than the tile is wide. Capping both at the
            // width made the vertical bar unreachable -- a 60dp-wide tile capped its
            // hero band at 46dp, below the bar's own 56dp minimum, so the narrow tall
            // shape fell back to the token ring this was meant to replace. Caught by
            // the test written for exactly that shape.
            val heroCeiling = if (innerW < VBAR_MAX_WIDTH) {
                maxOf(innerH * 0.5f, MIN_VBAR)
            } else {
                maxOf(innerW, minLineHero(size, ts))
            }
            // Includes the GAPS between stacked buttons, and a little breathing room.
            //
            // Both matter and both were wrong in the first cut of this ceiling. A
            // stack of n buttons needs n heights PLUS n-1 gaps; budgeting only the
            // heights left the band short by exactly the gaps, and the stack then
            // overflowed the band by that much -- the last button clipped at the
            // bottom edge.
            //
            // The breathing room fixes a subtler one: with the band exactly equal to
            // the button, the pill sits flush against both edges of its box, and
            // rounding dp to px there shaves the corner radius, so the buttons
            // rendered as rectangles with their shapes cut. A couple of dp either
            // side keeps the pill's own silhouette intact.
            val stacked = facts.actionCount > 0 && innerW < 90.dp
            val buttonRun = if (stacked) facts.actionCount else 1
            val buttonCeiling = Scale.buttonHeight(size) * buttonRun +
                (if (stacked) Scale.buttonGap(size) * (buttonRun - 1) else 0.dp) +
                BUTTON_BREATHING
            val infoCeiling = minInfoRow(size, ts) * facts.infoFieldCount.coerceAtLeast(1)
            // See `barCapable`'s own comment: whenever a bar is geometrically
            // plausible at all, the hero's FLOOR is the bar's own minimum, not the
            // plain text line's -- guaranteeing the band the allocator hands out is
            // already enough for a bar, rather than hoping a weighted share of
            // leftover slack happens to reach it.
            val heroFloor = if (barCapable) minBarHero(size, ts) else minLineHero(size, ts)
            if (controls) {
                if (wantsButtons) {
                    add(Want(Module.BUTTONS, minButtons(size), weight = 0.5f, max = buttonCeiling))
                }
                if (wantsHero) {
                    add(Want(Module.HERO, heroFloor, weight = 2f, max = heroCeiling))
                }
                if (wantsInfo) {
                    add(Want(Module.INFO, minInfoRow(size, ts), weight = 0.5f, max = infoCeiling))
                }
            } else {
                if (wantsHero) {
                    add(Want(Module.HERO, heroFloor, weight = 3f, max = heroCeiling))
                }
                if (wantsInfo) {
                    add(Want(Module.INFO, minInfoRow(size, ts), weight = 1f, max = infoCeiling))
                }
                if (wantsButtons) {
                    add(Want(Module.BUTTONS, minButtons(size), weight = 0.5f, max = buttonCeiling))
                }
            }
            if (config.showFooter) add(Want(Module.FOOTER, minFooter(size, ts)))
            // The map is last in PRIORITY (it is the first thing to lose on a
            // cramped tile) but the greediest for leftovers, because it is the
            // one module that genuinely improves with more room rather than
            // just getting taller.
            if (wantsMap) add(Want(Module.MAP, MIN_MAP, weight = 4f))
        }

        // Pass 1 -- floors, in priority order, dropping what will not fit.
        var remaining = innerH
        val taken = mutableListOf<Want>()
        for (w in wants) {
            val gapCost = if (taken.isEmpty()) 0.dp else gap
            if (remaining - gapCost >= w.min) {
                remaining -= gapCost + w.min
                taken += w
            }
        }

        // Pass 2 -- share the slack by weight, capped so nothing runs away.
        val heights = taken.associate { it.module to it.min }.toMutableMap()
        // Rounds, not one pass. A module that hits its ceiling stops taking, and its
        // unclaimed share has to go back into the pot for whatever can still use it --
        // otherwise capping the ring and the buttons would simply move the empty space
        // rather than remove it, which is the bug this is fixing. Bounded because every
        // round either fills the budget or takes at least one module out of the pool.
        var slack = remaining
        var open = taken.filter { it.weight > 0f }
        while (slack > 0.5.dp && open.isNotEmpty()) {
            val totalWeight = open.sumOf { it.weight.toDouble() }.toFloat()
            if (totalWeight <= 0f) break
            var used = 0.dp
            for (w in open) {
                val current = heights[w.module] ?: 0.dp
                val share = slack * (w.weight / totalWeight)
                val add = minOf(share, (w.max - current).coerceAtLeast(0.dp))
                if (add > 0.dp) {
                    heights[w.module] = current + add
                    used += add
                }
            }
            if (used <= 0.dp) break
            slack -= used
            open = open.filter { (heights[it.module] ?: 0.dp) < it.max - 0.5.dp }
        }

        // Anything still unclaimed becomes BREATHING SPACE between the bands.
        //
        // With every module capped, a tile can legitimately have height that nothing
        // wants: a large widget with the map and the ring both switched off caps out
        // at a header, a couple of stat rows and a button row, which on a 7x7 leaves
        // hundreds of dp over. Dumping that into one module would undo the ceilings
        // (it would be an over-sized band drawing small again) and leaving it at the
        // bottom stacks the content against the top edge, which is the top-heavy look
        // the ceilings were meant to fix.
        //
        // Spread between the bands it reads as deliberate spacing instead. Capped,
        // because past a point a gap stops looking like rhythm and starts looking
        // like the content came apart -- beyond the cap the tile simply has more room
        // than it has things to say, and the honest answer is space at the bottom.
        val spreadGap = if (taken.size > 1 && slack > 0.dp) {
            minOf(gap + slack / (taken.size - 1), MAX_SPREAD_GAP)
        } else {
            gap
        }

        // Restore the drawing order (priority order is not stacking order).
        val order = listOf(
            Module.HEADER, Module.HERO, Module.INFO, Module.MAP,
            Module.BUTTONS, Module.FOOTER,
        )
        val bands = order.mapNotNull { m -> heights[m]?.let { Band(m, it) } }

        // Pass 3 -- what the hero band actually won decides how it is drawn.
        val heroH = heights[Module.HERO] ?: 0.dp
        // A ring must fit BOTH ways: its diameter cannot exceed the band, nor
        // the tile's own width, or it stops being a circle.
        val ringCandidate = minOf(heroH, innerW)
        val sideBySide = !controls && grid.rows in 2..3 && grid.cols >= 4 &&
            innerW >= 240.dp && heroH < MIN_RING
        // `wideNotTall` (computed earlier, alongside the other `wants*` flags -- see
        // its own comment there for the full reasoning) is deliberately NOT applied to
        // the `sideBySide` ring branch immediately below. `sideBySide` shapes (7x2/7x3)
        // are wide-not-tall by the same definition, but that branch answers a
        // different question than RING_ASPECT does: `blueprint.sideBySide` also picks
        // the whole tile's ARRANGEMENT (see WidgetCanvas's SideBySide composable) --
        // hero in a narrow side column beside the text, not a hero band stacked above
        // other rows -- and a wide horizontal BAR does not fit a narrow side column at
        // all. Blocking ring there without giving the side-by-side arrangement
        // anything else that fits would resurrect the exact blank-tile bug this branch
        // exists to prevent. The reported "wide but not tall, should be a bar" shape
        // is the ordinary STACKED layout (header, hero, map/info, buttons each their
        // own band) -- RING_ASPECT below, not this one.
        val hero = when {
            !wantsHero || heroH <= 0.dp -> Hero.NONE
            // Side-by-side gives the hero the ROW's height, not the band's, so
            // a short wide tile can still carry a real ring beside its text --
            // this is the 7x2 / 7x3 case the old widget rendered as a blank.
            // `!wantsMap` for the same reason as the aspect-ratio branch below: a
            // map on the same tile as a ring reads as two competing shapes.
            sideBySide && minOf(innerH, innerW / 3) >= MIN_RING && !wantsMap -> Hero.RING
            // BEFORE the ring, not after, and the order is the whole point. A
            // ring is bounded by the NARROW axis (its diameter cannot exceed
            // innerW), so on a 60dp-wide, 300dp-tall tile it passes the 24dp ring
            // check comfortably and then draws a 46dp circle with 200dp of empty
            // tile beneath it. That is how these shapes ended up looking empty.
            // Checked after ring in a first draft, which made this branch
            // unreachable -- every narrow tile still took the ring.
            innerW < VBAR_MAX_WIDTH && heroH >= MIN_VBAR -> Hero.VBAR
            // A ring has to be a meaningful part of the tile, not just legible. Its
            // diameter is bounded by the NARROW axis, so on a wide short band it
            // clears every size floor and still renders as a token circle adrift in
            // empty width -- which is what "it's not using the bar when it should"
            // was describing. Requiring the band to be at least this fraction of the
            // width is what routes those shapes to the bar, which uses the axis the
            // tile actually has.
            //
            // `!wantsMap` on top of that ratio check for the same complaint, one
            // shape further: a map already draws a wide horizontal band right below
            // the hero, so a ring above it reads as two competing round-vs-rectangular
            // shapes stacked on each other, and the ring is the smaller, weaker one --
            // "should be a bar not a circle for this layout", reported against exactly
            // this shape (hero + range row + map + buttons). The bar shares the map's
            // own axis instead of fighting it, the same reasoning RING_ASPECT already
            // uses for the width axis generally, just forced whenever a map is also on
            // the tile rather than left to the aspect ratio alone.
            // `!wideNotTall` on top: this branch's own ratio check can still pass on a
            // tile that is nonetheless landscape overall (see the val's own comment),
            // and "wide but not tall, should be a bar not a circle" was reported
            // against exactly that -- a size where the per-band math said ring but the
            // tile plainly wasn't square-ish or taller-than-wide.
            ringCandidate >= RING_WORTH_IT && heroH >= innerW * RING_ASPECT && !wantsMap && !wideNotTall -> Hero.RING
            heroH >= minBarHero(size, ts) -> Hero.BAR
            // Below the bar's floor a small ring still beats a bare text line -- it is
            // the last thing that reads as a GAUGE, and MIN_RING is exactly the point
            // where it stops being one. Deliberately UNGUARDED by `!wantsMap`/
            // `!wideNotTall`, unlike the two branches above: those answer "is a ring
            // the wrong SHAPE for this tile when a bar would look better", which is a
            // real question only once a bar actually fits. This one only fires once
            // bar has already been ruled out for lacking room, where the choice isn't
            // ring-vs-bar any more, it's ring-vs-nothing -- and a small ring still beats
            // that. A previous version of this guard blocked it here too, on the theory
            // that it was the branch letting ring slip through on a map-bearing tile;
            // it wasn't -- the branch above had already ruled that tile out by the time
            // this one is reached -- and blocking it left tiles that fit neither ring
            // nor bar showing NO gauge at all, which is a worse outcome than the ring
            // this exists to fall back to. Reported from a real device.
            ringCandidate >= MIN_RING -> Hero.RING
            heroH >= minLineHero(size, ts) -> Hero.LINE
            else -> Hero.NONE
        }
        val ringEdge = when (hero) {
            Hero.RING -> if (sideBySide) minOf(innerH, innerW / 3) else ringCandidate
            else -> 0.dp
        }

        val infoH = heights[Module.INFO] ?: 0.dp
        val infoRows = if (infoH <= 0.dp) 0 else
            Scale.infoRowsIn(size, infoH, ts, facts.infoFieldCount).coerceAtLeast(1)

        // Row or stack, decided by which one can actually show the whole set.
        // A row that cannot fit them all used to escalate to a stack, which
        // "fixed" the horizontal squeeze by overflowing vertically instead: on
        // a 300x78 tile with four actions that produced a ~146dp column inside
        // a 78dp widget, two buttons visible and the rest off the tile. So both
        // capacities are measured, and if neither holds the full set the count
        // is truncated to whichever shows more.
        val buttonH = heights[Module.BUTTONS] ?: 0.dp
        val across = if (buttonH <= 0.dp) 0 else Scale.buttonsAcross(size, innerW)
        val down = if (buttonH <= 0.dp) 0 else Scale.buttonsDown(size, buttonH)
        val stacked = across < facts.actionCount && down > across
        val capacity = if (stacked) down else across
        val buttonCount = if (buttonH <= 0.dp) 0 else
            minOf(facts.actionCount, capacity).coerceAtLeast(1)

        return Blueprint(
            grid = grid,
            size = size,
            innerWidth = innerW,
            innerHeight = innerH,
            gap = gap,
            bandGap = spreadGap,
            bands = bands,
            hero = hero,
            ringEdge = ringEdge,
            sideBySide = sideBySide,
            isStrip = false,
            infoRows = infoRows,
            buttonCount = buttonCount,
            buttonsStacked = stacked,
            suppressedInfo = suppressed(hero),
        )
    }

    /**
     * The one-row strip (2x1 through 7x1), where the whole tile is about
     * 40dp tall and the only axis worth dividing is horizontal.
     *
     * Every column count from 2 to 7 lands here, which is deliberate: the
     * previous widget gated its strip layout on an aspect ratio, so a 2x1 and
     * a 3x1 fell through it and rendered as a bare icon while a 5x1 got a
     * real layout. A one-row tile is a strip because it has one row, not
     * because it happens to be three times wider than tall.
     */
    private fun strip(
        grid: WidgetGrid.GridSize,
        size: DpSize,
        innerW: Dp,
        innerH: Dp,
        gap: Dp,
        config: WidgetConfig,
        facts: Facts,
        ts: Float,
    ): Blueprint {
        val wantsHero = config.showRing && facts.hasPercent
        // The mark takes the full height it can have; the row is short enough
        // that this is always the binding constraint, never the width.
        val edge = if (wantsHero) innerH.coerceAtMost(innerW / 4) else 0.dp
        val hero = when {
            !wantsHero -> Hero.NONE
            edge >= MIN_RING -> Hero.RING
            innerH >= minLineHero(size, ts) -> Hero.LINE
            else -> Hero.NONE
        }
        val ringEdge = if (hero == Hero.RING) edge else 0.dp

        // Whatever the mark and a name do not use is button room. Buttons are
        // sized from the REAL remaining width, so a strip shows as many whole
        // buttons as fit and no partial one -- truncating is honest, drawing
        // past the edge is not.
        val used = ringEdge + (if (ringEdge > 0.dp) gap else 0.dp)
        val nameRoom = (innerW - used) * 0.35f
        val buttonRoom = (innerW - used - nameRoom).coerceAtLeast(0.dp)
        val across = Scale.buttonsAcross(size, buttonRoom)
        val buttonCount = minOf(facts.actionCount, across)

        return Blueprint(
            grid = grid,
            size = size,
            innerWidth = innerW,
            innerHeight = innerH,
            gap = gap,
            bandGap = gap,
            // A strip is one horizontal band by definition -- the whole tile.
            bands = listOf(Band(Module.HERO, innerH)),
            hero = hero,
            ringEdge = ringEdge,
            sideBySide = true,
            isStrip = true,
            infoRows = 0,
            buttonCount = buttonCount,
            buttonsStacked = false,
            suppressedInfo = suppressed(hero),
        )
    }

    /**
     * Which info fields the hero already covers.
     *
     * A user with Percent in their chosen info fields, on a tile whose hero
     * headline is "69% · 214 mi", saw 69% twice -- once as the headline and
     * again in the stack directly beneath it. Reported from a real device
     * screenshot. The hero is the authority on those numbers, so the stack
     * yields them rather than each layout remembering to filter.
     */
    private fun suppressed(hero: Hero): Set<WidgetInfoField> = when (hero) {
        Hero.NONE -> emptySet()
        Hero.RING, Hero.BAR, Hero.VBAR -> setOf(WidgetInfoField.PERCENT)
        Hero.LINE -> setOf(WidgetInfoField.PERCENT, WidgetInfoField.RANGE)
    }
}
