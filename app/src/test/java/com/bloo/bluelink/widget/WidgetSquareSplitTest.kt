package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The assertion the three square tiers never had.
 *
 * MEDIUM_SQUARE, LARGE_SQUARE and XL_SQUARE sized their ring from
 * [Scale.ringRoom] and then, separately, took their info row count from
 * [Scale.infoCap] -- a fraction of the raw tile height, subtracted from nothing.
 * Nothing made the two claims add up to the column they share, and RemoteViews
 * does not clip an overflowing Column, so the action buttons underneath simply
 * bled off the bottom edge of the tile.
 *
 * Rebuilding this arithmetic outside the codebase found 10,422 overflowing sizes
 * across three text scales, worst 37.9dp at 240x211 and 1.4x, where a 42dp ring
 * sat beside an 80dp info block in a 42dp band. [Scale.squareSplit] fixed it;
 * this is what stops it coming back, and it runs against the real function
 * rather than a copy of it -- the distinction that matters, because the sweep in
 * [WidgetScaleTest] stayed green through every one of those 10,422 sizes by
 * testing arithmetic the square tiers were not using.
 *
 * Every term below comes from [Scale] itself -- [Scale.innerHeight],
 * [Scale.headerHeight], [Scale.footerHeight] -- rather than being re-derived
 * here. Re-deriving is how a test drifts from the code it guards, and this file
 * would otherwise have kept its own copy of three formulas that just moved.
 */
class WidgetSquareSplitTest {

    /** Per-tier facts that live in the composable: its `spacers` argument, whether
     *  it draws a footer, its row cap, and its side-by-side width threshold. */
    private data class TierSpec(
        val spacers: Dp,
        val hasFooter: Boolean,
        val capRows: Int,
        val rowWidth: Dp,
    )

    private val specs = mapOf(
        WidgetTier.MEDIUM_SQUARE to TierSpec(16.dp, hasFooter = false, capRows = 3, rowWidth = 140.dp),
        WidgetTier.LARGE_SQUARE to TierSpec(20.dp, hasFooter = true, capRows = 4, rowWidth = 220.dp),
        WidgetTier.XL_SQUARE to TierSpec(24.dp, hasFooter = true, capRows = 4, rowWidth = 260.dp),
    )

    /** RingWithContent's own gap when it stacks the rows under the ring. */
    private val stackedGap = 8.dp

    /** The column a square tier renders, given a resolved split. Shared by the
     *  tests below so there is one model of the stack, not three. */
    private fun columnHeight(
        frame: Scale.Frame,
        spec: TierSpec,
        split: Scale.SquareSplit,
        sideBySide: Boolean,
    ): Dp {
        val info = Scale.infoBlockHeight(frame.size, split.rows, frame.textScale)
        // Side by side, the ring and the rows share one band, so the band holds
        // the taller of them. Stacked they sum, with RingWithContent's own gap
        // between -- and no gap when the ring came out at zero, since RingImage
        // renders nothing then.
        val band = when {
            sideBySide -> maxOf(split.ring, info)
            split.ring > 0.dp -> split.ring + stackedGap + info
            else -> info
        }
        return Scale.headerHeight(frame) +
            (if (spec.hasFooter) Scale.footerHeight(frame) else 0.dp) +
            spec.spacers + band + split.map + Scale.buttonHeight(frame.size)
    }

    @Test
    fun squareColumnNeverOverflowsItsTile() {
        var checked = 0
        var worst = 0f
        var worstAt = ""
        for (textScale in listOf(0.8f, 1.0f, 1.4f)) {
            // Pill corner and car switcher are swept, not assumed: each adds to what
            // the column must fit (8dp of extra root padding, and a header pill
            // taller than its text below 1.0x), and each was invisible to every
            // budget in this file until Scale.Frame carried it.
            for (pill in listOf(false, true)) for (switcher in listOf(false, true)) {
                for (w in 40..320 step 2) for (h in 40..320 step 2) {
                    val size = DpSize(w.dp, h.dp)
                    val spec = specs[tierFor(size)] ?: continue
                    val frame = testFrame(size, textScale, pill, switcher)
                    for (wantMap in listOf(false, true)) {
                        val sideBySide = size.width >= spec.rowWidth
                        val room = Scale.ringRoom(
                            frame,
                            hasHeader = true, hasFooter = spec.hasFooter, spacers = spec.spacers,
                        )
                        val split = Scale.squareSplit(
                            size, room, spec.capRows, textScale, wantMap, sideBySide,
                        )
                        val over = columnHeight(frame, spec, split, sideBySide).value -
                            Scale.innerHeight(frame).value
                        if (over > worst) {
                            worst = over
                            worstAt = "${w}x$h @${textScale}x pill=$pill switcher=$switcher " +
                                "map=$wantMap rows=${split.rows} ring=${split.ring}"
                        }
                        checked++
                    }
                }
            }
        }
        assertTrue(worst <= 0.01f, "square column overflows its tile by ${worst}dp at $worstAt")
        // Guards against the sweep silently matching nothing -- if tierFor's
        // boundaries move and no size lands on a square tier any more, the
        // assertion above passes vacuously and this is what notices.
        assertTrue(checked > 20_000, "sweep collapsed to $checked cases")
    }

    /** The rows must be a count the band can actually hold, not merely a number
     *  under the cap -- the exact thing infoCap did not guarantee. */
    @Test
    fun rowCountFitsTheBandItWasGiven() {
        for (textScale in listOf(0.8f, 1.4f)) {
            for (w in 40..320 step 3) for (h in 40..320 step 3) {
                val size = DpSize(w.dp, h.dp)
                val spec = specs[tierFor(size)] ?: continue
                val frame = testFrame(size, textScale, pillCorner = true, hasSwitcher = true)
                val room = Scale.ringRoom(
                    frame, hasHeader = true, hasFooter = spec.hasFooter, spacers = spec.spacers,
                )
                val split = Scale.squareSplit(
                    size, room, spec.capRows, textScale, wantMap = false,
                    sideBySide = size.width >= spec.rowWidth,
                )
                assertTrue(split.rows <= spec.capRows, "rows ${split.rows} exceeded cap")
                assertTrue(
                    Scale.infoBlockHeight(size, split.rows, textScale) <= room + 0.01f.dp,
                    "info block for ${split.rows} rows exceeds the whole column at ${w}x$h",
                )
            }
        }
    }

    /** Whatever the split reports as ring room, the ring it returns fits inside
     *  it. Cheap, but it is the invariant the callers assume when they hand
     *  `split.ringRoom` on to ChargeBarFallback. */
    @Test
    fun ringFitsItsReportedRoom() {
        for (textScale in listOf(0.8f, 1.4f)) {
            for (w in 40..320 step 5) for (h in 40..320 step 5) {
                val size = DpSize(w.dp, h.dp)
                val spec = specs[tierFor(size)] ?: continue
                for (sideBySide in listOf(false, true)) {
                    val frame = testFrame(size, textScale)
                    val room = Scale.ringRoom(
                        frame, hasHeader = true, hasFooter = spec.hasFooter, spacers = spec.spacers,
                    )
                    val split = Scale.squareSplit(
                        size, room, spec.capRows, textScale, wantMap = false, sideBySide,
                    )
                    assertTrue(
                        split.ring <= split.ringRoom + 0.01f.dp,
                        "ring ${split.ring} exceeds its own room ${split.ringRoom} at ${w}x$h",
                    )
                    assertTrue(
                        split.map <= room + 0.01f.dp,
                        "map reserve ${split.map} exceeds the column at ${w}x$h",
                    )
                }
            }
        }
    }
}
