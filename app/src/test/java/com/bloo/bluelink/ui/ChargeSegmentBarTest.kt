package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins [chargeBarLayout] -- the plain segment-boundary math [ChargeSegmentBar] draws
 * from -- against the exact shape it's meant to produce: a real gap at the limit split,
 * genuinely three positive-width segments when there's room for them, and no segment
 * ever overflowing or going negative regardless of how narrow the bar or how the
 * percent/limit happen to land.
 *
 * Written after "make sure that the bar will display in three segments like it's
 * supposed to" -- a Compose Canvas draw call cannot be unit-tested directly (no Compose
 * runtime in a plain JVM test), which is exactly why the math was pulled out into this
 * standalone function in the first place. This is the actual verification that request
 * asked for, not a re-reading of the code by eye.
 */
class ChargeSegmentBarTest {

    private val gap = 8f // an arbitrary but representative px value; the math doesn't care what it maps to in dp

    @Test
    fun `the exact reported case -- 76 percent, 90 percent limit -- renders three segments`() {
        // The numbers from the real device screenshot this whole redesign was reported
        // against. Swept across a few realistic hero-card widths, not just one.
        for (width in listOf(220f, 260f, 300f, 340f, 400f)) {
            val layout = chargeBarLayout(
                totalWidth = width, barHeight = 18f,
                filledFrac = 0.76f, limitFrac = 0.90f, stuckAtLimit = false, gap = gap,
            )
            assertTrue(!layout.hasSingleTrack, "width=$width collapsed to one track segment, not two")
            assertTrue(layout.fillWidth > 0f, "width=$width has no fill segment")
            assertTrue(layout.midWidth > 0f, "width=$width has no current->limit track segment")
            assertTrue(layout.farWidth > 0f, "width=$width has no limit->100% dim segment")
            // The three pieces (plus the one gap between mid and far) must not overflow
            // the bar, and must not leave an unaccounted-for gap ANYWHERE but the one
            // reserved spot.
            val consumed = layout.fillWidth + layout.midWidth + gap + layout.farWidth
            assertTrue(
                kotlin.math.abs(consumed - width) < 0.5f,
                "width=$width consumed $consumed, expected ~$width (fill=${layout.fillWidth} mid=${layout.midWidth} far=${layout.farWidth})",
            )
        }
    }

    @Test
    fun `three segments render across a wide sweep of percent, limit and bar width`() {
        val bad = mutableListOf<String>()
        for (width in listOf(200f, 260f, 320f, 400f)) {
            for (pct in 5..95 step 5) {
                for (limit in listOf(80, 85, 90, 95, 99)) {
                    if (limit <= pct) continue // not the not-stuck case this test covers
                    // Only assert the three-segment shape when there's genuinely enough
                    // room for it on BOTH sides of the limit split -- a limit only 1-2
                    // points above the current charge, OR only 1-2 points below 100%
                    // (this test's own first failure: limit=99 on every width, always the
                    // FAR side collapsing because limitX + halfGap overshoots the bar's
                    // right edge), can legitimately collapse once the gap is reserved,
                    // same as the fill floor collapses a 1% charge to nothing on a very
                    // short bar. That is correct behaviour, not a bug, so both sides are
                    // deliberately excluded rather than asserted against.
                    val nearSpanPx = width * (limit - pct) / 100f
                    val farSpanPx = width * (100 - limit) / 100f
                    if (nearSpanPx < gap * 3 || farSpanPx < gap * 3) continue
                    val layout = chargeBarLayout(
                        totalWidth = width, barHeight = 18f,
                        filledFrac = pct / 100f, limitFrac = limit / 100f, stuckAtLimit = false, gap = gap,
                    )
                    if (layout.hasSingleTrack || layout.midWidth <= 0f || layout.farWidth <= 0f) {
                        bad += "width=$width pct=$pct limit=$limit -> mid=${layout.midWidth} far=${layout.farWidth} single=${layout.hasSingleTrack}"
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "cases with enough room that still failed to show three segments:\n" + bad.take(20).joinToString("\n"))
    }

    @Test
    fun `stuck at limit collapses to one dim segment, not three`() {
        val layout = chargeBarLayout(
            totalWidth = 300f, barHeight = 18f,
            filledFrac = 0.90f, limitFrac = 0.90f, stuckAtLimit = true, gap = gap,
        )
        assertTrue(layout.hasSingleTrack, "stuck-at-limit should collapse to one remaining segment")
        assertTrue(layout.singleTrackDim, "the one remaining segment when stuck should be the DIM track")
        assertTrue(layout.midWidth == 0f && layout.farWidth == 0f)
    }

    @Test
    fun `no limit at all is one ordinary (non-dim) track segment`() {
        val layout = chargeBarLayout(
            totalWidth = 300f, barHeight = 18f,
            filledFrac = 0.5f, limitFrac = null, stuckAtLimit = false, gap = gap,
        )
        assertTrue(layout.hasSingleTrack)
        assertTrue(!layout.singleTrackDim, "no limit at all should use the ordinary track colour, not the dim one")
    }

    @Test
    fun `no segment is ever negative, and nothing overflows the bar, across every shape`() {
        val bad = mutableListOf<String>()
        for (width in listOf(40f, 80f, 150f, 300f, 500f)) {
            for (pct in 0..100 step 5) {
                for (limitOrNull in listOf(null, 1, 30, 50, pct, pct + 1, 80, 90, 99, 100)) {
                    val limit = limitOrNull?.coerceIn(1, 99)
                    for (stuck in listOf(false, true)) {
                        val layout = chargeBarLayout(
                            totalWidth = width, barHeight = 18f,
                            filledFrac = pct / 100f, limitFrac = limit?.let { it / 100f }, stuckAtLimit = stuck, gap = gap,
                        )
                        val widths = listOf(layout.fillWidth, layout.singleTrackWidth, layout.midWidth, layout.farWidth)
                        if (widths.any { it < -0.01f }) {
                            bad += "width=$width pct=$pct limit=$limit stuck=$stuck -> negative width: $layout"
                        }
                        if (layout.fillWidth > width + 0.5f) {
                            bad += "width=$width pct=$pct limit=$limit stuck=$stuck -> fill overflowed the bar: $layout"
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "shape violations:\n" + bad.take(20).joinToString("\n"))
    }
}
