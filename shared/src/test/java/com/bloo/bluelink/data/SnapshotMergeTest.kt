package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tests for [mergeVehicleUpdates], the batch-merge behind SnapshotStore.updateVehicles.
 *
 * updateVehicles replaced a per-vehicle write loop, so these pin the properties that
 * loop gave for free and that a batch could plausibly break: existing order, ignoring
 * unknown VINs, and duplicate handling.
 */
class SnapshotMergeTest {

    private fun car(vin: String, name: String = vin, pct: Int? = null) =
        VehicleSnapshot(vin = vin, name = name, model = "M", isEv = true, percent = pct)

    @Test
    fun replacesOnlyTheMatchingVins() {
        val existing = listOf(car("A", pct = 10), car("B", pct = 20), car("C", pct = 30))
        val result = mergeVehicleUpdates(existing, listOf(car("B", pct = 99)))
        assertEquals(listOf("A", "B", "C"), result.map { it.vin })
        assertEquals(listOf(10, 99, 30), result.map { it.percent })
    }

    /** Order is the EXISTING list's, not the updates'. The vehicle order is the car
     *  pager's order, so a background refresh must never reshuffle the user's garage. */
    @Test
    fun preservesExistingOrderRegardlessOfUpdateOrder() {
        val existing = listOf(car("A"), car("B"), car("C"))
        val result = mergeVehicleUpdates(existing, listOf(car("C", pct = 3), car("A", pct = 1)))
        assertEquals(listOf("A", "B", "C"), result.map { it.vin })
        assertEquals(listOf(1, null, 3), result.map { it.percent })
    }

    /** An update for a VIN that isn't in the store is dropped, not appended. A stale
     *  refresh for a car removed from the account must not resurrect it. */
    @Test
    fun ignoresUnknownVinsRatherThanAppending() {
        val existing = listOf(car("A"), car("B"))
        val result = mergeVehicleUpdates(existing, listOf(car("GONE", pct = 50)))
        assertEquals(listOf("A", "B"), result.map { it.vin })
    }

    /** Last write wins within one batch, which is what N sequential single-vehicle
     *  writes in the same order would have produced. */
    @Test
    fun lastDuplicateWins() {
        val existing = listOf(car("A"))
        val result = mergeVehicleUpdates(existing, listOf(car("A", pct = 1), car("A", pct = 2)))
        assertEquals(listOf(2), result.map { it.percent })
    }

    /** An empty batch is a no-op and returns the very same list -- updateVehicles
     *  early-returns on empty so it never even opens the store, and a refresh where
     *  every car's fetch failed must not rewrite the payload. */
    @Test
    fun emptyUpdatesReturnTheSameListInstance() {
        val existing = listOf(car("A"), car("B"))
        assertSame(existing, mergeVehicleUpdates(existing, emptyList()))
    }

    @Test
    fun emptyExistingStaysEmpty() {
        assertEquals(emptyList(), mergeVehicleUpdates(emptyList(), listOf(car("A"))))
    }

    /** Updating every car at once is the "refresh all" case this batch exists for. */
    @Test
    fun updatesEveryVehicleInOneBatch() {
        val existing = listOf(car("A"), car("B"), car("C"))
        val result = mergeVehicleUpdates(existing, existing.map { it.copy(percent = 77) })
        assertEquals(listOf("A", "B", "C"), result.map { it.vin })
        assertEquals(listOf(77, 77, 77), result.map { it.percent })
    }
}
