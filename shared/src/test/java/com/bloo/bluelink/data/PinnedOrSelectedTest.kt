package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Tests for [SnapshotStore.SnapshotData.pinnedOrSelected], the watch-surface car resolver
 * (Tile + complication): the pinned car if its VIN is set AND still present, else the
 * globally-selected car so an unconfigured or STALE pin is never blank.
 *
 * The stale-pin-falls-back-to-selected rule is the whole reason this helper exists (the phone
 * widget/QS tile deliberately do the opposite -- pinned or nothing). These pin that fallback
 * so a future edit can't quietly turn a stale pin into a blank tile, or make it swap when it
 * should have kept nothing.
 */
class PinnedOrSelectedTest {

    private fun car(vin: String) = VehicleSnapshot(vin = vin, name = vin, model = "M", isEv = true)

    private fun data(vins: List<String>, selectedVin: String?) =
        SnapshotStore.SnapshotData(vins.map { car(it) }, selectedVin)

    @Test
    fun pinnedPresentReturnsThatCar() {
        val d = data(listOf("A", "B", "C"), selectedVin = "A")
        assertEquals("B", d.pinnedOrSelected("B")?.vin, "a present pin wins over the selection")
    }

    @Test
    fun pinnedNullFallsBackToSelected() {
        val d = data(listOf("A", "B"), selectedVin = "B")
        assertEquals("B", d.pinnedOrSelected(null)?.vin, "no pin -> the selected car")
    }

    @Test
    fun stalePinFallsBackToSelected() {
        // The load-bearing case: a pin whose car has left the account resolves to the selected
        // car, NOT null -- so the tile/complication never goes blank on a stale pin.
        val d = data(listOf("A", "B"), selectedVin = "A")
        assertEquals("A", d.pinnedOrSelected("GONE")?.vin)
    }

    @Test
    fun selectedFallsBackToFirstWhenSelectionUnknown() {
        // Inherits SnapshotData.selected's own fallback: an unknown selectedVin -> first car.
        val d = data(listOf("A", "B"), selectedVin = "MISSING")
        assertEquals("A", d.pinnedOrSelected(null)?.vin)
        assertEquals("A", d.pinnedOrSelected("ALSO_GONE")?.vin)
    }

    @Test
    fun emptyGarageIsNull() {
        val d = data(emptyList(), selectedVin = null)
        assertNull(d.pinnedOrSelected("anything"))
        assertNull(d.pinnedOrSelected(null))
    }

    @Test
    fun returnsTheActualStoredInstance() {
        // It returns the snapshot from the list, not a copy -- callers read its fields.
        val d = data(listOf("A", "B"), selectedVin = "A")
        assertSame(d.vehicles.first { it.vin == "B" }, d.pinnedOrSelected("B"))
    }
}
