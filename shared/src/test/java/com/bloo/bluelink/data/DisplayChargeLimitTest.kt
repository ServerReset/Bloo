package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [EvStatus.displayChargeLimit], added after a real device report: a parked,
 * unplugged car's hero card lost its whole limit-aware charge bar (falling back to a
 * plain unsplit track, and losing the blue "topped up" state) because
 * [EvStatus.targetForCurrentPlug] -- the field every display of the limit used to read
 * directly -- is null the instant nothing is plugged in. `displayChargeLimit` is meant
 * to answer "what should the bar show right now" rather than "what is the currently
 * connected plug's own target", and these tests pin that it actually does.
 */
class DisplayChargeLimitTest {

    private fun ev(plugin: Int? = null, ac: Int? = null, dc: Int? = null) = EvStatus(
        batteryPlugin = plugin,
        reservChargeInfos = ReservChargeInfos(
            targetSOClist = listOfNotNull(
                ac?.let { TargetSOC(plugType = 1, targetSOClevel = it) },
                dc?.let { TargetSOC(plugType = 0, targetSOClevel = it) },
            ),
        ),
    )

    @Test
    fun `plugged into AC uses the AC target`() {
        assertEquals(80, ev(plugin = 2, ac = 80, dc = 90).displayChargeLimit())
    }

    @Test
    fun `plugged into DC fast uses the DC target`() {
        assertEquals(90, ev(plugin = 1, ac = 80, dc = 90).displayChargeLimit())
    }

    @Test
    fun `unplugged falls back to the AC target, not null`() {
        // The exact case reported: a parked car, batteryPlugin 0 (or unreported), still has
        // a configured AC limit worth showing.
        assertEquals(80, ev(plugin = 0, ac = 80, dc = 90).displayChargeLimit())
        assertEquals(80, ev(plugin = null, ac = 80, dc = 90).displayChargeLimit())
    }

    @Test
    fun `unplugged with no AC target reported at all is genuinely null`() {
        // No fabricated fallback beyond AC -- if the car has never reported an AC limit
        // either, there is nothing honest left to show.
        assertNull(ev(plugin = 0, ac = null, dc = 90).displayChargeLimit())
        assertNull(ev(plugin = 0).displayChargeLimit())
    }

    @Test
    fun `null EvStatus answers null rather than throwing`() {
        val ev: EvStatus? = null
        assertNull(ev?.displayChargeLimit())
    }
}
