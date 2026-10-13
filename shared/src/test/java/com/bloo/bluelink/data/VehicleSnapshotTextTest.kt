package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The glance text the widget, the watch screen and the watch tile all show. One implementation now, so
 * these pin the words rather than three copies drifting apart.
 */
class VehicleSnapshotTextTest {
    private fun car(
        percent: Int? = null,
        locked: Boolean? = null,
        charging: Boolean? = null,
        rangeMi: Int? = null,
        fetchedAt: Long = 0L,
    ) = VehicleSnapshot(
        vin = "V", name = "Car", model = "M", isEv = true,
        percent = percent, locked = locked, charging = charging, rangeMi = rangeMi, fetchedAt = fetchedAt,
    )

    @Test fun bigValuePrefersChargeThenLockThenADash() {
        assertEquals("76%", car(percent = 76, locked = true).bigValue())
        assertEquals("Locked", car(locked = true).bigValue())
        assertEquals("Unlocked", car(locked = false).bigValue())
        assertEquals("—", car().bigValue())
    }

    @Test fun stateLineJoinsWhatTheCarReports() {
        assertEquals(
            "Locked · Charging · 270 mi",
            car(percent = 76, locked = true, charging = true, rangeMi = 270).stateLine(),
        )
        // The lock state only rides along when there is a charge figure to contrast it with.
        assertEquals("Charging", car(charging = true).stateLine())
        assertEquals("270 mi", car(rangeMi = 270).stateLine())
        assertNull(car().stateLine())
    }

    @Test fun stateLineOrParkedIsNeverBlank() {
        assertEquals("Parked", car().stateLineOrParked())
        assertEquals("Charging", car(charging = true).stateLineOrParked())
    }

    @Test fun updatedLabelIsRelativeAndNullBeforeAnyReading() {
        assertNull(car().updatedLabel(), "never read")
        val now = System.currentTimeMillis()
        assertEquals("just now", car(fetchedAt = now - 30_000L).updatedLabel())
        assertEquals("4 min ago", car(fetchedAt = now - 4 * 60_000L).updatedLabel())
        assertEquals("2 hr ago", car(fetchedAt = now - 2 * 3_600_000L).updatedLabel())
    }

    @Test fun staleOnlyPastHalfAnHour() {
        val now = 1_000_000_000L
        assertFalse(car().isStale(now), "never read")
        assertFalse(car(fetchedAt = now - 30 * 60_000L).isStale(now), "exactly 30 minutes is not yet stale")
        assertTrue(car(fetchedAt = now - 31 * 60_000L).isStale(now))
    }
}
