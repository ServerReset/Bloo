package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sweeps [tierFor] over the whole size space a widget can actually be
 * measured at, rather than spot-checking a handful of nominal cell sizes.
 *
 * This exists because the tier map has had real, shipped holes in it that
 * no spot check would have caught: an aspect-ratio gap between
 * COMPACT_SQUARE's old 1.33 ceiling and COMPACT_WIDE's `w >= 1.4h` floor
 * meant 20 sizes on a 5dp grid -- 190x140dp among them -- matched no band
 * at all and fell through to MICRO, rendering a fully-sized widget as a
 * single lock glyph. The bug was invisible in isolation and obvious the
 * moment the space was enumerated, which is exactly the shape of thing a
 * test should own.
 */
class WidgetTierTest {

    private companion object {
        /** The manifest's declared floor (car_widget_info.xml minWidth/Height). */
        const val MIN_DP = 40

        /** The manifest's declared ceiling (maxResizeWidth/Height). Sweeping
         *  only to 400 would be wrong rather than merely conservative:
         *  XL_TALL needs roughly h > 405 at w = 300 to satisfy its own aspect
         *  gate, so a 400dp bound cannot reach it and would report it as dead
         *  code. */
        const val MAX_DP = 640

        /** Step fine enough to land inside any real gap. The 20-size hole
         *  above was already visible at 5dp; 2dp is well under that. */
        const val STEP = 2
    }

    private fun sizes(): Sequence<Pair<Int, Int>> = sequence {
        var w = MIN_DP
        while (w <= MAX_DP) {
            var h = MIN_DP
            while (h <= MAX_DP) {
                yield(w to h)
                h += STEP
            }
            w += STEP
        }
    }

    private fun tier(w: Int, h: Int) = tierFor(DpSize(w.dp, h.dp))

    @Test
    fun `every size resolves to a tier`() {
        // tierFor's `when` is exhaustive by construction, so this really
        // guards against a future edit introducing a branch that throws or
        // returns something unexpected for an odd aspect ratio.
        val count = sizes().count { tier(it.first, it.second) in WidgetTier.entries }
        assertEquals(sizes().count(), count)
    }

    @Test
    fun `roomy sizes never fall through to the icon-only tiers`() {
        // MICRO and MICRO_TINY draw a glyph or ring and no text at all, which
        // is only ever the right answer for a tile barely past the manifest
        // floor. Anything with 90dp on BOTH sides has room for a real layout.
        // This is the assertion that would have caught the 190x140 bug.
        val holes = sizes()
            .filter { (w, h) -> minOf(w, h) >= 90 }
            .filter { (w, h) -> tier(w, h) == WidgetTier.MICRO || tier(w, h) == WidgetTier.MICRO_TINY }
            .toList()
        assertTrue(
            holes.isEmpty(),
            "sizes with >=90dp on both sides that render icon-only: " +
                "${holes.size}, e.g. ${holes.take(5).map { "${it.first}x${it.second}" }}",
        )
    }

    @Test
    fun `RAIL only takes genuinely strip-shaped tiles`() {
        // RAIL drops everything the other tiers keep (a header, and all
        // text), which is only an honest trade when the short axis really
        // has no room. A merely-large tall tile must not be flattened into a
        // strip and lose its layout. (BANNER has its own rule now -- see
        // `BANNER covers every one-row strip regardless of width` below --
        // since it's grid-driven, not aspect-driven.)
        val misrouted = sizes()
            .filter { (w, h) -> tier(w, h) == WidgetTier.RAIL }
            .filter { (w, h) -> minOf(w, h) >= 110 || h.toFloat() / w < 3f }
            .toList()
        assertTrue(
            misrouted.isEmpty(),
            "non-strip sizes routed to RAIL: ${misrouted.take(5).map { "${it.first}x${it.second}" }}",
        )
    }

    @Test
    fun `BANNER covers every one-row strip regardless of width`() {
        // The whole point of the grid-driven BANNER gate: a tile shorter
        // than one compact cell (80dp) is a one-row strip AT ANY width from
        // WidgetGrid's own 80dp floor up -- not just the wide ones the old
        // aspect-ratio gate happened to catch. This is the direct regression
        // test for the "110x40 fell through to MICRO_TINY" and "180x40 fell
        // through to COMPACT_WIDE_NARROW" bugs -- both nominal grid sizes
        // (2 and 3 columns by 1 row) now land on BANNER, which is what
        // WidgetGrid's own nominal-size table describes them as.
        for (cols in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            val nominal = WidgetGrid.nominalSize(cols, WidgetGrid.MIN_ROWS)
            assertEquals(
                WidgetTier.BANNER, tierFor(nominal),
                "${cols}x${WidgetGrid.MIN_ROWS} (nominal ${nominal.width.value}x${nominal.height.value}) " +
                    "should be BANNER, was ${tierFor(nominal)}",
            )
        }
    }

    @Test
    fun `every nominal grid cell from 2x1 to 7x7 lands on a real layout, never an icon`() {
        // The full space this rework exists to cover: every (cols, rows)
        // pair WidgetGrid names, at its own nominal dp size, must resolve to
        // a tier that actually draws something -- never MICRO/MICRO_TINY,
        // which show a glyph and nothing else. This is the direct sweep of
        // the "2 by 1, 3 by 1, ... 7 by 7" size list the widget is meant to
        // support cleanly.
        val holes = mutableListOf<String>()
        for (cols in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            for (rows in WidgetGrid.MIN_ROWS..WidgetGrid.MAX_ROWS) {
                val t = tierFor(WidgetGrid.nominalSize(cols, rows))
                if (t == WidgetTier.MICRO || t == WidgetTier.MICRO_TINY) holes += "${cols}x$rows -> $t"
            }
        }
        assertTrue(holes.isEmpty(), "grid cells with no real layout: $holes")
    }

    @Test
    fun `the square tiers never receive a lopsided size`() {
        // The *_SQUARE layouts split their width between a ring and a content
        // column side by side; handing one a strongly wide or tall tile would
        // squeeze one half. The wide/tall bands are supposed to claim those
        // shapes first, so nothing lopsided should reach a square tier.
        val squares = setOf(
            WidgetTier.COMPACT_SQUARE, WidgetTier.MEDIUM_SQUARE,
            WidgetTier.LARGE_SQUARE, WidgetTier.XL_SQUARE,
        )
        val bad = sizes()
            .filter { (w, h) -> tier(w, h) in squares }
            .filter { (w, h) -> maxOf(w.toFloat() / h, h.toFloat() / w) > 1.7f }
            .toList()
        assertTrue(
            bad.isEmpty(),
            "lopsided sizes routed to a square tier: ${bad.take(5).map { "${it.first}x${it.second}" }}",
        )
    }

    @Test
    fun `the tiniest tiers are reserved for genuinely tiny tiles`() {
        val tooBig = sizes()
            .filter { (w, h) -> tier(w, h) == WidgetTier.MICRO_TINY }
            .filter { (w, h) -> minOf(w, h) >= 60 }
            .toList()
        assertTrue(tooBig.isEmpty(), "MICRO_TINY used above its 60dp ceiling: ${tooBig.take(5)}")
    }

    @Test
    fun `every tier is reachable`() {
        // A tier no size can reach is dead code -- either its gate is wrong or
        // a band above it is swallowing everything it was meant to catch.
        val reached = sizes().map { (w, h) -> tier(w, h) }.toSet()
        val unreachable = WidgetTier.entries.filterNot { it in reached }
        assertTrue(unreachable.isEmpty(), "unreachable tiers: $unreachable")
    }

    /** How much a tier draws, coarsely. Deliberately by BAND and not by enum
     *  ordinal: the wide/tall/square variants inside a band are siblings, not
     *  a ranking, so a tile shifting between them as it grows is expected and
     *  correct rather than a demotion. */
    private fun band(tier: WidgetTier): Int = when (tier) {
        WidgetTier.MICRO_TINY -> 0
        WidgetTier.MICRO -> 1
        WidgetTier.BANNER, WidgetTier.RAIL,
        WidgetTier.COMPACT_SQUARE, WidgetTier.COMPACT_WIDE_NARROW, WidgetTier.COMPACT_WIDE,
        WidgetTier.COMPACT_TALL_NARROW, WidgetTier.COMPACT_TALL -> 2
        WidgetTier.MEDIUM_SQUARE, WidgetTier.MEDIUM_WIDE, WidgetTier.MEDIUM_TALL -> 3
        WidgetTier.LARGE_SQUARE, WidgetTier.LARGE_WIDE, WidgetTier.LARGE_TALL -> 4
        WidgetTier.XL_WIDE, WidgetTier.XL_TALL, WidgetTier.XL_SQUARE -> 5
    }

    @Test
    fun `growing a widget never demotes it to a smaller band`() {
        // Resizing should feel monotonic: dragging a widget bigger must never
        // take content away. Growing along either axis must not move it down
        // a band -- which also catches a band whose gate is not upward-closed,
        // the structural mistake behind a tile "losing" its layout mid-drag.
        val regressions = mutableListOf<String>()
        var w = MIN_DP
        while (w <= MAX_DP - STEP) {
            var h = MIN_DP
            while (h <= MAX_DP - STEP) {
                val here = tier(w, h)
                for ((nw, nh) in listOf(w + STEP to h, w to h + STEP)) {
                    val next = tier(nw, nh)
                    if (band(next) < band(here)) {
                        regressions += "${w}x$h $here -> ${nw}x$nh $next"
                    }
                }
                h += STEP
            }
            w += STEP
        }
        assertTrue(
            regressions.isEmpty(),
            "growing the widget demoted it to a lesser band in ${regressions.size} places, " +
                "e.g. ${regressions.take(5)}",
        )
    }
}
