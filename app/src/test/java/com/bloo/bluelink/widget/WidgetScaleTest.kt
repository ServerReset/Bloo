package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
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
        var worst = 0f
        var worstAt = ""
        for (size in sizes()) {
            if (tierFor(size) != WidgetTier.MEDIUM_WIDE) continue
            for (ts in scales) {
                for (hasHeader in listOf(true, false)) {
                    val avail = content(size)
                    val barH = Scale.barHeight(size)
                    val room = Scale.ringRoom(size, ts, hasHeader, false, 18.dp)
                    val rows = Scale.infoRowsIn(size, (room - barH).coerceAtLeast(0.dp), ts, 2)
                    val header = if (hasHeader) {
                        Scale.lineHeight(Scale.titleSp(size).value, ts) +
                            Scale.lineHeight(Scale.subtitleSp(size).value, ts)
                    } else 0.dp
                    val rowsH = Scale.infoBlockHeight(size, rows, ts)
                    var demand = header + 6.dp + barH + Scale.buttonHeight(size)
                    if (rows > 0) demand += 6.dp + rowsH
                    val over = (demand - avail).value
                    if (over > worst) { worst = over; worstAt = "$size @${ts}x header=$hasHeader" }
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
                val room = Scale.ringRoom(size, ts, true, true, 12.dp)
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
                val room = Scale.ringRoom(size, ts, true, true, 20.dp)
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
                val room = Scale.ringRoom(size, ts, true, true, 20.dp)
                val map = Scale.mapReserve(size, room, wantMap = true)
                assertTrue(
                    map.value <= room.value * 0.35f + 0.01f,
                    "map ${map} claims more than a third of ${room} at $size @${ts}x",
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
                    // name + ring + one button row
                    val room = Scale.ringRoom(size, ts, hasHeader = false, hasFooter = false, spacers = 8.dp) - name
                    val ring = Scale.ring(size, minOf(room, size.width - 12.dp))
                    val used = name + 8.dp + ring + Scale.buttonHeight(size)
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
        // Mirrors TALL_TIER_MARGIN in CarWidget.kt -- private there, so
        // duplicated here the same way this file already duplicates each
        // layout's own inline spacing constants rather than reaching into
        // the widget class for them.
        val margin = 16.dp
        for (size in sizes()) {
            val tier = tierFor(size)
            if (tier != WidgetTier.RAIL && tier != WidgetTier.COMPACT_TALL_NARROW && tier != WidgetTier.COMPACT_TALL) continue
            val budget = content(size)
            for (ts in scales) {
                for (actionCount in 0..7) {
                    for (wantMap in listOf(false, true)) {
                        when (tier) {
                            WidgetTier.RAIL -> {
                                val n = Scale.maxStackedButtons(size, budget, overhead = 16.dp, cap = actionCount)
                                val buttonZone = if (n > 0) {
                                    Scale.buttonHeight(size) * n + Scale.buttonGap(size) * (n - 1) + 8.dp
                                } else {
                                    0.dp
                                }
                                val heroRoom = (budget - buttonZone - margin).coerceAtLeast(0.dp)
                                val split = Scale.tallSplit(size, heroRoom, capRows = 0, textScale = ts, wantMap = wantMap)
                                val spacerBeforeMap = if (wantMap && split.map > 0.dp) 6.dp else 0.dp
                                val spacerBeforeButtons = if (n > 0) 8.dp else 0.dp
                                val used = buttonZone + split.ring + split.map + spacerBeforeMap + spacerBeforeButtons
                                assertTrue(
                                    used.value <= budget.value + 0.5f,
                                    "RAIL ${used} exceeds ${budget} at $size @${ts}x actions=$actionCount map=$wantMap",
                                )
                            }
                            WidgetTier.COMPACT_TALL_NARROW -> {
                                val nameH = Scale.lineHeight(Scale.titleSp(size).value, ts) + 4.dp
                                val n = Scale.maxStackedButtons(size, budget - nameH, overhead = 8.dp, cap = minOf(actionCount, 4))
                                val buttonZone = if (n > 0) {
                                    Scale.buttonHeight(size) * n + Scale.buttonGap(size) * (n - 1) + 4.dp
                                } else {
                                    0.dp
                                }
                                val heroRoom = (budget - nameH - buttonZone - margin).coerceAtLeast(0.dp)
                                val split = Scale.tallSplit(size, heroRoom, capRows = 1, textScale = ts, wantMap = wantMap)
                                val spacerRows = if (split.rows > 0) 4.dp else 0.dp
                                val spacerMap = if (wantMap && split.map > 0.dp) 4.dp else 0.dp
                                val spacerButtons = if (n > 0) 4.dp else 0.dp
                                val used = nameH + buttonZone + split.ring + Scale.infoBlockHeight(size, split.rows, ts) +
                                    split.map + spacerRows + spacerMap + spacerButtons
                                assertTrue(
                                    used.value <= budget.value + 0.5f,
                                    "COMPACT_TALL_NARROW ${used} exceeds ${budget} at $size @${ts}x actions=$actionCount map=$wantMap",
                                )
                            }
                            else -> {
                                val nameH = Scale.lineHeight(Scale.titleSp(size).value, ts)
                                val buttonZone = Scale.buttonHeight(size) + 12.dp
                                val heroRoom = (budget - nameH - buttonZone - margin).coerceAtLeast(0.dp)
                                val split = Scale.tallSplit(size, heroRoom, capRows = 4, textScale = ts, wantMap = wantMap)
                                val spacerRing = if (split.ring > 0.dp) 6.dp else 0.dp
                                val used = nameH + split.ring + spacerRing + Scale.infoBlockHeight(size, split.rows, ts) +
                                    split.map + buttonZone
                                assertTrue(
                                    used.value <= budget.value + 0.5f,
                                    "COMPACT_TALL ${used} exceeds ${budget} at $size @${ts}x actions=$actionCount map=$wantMap",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
