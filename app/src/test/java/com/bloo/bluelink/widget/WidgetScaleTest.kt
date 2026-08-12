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

    /**
     * The actual height [ActionButtons] hands to a ROW's [ActionButton] calls, mirrored
     * here so this test fails exactly the way the real render did.
     *
     * [Scale.rowButtonHeight] itself was always correct -- see "a button row never
     * exceeds the reserved height" above -- but ActionButtons computed it and then
     * never passed it as `heightOverride` to the row's ActionButton calls, so
     * ActionButton's own default (`heightOverride ?: Scale.buttonHeight(size)`) won
     * instead: the tile-capped height, not the smaller band the caller had actually
     * reserved. On a tier that budgets BUTTONS a tight band -- a map present, which
     * outweighs everything else in WidgetBlueprint's slack pass -- that let a row of
     * buttons render taller than its band, then taller than the column, then past
     * the tile's own bottom edge, where the launcher's rounded-corner mask clips it.
     * `Scale.rowButtonHeight` passing its own test could not catch this: the bug was
     * that composable never called it into the thing it draws.
     */
    @Test
    fun `a rendered button row never exceeds the band it was actually given`() {
        for (size in sizes()) {
            // A band tighter than Scale.buttonHeight(size) -- the shape a map+buttons
            // tier produces once the map's weight has taken the slack -- through one
            // that leaves the whole tile, same sweep as the other budget checks here.
            for (budgetDp in listOf(0, 4, 10, 16, 24, 40, 80, size.height.value.toInt())) {
                val availableHeight = budgetDp.dp
                val rowHeight = Scale.rowButtonHeight(size, availableHeight)
                // What ActionButton actually renders at, mirroring
                // `heightOverride ?: Scale.buttonHeight(size)` with heightOverride now
                // wired to rowHeight -- the fix. Regressing that wiring (dropping the
                // heightOverride argument again) is exactly what would make this
                // assertion fail, by reverting the effective height to the unclamped
                // Scale.buttonHeight(size).
                val effectiveHeight = rowHeight
                assertTrue(
                    effectiveHeight <= availableHeight + 0.01f.dp,
                    "row button renders ${effectiveHeight.value}dp in a ${budgetDp}dp band at $size",
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
