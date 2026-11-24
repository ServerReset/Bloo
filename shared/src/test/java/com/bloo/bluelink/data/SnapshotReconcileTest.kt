package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** The app's save of every car keeps a newer status another writer stored, and never drops what it does not know. */
class SnapshotReconcileTest {
    private fun snap(name: String = "Car", locked: Boolean? = null, percent: Int? = null, fetchedAt: Long = 0L, place: String? = null) =
        VehicleSnapshot(vin = "V", name = name, model = "M", isEv = true, locked = locked, percent = percent, fetchedAt = fetchedAt, place = place)

    @Test fun aNewerStoredStatusWins() {
        // The widget locked the car at 2000; the app still holds its 1000 reading of "unlocked".
        val stored = snap(locked = true, percent = 80, fetchedAt = 2_000)
        val app = snap(name = "Renamed", locked = false, percent = 70, fetchedAt = 1_000)
        val saved = app.reconciledWith(stored)
        assertEquals(true, saved.locked, "the widget's lock is kept")
        assertEquals(80, saved.percent)
        assertEquals(2_000, saved.fetchedAt, "and its time, not the app's")
        assertEquals("Renamed", saved.name, "identity still comes from the app")
    }

    @Test fun aNewerAppStatusWins() {
        val stored = snap(locked = true, fetchedAt = 1_000)
        val app = snap(locked = false, fetchedAt = 2_000)
        assertEquals(false, app.reconciledWith(stored).locked)
    }

    @Test fun whatTheWinnerLacksIsFilledFromTheOther() {
        val stored = snap(locked = true, percent = null, fetchedAt = 2_000)
        val app = snap(locked = false, percent = 70, fetchedAt = 1_000)
        assertEquals(70, app.reconciledWith(stored).percent)
    }

    @Test fun aPlaceNameTheAppNeverSetsIsKept() {
        val stored = snap(place = "Home", fetchedAt = 1_000)
        assertEquals("Home", snap(fetchedAt = 2_000).reconciledWith(stored).place)
    }
}
