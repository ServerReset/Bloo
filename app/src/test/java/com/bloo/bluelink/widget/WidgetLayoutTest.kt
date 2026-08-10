package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the per-tier CONSTANTS in [WidgetLayout] against the literal values each tier's composable
 * used before the plan layer existed.
 *
 * WidgetScaleTest already sweeps that the plans FIT every tile -- but "fits" would still pass if
 * a spec constant were quietly changed to a different value that also happens to fit. This test
 * is the other half: it recomputes each plan's expected result from a direct [Scale] call with
 * the documented literals inline, so editing any WidgetLayout spec value (a spacer allowance, a
 * button overhead, a capRows, a row-width threshold) makes the plan output diverge from the
 * literal expectation and fails here -- immediately and legibly, naming the tier, rather than as
 * a subtle overflow somewhere in the 90,601-size sweep. Each tier is checked at one representative
 * size (chosen so tierFor routes it to that tier) across the four text scales.
 */
class WidgetLayoutTest {

    private val scales = listOf(0.8f, 1.0f, 1.2f, 1.4f)

    // Representative sizes, each verified to route to its tier via tierFor (asserted below).
    private val rail = DpSize(40.dp, 640.dp)
    private val compactTallNarrow = DpSize(100.dp, 160.dp)
    private val compactTall = DpSize(130.dp, 300.dp)
    private val mediumSquare = DpSize(160.dp, 160.dp)
    private val largeSquare = DpSize(240.dp, 240.dp)
    private val xlSquare = DpSize(320.dp, 320.dp)
    private val largeWide = DpSize(320.dp, 200.dp)
    private val xlWide = DpSize(500.dp, 300.dp)
    private val mediumTall = DpSize(160.dp, 220.dp)
    private val largeTall = DpSize(240.dp, 320.dp)
    private val xlTall = DpSize(300.dp, 500.dp)
    private val mediumWide = DpSize(220.dp, 160.dp)

    @Test
    fun representativeSizesRouteToTheirTiers() {
        assertEquals(WidgetTier.RAIL, tierFor(rail))
        assertEquals(WidgetTier.COMPACT_TALL_NARROW, tierFor(compactTallNarrow))
        assertEquals(WidgetTier.COMPACT_TALL, tierFor(compactTall))
        assertEquals(WidgetTier.MEDIUM_SQUARE, tierFor(mediumSquare))
        assertEquals(WidgetTier.LARGE_SQUARE, tierFor(largeSquare))
        assertEquals(WidgetTier.XL_SQUARE, tierFor(xlSquare))
        assertEquals(WidgetTier.LARGE_WIDE, tierFor(largeWide))
        assertEquals(WidgetTier.XL_WIDE, tierFor(xlWide))
        assertEquals(WidgetTier.MEDIUM_TALL, tierFor(mediumTall))
        assertEquals(WidgetTier.LARGE_TALL, tierFor(largeTall))
        assertEquals(WidgetTier.XL_TALL, tierFor(xlTall))
        assertEquals(WidgetTier.MEDIUM_WIDE, tierFor(mediumWide))
    }

    // ---- tallPlan: name-or-not, button overhead/trailing/cap, capRows ----------

    @Test
    fun tallPlanMatchesTheLiteralPerTierConstants() {
        for (ts in scales) {
            for (actions in 0..7) {
                // RAIL: no name; overhead 16, trailing 8, cap all; capRows 0.
                assertTallEquals(rail, ts, actions, nameHeight = 0.dp, overhead = 16.dp, trailing = 8.dp, cap = actions, capRows = 0)
                // COMPACT_TALL_NARROW: name + 4dp; overhead 8, trailing 4, cap min(,4); capRows 1.
                val ctnName = Scale.lineHeight(Scale.titleSp(compactTallNarrow).value, ts) + 4.dp
                assertTallEquals(compactTallNarrow, ts, actions, nameHeight = ctnName, overhead = 8.dp, trailing = 4.dp, cap = minOf(actions, 4), capRows = 1)
                // COMPACT_TALL: name (no extra); overhead 20, trailing 12, cap all; capRows 4.
                val ctName = Scale.lineHeight(Scale.titleSp(compactTall).value, ts)
                assertTallEquals(compactTall, ts, actions, nameHeight = ctName, overhead = 20.dp, trailing = 12.dp, cap = actions, capRows = 4)
            }
        }
    }

    private fun assertTallEquals(
        size: DpSize, ts: Float, actions: Int,
        nameHeight: androidx.compose.ui.unit.Dp, overhead: androidx.compose.ui.unit.Dp,
        trailing: androidx.compose.ui.unit.Dp, cap: Int, capRows: Int,
    ) {
        val tier = tierFor(size)
        val expectedColumn = Scale.tallColumn(size, nameHeight = nameHeight, buttonOverhead = overhead, buttonTrailingGap = trailing, buttonCap = cap)
        val expectedSplit = Scale.tallSplit(size, expectedColumn.heroRoom, capRows = capRows, textScale = ts, wantMap = false)
        val plan = WidgetLayout.tallPlan(tier, size, ts, actions)
        assertEquals(expectedColumn, plan.column, "$tier column @${ts}x actions=$actions")
        assertEquals(expectedSplit, plan.split, "$tier split @${ts}x actions=$actions")
        assertEquals(nameHeight, plan.nameHeight, "$tier nameHeight @${ts}x")
    }

    // ---- squarePlan: rowWidth, spacer allowance, capRows, footer ---------------

    @Test
    fun squarePlanMatchesTheLiteralPerTierConstants() {
        data class Spec(val size: DpSize, val rowWidth: androidx.compose.ui.unit.Dp, val spacer: androidx.compose.ui.unit.Dp, val capRows: Int, val hasFooter: Boolean)
        val specs = listOf(
            Spec(mediumSquare, 140.dp, 16.dp, 3, hasFooter = false),
            Spec(largeSquare, 220.dp, 20.dp, 4, hasFooter = true),
            Spec(xlSquare, 260.dp, 24.dp, 4, hasFooter = true),
        )
        for (ts in scales) for (showHeader in listOf(true, false)) for (showFooter in listOf(true, false)) for (wantMap in listOf(true, false)) {
            for (s in specs) {
                val tier = tierFor(s.size)
                assertEquals(s.rowWidth, WidgetLayout.squareRowWidth(tier), "$tier rowWidth")
                val room = Scale.ringRoom(Scale.Frame(s.size, ts, false, false), showHeader, s.hasFooter && showFooter, s.spacer)
                val expected = Scale.squareSplit(s.size, room, capRows = s.capRows, textScale = ts, wantMap = wantMap, sideBySide = s.size.width >= s.rowWidth)
                val actual = WidgetLayout.squarePlan(tier, Scale.Frame(s.size, ts, false, false), showHeader, showFooter, wantMap).split
                assertEquals(expected, actual, "$tier square split @${ts}x header=$showHeader footer=$showFooter map=$wantMap")
            }
        }
    }

    // ---- wideBarPlan / ringHeroPlan / mediumWideBarPlan ------------------------

    @Test
    fun wideBarPlanMatchesTheLiteralPerTierConstants() {
        for (ts in scales) for (showHeader in listOf(true, false)) for (showFooter in listOf(true, false)) for (wantMap in listOf(true, false)) {
            for ((size, spacer, capRows) in listOf(
                Triple(largeWide, 30.dp, 4),
                Triple(xlWide, 42.dp, WidgetInfoField.ALL.size),
            )) {
                val tier = tierFor(size)
                val frame = Scale.Frame(size, ts, false, false)
                val room = Scale.ringRoom(frame, showHeader, showFooter, spacer)
                val expected = Scale.tallSplit(size, room, capRows = capRows, textScale = ts, wantMap = wantMap)
                assertEquals(expected, WidgetLayout.wideBarPlan(tier, frame, showHeader, showFooter, wantMap).split, "$tier wide @${ts}x h=$showHeader f=$showFooter m=$wantMap")
            }
        }
    }

    @Test
    fun ringHeroPlanMatchesTheLiteralPerTierConstants() {
        for (ts in scales) for (showHeader in listOf(true, false)) for (showFooter in listOf(true, false)) for (wantMap in listOf(true, false)) {
            // MEDIUM_TALL: no footer, base 16, no primaryValue, capRows 3.
            assertRingHero(mediumTall, ts, showHeader, showFooter, wantMap, hasFooter = false, base = 16.dp, primaryValue = false, capRows = 3)
            // LARGE_TALL: footer, base 20, no primaryValue, capRows 4.
            assertRingHero(largeTall, ts, showHeader, showFooter, wantMap, hasFooter = true, base = 20.dp, primaryValue = false, capRows = 4)
            // XL_TALL: footer, base 36 + one title line, capRows all.
            assertRingHero(xlTall, ts, showHeader, showFooter, wantMap, hasFooter = true, base = 36.dp, primaryValue = true, capRows = WidgetInfoField.ALL.size)
        }
    }

    private fun assertRingHero(
        size: DpSize, ts: Float, showHeader: Boolean, showFooter: Boolean, wantMap: Boolean,
        hasFooter: Boolean, base: androidx.compose.ui.unit.Dp, primaryValue: Boolean, capRows: Int,
    ) {
        val tier = tierFor(size)
        val frame = Scale.Frame(size, ts, false, false)
        val pvh = if (primaryValue) Scale.lineHeight(Scale.titleSp(size).value, ts) else 0.dp
        val room = Scale.ringRoom(frame, showHeader, hasFooter && showFooter, base + pvh)
        val expected = Scale.tallSplit(size, room, capRows = capRows, textScale = ts, wantMap = wantMap)
        val plan = WidgetLayout.ringHeroPlan(tier, frame, showHeader, showFooter, wantMap)
        assertEquals(expected, plan.split, "$tier ringHero split @${ts}x h=$showHeader f=$showFooter m=$wantMap")
        assertEquals(pvh, plan.primaryValueHeight, "$tier primaryValueHeight @${ts}x")
    }

    @Test
    fun mediumWideBarPlanMatchesTheLiteralConstants() {
        for (ts in scales) for (showHeader in listOf(true, false)) for (wantMap in listOf(true, false)) {
            val frame = Scale.Frame(mediumWide, ts, false, false)
            val barH = Scale.barHeight(mediumWide)
            val room = Scale.ringRoom(frame, showHeader, false, 18.dp)
            val restAfterBar = (room - barH).coerceAtLeast(0.dp)
            val expected = Scale.tallSplit(mediumWide, restAfterBar, capRows = 2, textScale = ts, wantMap = wantMap)
            val actual = WidgetLayout.mediumWideBarPlan(frame, showHeader, barH, wantMap)
            assertEquals(expected, actual, "MEDIUM_WIDE bar split @${ts}x header=$showHeader map=$wantMap")
        }
    }
}
