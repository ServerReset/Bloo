package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sweeps the whole size range the launcher can hand the widget and asserts
 * that the layouts built on [Scale] still fit the tile they're given.
 *
 * WHY THIS EXISTS: RemoteViews gives no measurement callback and does NOT
 * clip an overflowing Column -- it lets children bleed past the tile's
 * bottom edge. So every vertical decision in CarWidget is an estimate made
 * ahead of time, and four of them have been wrong: the button-height cap
 * (902 sizes), the ring room (all nine MEDIUM/LARGE/XL tiers, worst 233dp),
 * the bar hero (422 sizes, worst 1.7dp) and the name/stat pair (1266 sizes,
 * worst 9.8dp). Each was found by rebuilding this arithmetic outside the
 * codebase and sweeping it; each fix was only ever re-provable by doing that
 * again. This does it in CI, against the real functions.
 *
 * Every case runs at all four text scales, because the scale multiplies the
 * text but not the tile: a budget that holds at 1.0x can fail at 1.4x, which
 * is exactly where two of the four bugs above lived.
 */
class WidgetScaleTest {

    private val scales = listOf(0.8f, 1.0f, 1.2f, 1.4f)

    /** Every size the manifest permits, on a 2dp grid (90,601 of them). */
    private fun sizes(): Sequence<DpSize> = sequence {
        var w = 40
        while (w <= 640) {
            var h = 40
            while (h <= 640) {
                yield(DpSize(w.dp, h.dp))
                h += 2
            }
            w += 2
        }
    }

    private fun content(size: DpSize) = size.height - Scale.contentPadding(size) * 2

    /**
     * BarHero: hero number + bar + optional sub-line, or a decline. Mirrors
     * the composable's own gating exactly -- if this and it ever disagree the
     * test is worthless, so both read the same three Scale calls in the same
     * order.
     */
    @Test
    fun `bar hero fits its tile at every size and text scale`() {
        var worst = 0f
        var worstAt = ""
        for (size in sizes()) {
            val avail = content(size)
            val barH = Scale.barHeight(size)
            for (ts in scales) {
                val heroSp = Scale.heroSpIn(size, avail, barH + 4.dp, ts) ?: continue
                val heroH = Scale.lineHeight(heroSp, 1f)
                val subH = Scale.lineHeight(Scale.subtitleSp(size).value, ts) + 4.dp
                var demand = heroH + 4.dp + barH
                if (demand + subH <= avail) demand += subH
                val over = (demand - avail).value
                if (over > worst) { worst = over; worstAt = "$size @${ts}x" }
            }
        }
        assertTrue(worst <= 0.01f, "bar hero overflows by ${worst}dp at $worstAt")
    }

    /** The plain name/stat pair BarHero declines to, and the two compact rows
     *  use directly. The name alone must always fit; the stat is conditional. */
    @Test
    fun `name and stat pair fits its tile at every size and text scale`() {
        var worst = 0f
        var worstAt = ""
        for (size in sizes()) {
            val avail = content(size)
            for (ts in scales) {
                val nameH = Scale.lineHeight(Scale.titleSp(size).value, ts)
                val statH = Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                val demand = if (nameH + statH <= avail) nameH + statH else nameH
                val over = (demand - avail).value
                if (over > worst) { worst = over; worstAt = "$size @${ts}x" }
            }
        }
        assertTrue(worst <= 0.01f, "name/stat overflows by ${worst}dp at $worstAt")
    }

    /**
     * The wide-MEDIUM bar layout: header, bar, info rows sized by
     * [Scale.infoRowsIn] against what [Scale.ringRoom] left, then buttons.
     */
    @Test
    fun `wide medium bar layout fits its tile at every size and text scale`() {
        // Mirrors MediumWideLayout's own bar branch, including the map --
        // this used to hand the SAME leftover room to both the info rows
        // AND the map independently (rows sized from the full
        // room-minus-bar, then the map ALSO capped at that same full
        // amount), which this test never caught because it didn't model
        // the map's contribution to `demand` at all. tallSplit's own
        // map-then-rows division is what MediumWideLayout now uses, so
        // this mirrors that instead of a hand-rolled infoRowsIn call.
        var worst = 0f
        var worstAt = ""
        for (size in sizes()) {
            if (tierFor(size) != WidgetTier.MEDIUM_WIDE) continue
            for (ts in scales) {
                for (hasHeader in listOf(true, false)) {
                    for (wantMap in listOf(false, true)) {
                        val avail = content(size)
                        val barH = Scale.barHeight(size)
                        val room = Scale.ringRoom(testFrame(size, ts), hasHeader, false, 18.dp)
                        val restAfterBar = (room - barH).coerceAtLeast(0.dp)
                        val split = Scale.tallSplit(size, restAfterBar, capRows = 2, textScale = ts, wantMap = wantMap)
                        val header = if (hasHeader) {
                            Scale.lineHeight(Scale.titleSp(size).value, ts) +
                                Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                        } else 0.dp
                        val rowsH = Scale.infoBlockHeight(size, split.rows, ts)
                        var demand = header + 6.dp + barH + Scale.buttonHeight(size) + split.map
                        if (split.rows > 0) demand += 6.dp + rowsH
                        val over = (demand - avail).value
                        if (over > worst) { worst = over; worstAt = "$size @${ts}x header=$hasHeader map=$wantMap" }
                    }
                }
            }
        }
        assertTrue(worst <= 0.01f, "wide-medium bar layout overflows by ${worst}dp at $worstAt")
    }

    /** [Scale.ringRoom] floors at zero rather than at a minimum ring, and
     *  [Scale.ring] turns too little room into NO ring -- the contract that
     *  lets a cramped column drop the ring instead of drawing one that
     *  overflows. A ring is either zero or fits the room it was given. */
    @Test
    fun `ring is zero or fits the room it was given`() {
        for (size in sizes()) {
            for (ts in scales) {
                val room = Scale.ringRoom(testFrame(size, ts), true, true, 12.dp)
                assertTrue(room.value >= 0f, "negative ring room at $size @${ts}x")
                val ring = Scale.ring(size, room)
                assertTrue(
                    ring == 0.dp || ring <= room,
                    "ring ${ring} exceeds room ${room} at $size @${ts}x",
                )
            }
        }
    }

    /** The hero number never shrinks below legibility: [Scale.heroSpIn]
     *  returns null rather than a number too small to be a hero, which is what
     *  makes BarHero's decline path reachable instead of it drawing a 6sp
     *  "82%". */
    @Test
    fun `hero size is null or legible`() {
        for (size in sizes()) {
            val avail = content(size)
            val barH = Scale.barHeight(size)
            for (ts in scales) {
                val sp = Scale.heroSpIn(size, avail, barH + 4.dp, ts) ?: continue
                assertTrue(sp >= Scale.HERO_MIN_SP, "hero ${sp}sp below floor at $size @${ts}x")
            }
        }
    }

    /** [Scale.buttonHeight] is capped by the padded content box, so a
     *  short-but-wide strip can't be handed a button taller than the tile --
     *  progress() reads the SHORTER side, which is precisely the side that
     *  has to hold it. */
    @Test
    fun `button height fits the padded content box`() {
        for (size in sizes()) {
            val avail = content(size)
            val h = Scale.buttonHeight(size)
            assertTrue(h <= avail || avail.value < 16f, "button ${h} exceeds ${avail} at $size")
        }
    }

    /**
     * A tall tier's three claims -- ring, info rows, map -- never sum past
     * the column they share, and the map is either a real map or nothing.
     *
     * This is the invariant the tall tiers used to break: [Scale.ringHero]
     * takes everything it is offered, so sizing it from the whole column and
     * then handing the map a weighted slot underneath gave the map zero
     * height on every tile whose ring was room-bound. Turning the location
     * option on produced no map at all, which is indistinguishable from the
     * option not working.
     */
    @Test
    fun `tall split fits its column and leaves a usable map`() {
        for (size in sizes()) {
            for (ts in scales) {
                val room = Scale.ringRoom(testFrame(size, ts), true, true, 20.dp)
                for (wantMap in listOf(false, true)) {
                    val s = Scale.tallSplit(size, room, capRows = 5, textScale = ts, wantMap = wantMap)
                    val used = s.ring + s.map + Scale.infoBlockHeight(size, s.rows, ts)
                    assertTrue(
                        used.value <= room.value + 0.5f,
                        "tall split uses ${used} of ${room} at $size @${ts}x map=$wantMap",
                    )
                    if (!wantMap) {
                        assertTrue(s.map == 0.dp, "map reserved with no map at $size")
                    } else {
                        assertTrue(
                            s.map == 0.dp || s.map >= Scale.MAP_MIN,
                            "map ${s.map} is a sliver at $size @${ts}x",
                        )
                    }
                }
            }
        }
    }

    /** Asking for a map never costs the ring more than the map is worth: a
     *  tile with room for a gauge still has one once the map is reserved,
     *  rather than the reserve quietly consuming the whole column. */
    @Test
    fun `reserving a map leaves the ring the larger share`() {
        for (size in sizes()) {
            for (ts in scales) {
                val room = Scale.ringRoom(testFrame(size, ts), true, true, 20.dp)
                val map = Scale.mapReserve(size, room, wantMap = true)
                assertTrue(
                    map.value <= room.value * 0.35f + 0.01f,
                    "map ${map} claims more than a third of ${room} at $size @${ts}x",
                )
            }
        }
    }

    /**
     * MapModule's own leading 8dp Spacer plus its Image never together
     * exceed the room its caller reserved for it via [Scale.mapReserve].
     *
     * MapModule used to cap the Image at `room` itself, on top of the
     * unconditional 8dp Spacer drawn right before it -- so whenever
     * mapReserve's own `room * 0.35f` cap was the binding constraint (not
     * `mapHeight + 8.dp`, the case it was clearly meant to pre-pay the
     * spacer for), the module consumed exactly 8dp more than it was ever
     * given. Mirrors MapModule's real formula exactly: `imageHeight =
     * min(mapHeight, (room - 8.dp).coerceAtLeast(0.dp))`, guarded the same
     * way (`imageHeight >= Scale.MAP_MIN` or draw nothing).
     */
    @Test
    fun `map module never spends more than its reserved room`() {
        for (size in sizes()) {
            for (ts in scales) {
                val room = Scale.ringRoom(testFrame(size, ts), true, true, 20.dp)
                val mapRoom = Scale.mapReserve(size, room, wantMap = true)
                if (mapRoom <= 0.dp) continue
                val imageHeight = minOf(Scale.mapHeight(size), (mapRoom - 8.dp).coerceAtLeast(0.dp))
                if (imageHeight < Scale.MAP_MIN) continue
                val consumed = 8.dp + imageHeight
                assertTrue(
                    consumed.value <= mapRoom.value + 0.01f,
                    "map module spends ${consumed} of reserved ${mapRoom} at $size @${ts}x",
                )
            }
        }
    }

    /**
     * The two column-shaped compact tiers fit their tile.
     *
     * Both used to cap the ring against an axis that wasn't the one holding
     * the column -- CompactSquare against 55% of the height, CompactTallNarrow
     * against the width -- so neither ever subtracted the name above or the
     * stat/buttons below. That overran the tile on 498 COMPACT_SQUARE
     * configurations, worst by 33dp. RemoteViews doesn't clip an overfull
     * Column, it lets the children bleed past the bottom edge, so the symptom
     * is text hanging off the widget rather than anything caught in review.
     *
     * Scoped by [tierFor], because these are per-tier layouts and the budgets
     * only have to hold where the tier is actually reached: a 40x40 tile's
     * button row alone fills its whole content box, which is fine because
     * nothing that small ever routes here.
     */
    @Test
    fun `compact column tiers fit their tile`() {
        for (size in sizes()) {
            val tier = tierFor(size)
            if (tier != WidgetTier.COMPACT_SQUARE && tier != WidgetTier.COMPACT_TALL_NARROW) continue
            val budget = content(size)
            for (ts in scales) {
                val name = Scale.lineHeight(Scale.titleSp(size).value, ts)
                if (tier == WidgetTier.COMPACT_SQUARE) {
                    // name + ring + one stat row
                    val left = (budget - name - 8.dp).coerceAtLeast(0.dp)
                    val rows = Scale.infoRowsIn(size, left, ts, cap = 1)
                    val ring = Scale.ring(
                        size,
                        minOf(left - Scale.infoBlockHeight(size, rows, ts), size.width - 8.dp),
                    )
                    val used = name + 8.dp + ring + Scale.infoBlockHeight(size, rows, ts)
                    assertTrue(
                        used.value <= budget.value + 0.5f,
                        "compact square column ${used} exceeds ${budget} at $size @${ts}x",
                    )
                } else {
                    // COMPACT_TALL_NARROW, via the real Scale.tallColumn the composable
                    // now calls. This branch used to model the tier as
                    // `ringRoom(spacers = 8.dp) - name` plus a single `buttonHeight` -- a
                    // shape CompactTallNarrowLayout stopped having when it moved to
                    // budget/buttonZone/tallSplit, so it was asserting against arithmetic
                    // no composable ran and could not fail for any real reason.
                    val column = Scale.tallColumn(
                        size,
                        nameHeight = name + 4.dp,
                        buttonOverhead = 8.dp,
                        buttonTrailingGap = 4.dp,
                        buttonCap = 4,
                    )
                    val split = Scale.tallSplit(
                        size, column.heroRoom, capRows = 1, textScale = ts, wantMap = false,
                    )
                    val ring = minOf(split.ring, size.width - 12.dp)
                    val used = (name + 4.dp) + column.buttonZone + ring +
                        Scale.infoBlockHeight(size, split.rows, ts)
                    assertTrue(
                        used.value <= budget.value + 0.5f,
                        "compact tall-narrow column ${used} exceeds ${budget} at $size @${ts}x",
                    )
                }
            }
        }
    }


    /**
     * RAIL, COMPACT_TALL_NARROW and COMPACT_TALL: the three tiers rewritten
     * to stop wasting most of a tall tile on empty photo (reported from real
     * devices, six screenshots in one batch -- a small ring-and-button
     * cluster centred in a huge amount of nothing). Mirrors each layout's own
     * budget arithmetic exactly, including [Scale.maxStackedButtons] -- the
     * function that exists because an EARLIER version of this exact fix
     * reserved a flat "up to six buttons" without checking whether six
     * buttons actually fit, which overflowed near Rail's own 220dp floor
     * before this test caught it.
     *
     * This test itself then caught a SECOND overflow the same fix
     * introduced: each layout renders a forced Spacer right before its
     * button row (and RAIL another right before its map) that is NOT part
     * of buttonZone's own reservation. When the button stack alone already
     * consumed the whole budget, heroRoom clamped to 0.dp -- and clamping
     * meant the margin below was never actually subtracted from anything,
     * so those forced spacers rendered on top of an already-full tile.
     * Fixed by folding the extra spacer into each maxStackedButtons overhead
     * (so the button count itself leaves room) and widening the margin to
     * match -- both here, and in the constant CarWidget.kt derives it from.
     *
     * Every combination of configured-action count (0..7, the full action
     * set the config screen can produce) and map on/off, at every text
     * scale -- the same
     * sweep that found the bug, now enforced in CI against the real
     * functions rather than a one-off script.
     */
    @Test
    fun `tall narrow tiers fit their tile at every action count`() {
        // Each branch now calls WidgetLayout.tallPlan -- the SAME call the composable renders
        // from -- so the per-tier reservation constants (name, button overhead/gap/cap, row cap)
        // live in exactly ONE place. The previous version still hand-copied those constants into
        // Scale.tallColumn calls here, which was the last remaining drift vector (it had gone
        // stale once, modelling COMPACT_TALL's overhead as 12 while the code used 20). What still
        // legitimately lives here is the RENDER-TREE spacer arithmetic -- the gaps the composable
        // emits around the plan's slots -- since those are the composable's layout, not the plan.
        // The tall tiers never draw a map, so wantMap stays false (matching WidgetLayout's spec).
        for (size in sizes()) {
            val tier = tierFor(size)
            if (tier != WidgetTier.RAIL && tier != WidgetTier.COMPACT_TALL_NARROW && tier != WidgetTier.COMPACT_TALL) continue
            val budget = content(size)
            for (ts in scales) {
                for (actionCount in 0..7) {
                    val plan = WidgetLayout.tallPlan(tier, size, ts, actionCount)
                    val split = plan.split
                    val used = when (tier) {
                        WidgetTier.RAIL -> {
                            // name/rows/map are all absent on RAIL; ring + button stack + the
                            // forced pre-button Spacer(8) when buttons show.
                            val spacerButtons = if (plan.buttonCount > 0) 8.dp else 0.dp
                            plan.buttonZone + split.ring + spacerButtons
                        }
                        WidgetTier.COMPACT_TALL_NARROW -> {
                            val spacerRows = if (split.rows > 0) 4.dp else 0.dp
                            val spacerButtons = if (plan.buttonCount > 0) 4.dp else 0.dp
                            plan.nameHeight + plan.buttonZone + split.ring +
                                Scale.infoBlockHeight(size, split.rows, ts) + spacerRows + spacerButtons
                        }
                        else -> { // COMPACT_TALL
                            val spacerRing = if (split.ring > 0.dp) 6.dp else 0.dp
                            val spacerButtons = if (plan.buttonCount > 0) 8.dp else 0.dp
                            plan.nameHeight + split.ring + spacerRing +
                                Scale.infoBlockHeight(size, split.rows, ts) + plan.buttonZone + spacerButtons
                        }
                    }
                    assertTrue(
                        used.value <= budget.value + 0.5f,
                        "$tier ${used} exceeds ${budget} at $size @${ts}x actions=$actionCount",
                    )
                }
            }
        }
    }

    /**
     * LARGE_WIDE and XL_WIDE: rebuilt to run [Scale.heroSpIn]'s bar-hero
     * treatment the full tile width under the header instead of confining
     * it to a ring-shaped side column (see CarWidget.kt's own note on why --
     * the confined version read as an undersized bar with empty space above
     * and below it, reported from a real device).
     *
     * BarHero's own budget check (`heroH + 4.dp + barH + subH <= avail`)
     * assumes it is handed the REAL remaining room, which used to be a given
     * when it was the row's only content (Banner/CompactWide) but isn't
     * automatic here: this column also carries a header, footer, info rows
     * and a button row. This test mirrors exactly what the two layouts pass
     * as `avail` (tallSplit's own leftover-after-rows split of ringRoom) and
     * confirms the resulting hero block, plus everything else stacked in the
     * column, still fits.
     */
    @Test
    fun `wide LARGE and XL tiers fit their bar hero at every size`() {
        // wantMap sweeps both ways: these two tiers used to hand the map
        // its own full natural height with NOTHING subtracted from the
        // budget the hero and rows were sized against (a fixed-height
        // MapModule avoided the "weighted element swallows a sibling's
        // room" failure mode, but did nothing to stop the SUM of the
        // column's own content from exceeding the tile once the map's
        // height was added on top of an already-fully-allocated ringRoom).
        // wantMap = true is what makes tallSplit actually reserve room for
        // it before dividing the rest between hero and rows.
        for (size in sizes()) {
            val tier = tierFor(size)
            if (tier != WidgetTier.LARGE_WIDE && tier != WidgetTier.XL_WIDE) continue
            val budget = content(size)
            // The per-tier spacer allowance is the render-tree total this test's `spacers` term
            // below must also account for; the SPLIT itself now comes from WidgetLayout.wideBarPlan,
            // the same call the composable renders from, so capRows and the ringRoom spacer arg
            // can't drift between the two. header+footer are both shown here (the tier's common
            // case), matching the composable's config-gated call with both flags on.
            val spacers = if (tier == WidgetTier.LARGE_WIDE) 30.dp else 42.dp
            for (ts in scales) {
                for (wantMap in listOf(false, true)) {
                    val split = WidgetLayout.wideBarPlan(
                        tier, testFrame(size, ts), showHeader = true, showFooter = true, wantMap = wantMap,
                    ).split
                    val barH = Scale.barHeight(size)
                    val heroSp = Scale.heroSpIn(size, split.ring, barH + 4.dp, ts)
                    val heroBlock = if (heroSp == null) {
                        0.dp
                    } else {
                        val heroH = Scale.lineHeight(heroSp, 1f)
                        val subH = Scale.lineHeight(Scale.subtitleSp(size).value, ts) + 4.dp
                        val showSub = heroH + 4.dp + barH + subH <= split.ring
                        heroH + 4.dp + barH + (if (showSub) subH else 0.dp)
                    }
                    val header = Scale.lineHeight(Scale.titleSp(size).value, ts) + Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                    val footer = Scale.lineHeight(Scale.subtitleSp(size).value, ts) + 6.dp
                    val used = header + footer + spacers + heroBlock +
                        Scale.infoBlockHeight(size, split.rows, ts) + split.map + Scale.buttonHeight(size)
                    assertTrue(
                        used.value <= budget.value + 0.5f,
                        "$tier ${used} exceeds ${budget} at $size @${ts}x map=$wantMap",
                    )
                }
            }
        }
    }

    /**
     * XL_TALL: the primaryValue line under the ring ("69% · 219 mi") is
     * real, known-size content that was never subtracted from the budget
     * tallSplit divides between the ring, the info rows and the map --
     * ringRoom's own spacers argument only ever covered the fixed spacers
     * in the column (14 + 8 + 14 = 36.dp), not the text line sitting
     * between two of them. Rebuilding this arithmetic outside the
     * codebase found up to 46dp of real overflow on a real XL_TALL size,
     * enough to push the map and every button off the bottom of the tile's
     * own bounds -- confirmed here at every size and text scale, map on
     * and off.
     */
    @Test
    fun `XL tall tier fits its primary-value line at every size`() {
        for (size in sizes()) {
            if (tierFor(size) != WidgetTier.XL_TALL) continue
            val budget = content(size)
            for (ts in scales) {
                for (wantMap in listOf(false, true)) {
                    // The split AND the reserved primaryValue line both come from the same
                    // WidgetLayout.ringHeroPlan the composable renders from (header+footer on,
                    // its common case). The render-tree spacers (14/8/14) are the composable's
                    // Column layout, modelled here.
                    val plan = WidgetLayout.ringHeroPlan(
                        WidgetTier.XL_TALL, testFrame(size, ts), showHeader = true, showFooter = true, wantMap = wantMap,
                    )
                    val split = plan.split
                    val header = Scale.lineHeight(Scale.titleSp(size).value, ts) + Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                    val footer = Scale.lineHeight(Scale.subtitleSp(size).value, ts) + 6.dp
                    val used = header + footer + 14.dp + split.ring + 8.dp + plan.primaryValueHeight + 14.dp +
                        Scale.infoBlockHeight(size, split.rows, ts) + split.map + Scale.buttonHeight(size)
                    assertTrue(
                        used.value <= budget.value + 0.5f,
                        "XL_TALL ${used} exceeds ${budget} at $size @${ts}x map=$wantMap",
                    )
                }
            }
        }
    }

    /**
     * The three SQUARE tiers fit their whole column, header and footer on.
     *
     * These were the one tier family with no direct full-column sweep: the
     * `tall`/`wide` families each had one above, but the square tiers were only
     * ever guarded indirectly through [Scale.squareSplit]'s own internal
     * invariant test (`ring + rows + map <= room`). That proves the SPLIT is
     * sound, not that the tier ASSEMBLES soundly -- the header, footer, the
     * per-branch spacers and the single-row button reservation stacked on top of
     * the split were never checked to fit the tile together. This mirrors each
     * square layout's real Column: [Scale.squareSplit] with the same room,
     * capRows, sideBySide threshold and spacer count each composable passes, then
     * everything the Column actually stacks. `sideBySide` is swept both ways so
     * both RingWithContent branches (rows beside the ring vs stacked under it)
     * are covered -- the stacked branch is the one that has to fit rows AND ring
     * in the band, so it is where an assembly overflow would surface.
     *
     * showHeader/showFooter both sweep on/off: [ringRoom] takes the header and
     * footer out of the column only when they are shown, so a tile with them off
     * has MORE room, not less -- but the composable still emits the same spacer
     * literals, and this confirms the tighter (shown) case is the binding one.
     */
    @Test
    fun `square tiers fit their whole column at every size`() {
        for (size in sizes()) {
            val tier = tierFor(size)
            // rowWidth now comes from WidgetLayout.squareRowWidth -- the SAME source the
            // composable's minRowWidth and squarePlan's sideBySide read, so this can't drift
            // from either. The split itself is WidgetLayout.squarePlan, the exact call the
            // composable renders from; only the render-tree assembly (spacers, ringRow shape)
            // is modelled here, since that's the composable's Column, not the plan.
            val rowWidth = when (tier) {
                WidgetTier.MEDIUM_SQUARE, WidgetTier.LARGE_SQUARE, WidgetTier.XL_SQUARE ->
                    WidgetLayout.squareRowWidth(tier)
                else -> continue
            }
            // The column's fixed spacer allowance, matching the composable's own Spacer calls:
            //  MEDIUM_SQUARE: header + 8 + ringRow + weightSpacer + 8 + buttons  (16 total)
            //  LARGE_SQUARE:  header + footer + 10 + ringRow + weightSpacer + map + 10 + buttons
            //  XL_SQUARE:     header + footer + 12 + ringRow + weightSpacer + map + buttons
            val spacerArg = when (tier) {
                WidgetTier.MEDIUM_SQUARE -> 16.dp
                WidgetTier.LARGE_SQUARE -> 20.dp
                else -> 24.dp
            }
            val hasFooterTier = tier != WidgetTier.MEDIUM_SQUARE
            val wantMapTier = tier != WidgetTier.MEDIUM_SQUARE // MEDIUM_SQUARE never draws a map
            val budget = content(size)
            for (ts in scales) {
                for (showHeader in listOf(true, false)) {
                    for (showFooter in if (hasFooterTier) listOf(true, false) else listOf(false)) {
                        for (wantMap in if (wantMapTier) listOf(false, true) else listOf(false)) {
                            val split = WidgetLayout.squarePlan(
                                tier, testFrame(size, ts),
                                showHeader = showHeader, showFooter = showFooter, wantMap = wantMap,
                            ).split
                            // The ring row is as tall as the taller of the ring and the info
                            // block when they sit side by side; when stacked, squareSplit has
                            // already subtracted the rows from the ring's room, so ring +
                            // rows + its own 8dp internal gap is the row's real height.
                            val sideBySide = size.width >= rowWidth
                            val rowsH = Scale.infoBlockHeight(size, split.rows, ts)
                            val ringRow = if (sideBySide) maxOf(split.ring, rowsH)
                                else split.ring + rowsH + (if (split.rows > 0) 8.dp else 0.dp)
                            val header = if (showHeader) {
                                Scale.lineHeight(Scale.titleSp(size).value, ts) +
                                    Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                            } else 0.dp
                            val footer = if (showFooter) {
                                Scale.lineHeight(Scale.subtitleSp(size).value, ts) + 6.dp
                            } else 0.dp
                            val used = header + footer + spacerArg + ringRow + split.map + Scale.buttonHeight(size)
                            assertTrue(
                                used.value <= budget.value + 0.5f,
                                "$tier ${used} exceeds ${budget} at $size @${ts}x header=$showHeader footer=$showFooter map=$wantMap",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The action-button geometry the widget actually draws with.
 *
 * These could not be written before: the capacity and per-button heights were computed
 * inside the ActionButtons composable, which a unit test cannot reach. WidgetScaleTest
 * could only ever sweep [Scale], so the widget's real button arithmetic -- the numbers
 * that decide whether a configured button appears and how tall it is -- was the widest
 * untested surface in the widget. Moving it into [Scale] is what makes this file able to
 * assert on it.
 *
 * The invariant that matters is the one the old tests structurally could not express: the
 * drawn button block must never be taller than the height its tier reserved. RemoteViews
 * does not clip an overflowing Column, so an overflow here does not get cut off at the
 * tile edge -- it bleeds past it.
 */
class ActionButtonGeometryTest {

    private val scales = listOf(0.8f, 1.0f, 1.2f, 1.4f)

    /** Same 2dp grid over the manifest's permitted range as WidgetScaleTest. */
    private fun sizes(): Sequence<DpSize> = sequence {
        var w = 40
        while (w <= 640) {
            var h = 40
            while (h <= 640) {
                yield(DpSize(w.dp, h.dp))
                h += 2
            }
            w += 2
        }
    }

    /** A STACK of buttons must fit the height it was given, at every tile size, every
     *  budget, and every count. This is the assertion whose absence let the 16dp floor
     *  win against a smaller reservation. */
    @Test
    fun `stacked buttons never exceed the reserved height`() {
        for (size in sizes()) {
            // Budgets from nothing to the whole tile, including the tiny ones where the
            // old floor overflowed.
            for (budgetDp in listOf(0, 4, 10, 16, 24, 40, 80, size.height.value.toInt())) {
                val budget = budgetDp.dp
                for (count in 1..7) {
                    val each = Scale.stackedButtonHeight(size, budget, count)
                    assertTrue(
                        each <= budget + 0.01f.dp,
                        "one of $count stacked buttons is ${each.value}dp in a ${budgetDp}dp budget at $size",
                    )
                }
            }
        }
    }

    /** A ROW of buttons must fit the height it was given. Was buttonHeight(size), which
     *  is capped by the tile rather than by the tier's reservation. */
    @Test
    fun `a button row never exceeds the reserved height`() {
        for (size in sizes()) {
            for (budgetDp in listOf(0, 4, 10, 16, 24, 40, 80, size.height.value.toInt())) {
                val budget = budgetDp.dp
                val h = Scale.rowButtonHeight(size, budget)
                assertTrue(
                    h <= budget + 0.01f.dp,
                    "button row is ${h.value}dp in a ${budgetDp}dp budget at $size",
                )
            }
        }
    }

    /** Capacity is never negative, and never claims room for a button that could not be
     *  laid out at this tile's own minimum width / height. */
    @Test
    fun `capacity is non-negative and honours the minimum button size`() {
        for (size in sizes()) {
            val across = Scale.buttonsAcross(size, size.width)
            val down = Scale.buttonsDown(size, size.height)
            assertTrue(across >= 0, "negative across at $size")
            assertTrue(down >= 0, "negative down at $size")
            if (across > 0) {
                val gap = Scale.buttonGap(size)
                val needed = Scale.minButtonWidth(size) * across + gap * (across - 1)
                assertTrue(
                    needed <= size.width + 0.01f.dp,
                    "$across buttons across need ${needed.value}dp of ${size.width.value}dp at $size",
                )
            }
            if (down > 0) {
                val gap = Scale.buttonGap(size)
                val needed = Scale.buttonHeight(size) * down + gap * (down - 1)
                assertTrue(
                    needed <= size.height + 0.01f.dp,
                    "$down buttons down need ${needed.value}dp of ${size.height.value}dp at $size",
                )
            }
        }
    }

    /**
     * At least one button is always drawn, even where capacity says none fits -- a
     * deliberate trade recorded in ActionButtons ("a missing button can't be pressed").
     * Pinned so the clamping added alongside it can't be mistaken for licence to drop it.
     */
    @Test
    fun `one button is still drawn when nothing fits`() {
        assertEquals(1, Scale.buttonsForced(capacity = 0, configured = 4))
        assertEquals(1, Scale.buttonsForced(capacity = 1, configured = 4))
        assertEquals(3, Scale.buttonsForced(capacity = 3, configured = 4))
        // Never more than the user configured.
        assertEquals(2, Scale.buttonsForced(capacity = 7, configured = 2))
        assertEquals(0, Scale.buttonsForced(capacity = 5, configured = 0))
    }

    /** The forced button still fits: this is the pairing that makes the trade safe rather
     *  than an overflow. */
    @Test
    fun `the forced button fits the budget it was given`() {
        for (size in sizes()) {
            for (budgetDp in listOf(0, 2, 6, 10, 14)) {
                val budget = budgetDp.dp
                val n = Scale.buttonsForced(Scale.buttonsDown(size, budget), configured = 4)
                assertEquals(1, n, "expected the forced single button at ${budgetDp}dp on $size")
                assertTrue(
                    Scale.stackedButtonHeight(size, budget, n) <= budget + 0.01f.dp,
                    "forced button overflows a ${budgetDp}dp budget at $size",
                )
            }
        }
    }
}

/** [Scale.Frame] for a test that is not exercising the pill corner or the car
 *  switcher. Both default to absent, which is exactly the behaviour the
 *  assertions in this file were written against before Frame existed -- so the
 *  signature change moves no goalposts. The pill and switcher cases are swept
 *  in WidgetFrameTest instead.
 */
internal fun testFrame(
    size: androidx.compose.ui.unit.DpSize,
    textScale: Float,
    pillCorner: Boolean = false,
    hasSwitcher: Boolean = false,
): Scale.Frame = Scale.Frame(size, textScale, pillCorner, hasSwitcher)
