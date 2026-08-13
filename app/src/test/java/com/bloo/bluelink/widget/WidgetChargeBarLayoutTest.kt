package com.bloo.bluelink.widget

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins [widgetChargeBarLayout] -- the plain segment-boundary math [ChargeBar] draws
 * from -- against the same shape the phone's ChargeSegmentBarTest pins: a real gap at
 * the limit split, genuinely three positive-width segments when there's room for them,
 * and no segment ever overflowing or going negative.
 *
 * Written after "make sure that the bar will display in three segments like it's
 * supposed to" -- Glance composables can't be unit-tested directly (no Glance/Compose
 * runtime in a plain JVM test), which is exactly why the math was pulled out into this
 * standalone function.
 */
class WidgetChargeBarLayoutTest {

    @Test
    fun `the exact reported case -- 76 percent, 90 percent limit -- renders three segments`() {
        for (width in listOf(120.dp, 160.dp, 200.dp, 260.dp, 320.dp)) {
            val layout = widgetChargeBarLayout(width, 12.dp, 0.76f, 90, stuckAtLimit = false)
            assertTrue(!layout.hasSingleTrack, "width=$width collapsed to one track segment, not two")
            assertTrue(layout.filled > 0.dp, "width=$width has no fill segment")
            assertTrue(layout.mid > 0.dp, "width=$width has no current->limit track segment")
            assertTrue(layout.far > 0.dp, "width=$width has no limit->100% dim segment")
            val consumed = layout.filled + layout.mid + layout.gap + layout.far
            assertTrue(
                (consumed - width).value.let { kotlin.math.abs(it) } < 0.5f,
                "width=$width consumed $consumed, expected ~$width (filled=${layout.filled} mid=${layout.mid} far=${layout.far})",
            )
        }
    }

    @Test
    fun `three segments render across a wide sweep of percent, limit and bar width`() {
        val bad = mutableListOf<String>()
        for (width in listOf(100.dp, 160.dp, 220.dp, 300.dp)) {
            for (pct in 5..95 step 5) {
                for (limit in listOf(80, 85, 90, 95, 99)) {
                    if (limit <= pct) continue
                    // Same reasoning as the phone's own sweep test (and the same first
                    // failure: limit=99 on every width, always the FAR side collapsing) --
                    // only assert the three-segment shape when there's genuinely enough
                    // room for it on BOTH sides of the limit split, since either one can
                    // legitimately collapse once the gap is reserved.
                    val gap = if (width >= 60.dp) 3.dp else 0.dp
                    val nearSpanDp = width * (limit - pct) / 100f
                    val farSpanDp = width * (100 - limit) / 100f
                    if (nearSpanDp < gap * 3 || farSpanDp < gap * 3) continue
                    val layout = widgetChargeBarLayout(width, 12.dp, pct / 100f, limit, stuckAtLimit = false)
                    if (layout.hasSingleTrack || layout.mid <= 0.dp || layout.far <= 0.dp) {
                        bad += "width=$width pct=$pct limit=$limit -> mid=${layout.mid} far=${layout.far} single=${layout.hasSingleTrack}"
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "cases with enough room that still failed to show three segments:\n" + bad.take(20).joinToString("\n"))
    }

    @Test
    fun `stuck at limit collapses to one dim segment, not three`() {
        val layout = widgetChargeBarLayout(200.dp, 12.dp, 0.90f, 90, stuckAtLimit = true)
        assertTrue(layout.hasSingleTrack, "stuck-at-limit should collapse to one remaining segment")
        assertTrue(layout.singleTrackDim, "the one remaining segment when stuck should be the DIM track")
        assertTrue(layout.mid == 0.dp && layout.far == 0.dp)
    }

    @Test
    fun `no limit at all is one ordinary (non-dim) track segment`() {
        val layout = widgetChargeBarLayout(200.dp, 12.dp, 0.5f, null, stuckAtLimit = false)
        assertTrue(layout.hasSingleTrack)
        assertTrue(!layout.singleTrackDim, "no limit at all should use the ordinary track colour, not the dim one")
    }

    @Test
    fun `no segment is ever negative, and nothing overflows the bar, across every shape`() {
        val bad = mutableListOf<String>()
        for (width in listOf(20.dp, 40.dp, 80.dp, 200.dp, 400.dp)) {
            for (pct in 0..100 step 5) {
                for (limitOrNull in listOf(null, 1, 30, 50, pct, pct + 1, 80, 90, 99, 100)) {
                    val limit = limitOrNull?.coerceIn(1, 99)
                    for (stuck in listOf(false, true)) {
                        val layout = widgetChargeBarLayout(width, 12.dp, pct / 100f, limit, stuck)
                        val widths = listOf(layout.filled, layout.singleTrackWidth, layout.mid, layout.far)
                        if (widths.any { it.value < -0.01f }) {
                            bad += "width=$width pct=$pct limit=$limit stuck=$stuck -> negative width: $layout"
                        }
                        if (layout.filled > width + 0.5.dp) {
                            bad += "width=$width pct=$pct limit=$limit stuck=$stuck -> fill overflowed the bar: $layout"
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "shape violations:\n" + bad.take(20).joinToString("\n"))
    }
}
