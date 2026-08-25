package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins for the snapshot reshaping layer ([Snapshots.kt]) -- the mapping from
 * the in-memory [UiState] onto the persisted [VehicleSnapshot] form that the
 * watch, widget and tile runners read.
 *
 * This is the layer a "fix the phone" bug used to ship through: the snapshot
 * carried the RAW API generation number while the phone displayed the
 * user-overridden one (so the widget disagreed with the phone about what the
 * car was), the snapshot dropped the cached location whenever the status had
 * no GPS (Canada's repo never puts one there), and the charge-limit field went
 * null for every PARKED car. All three were correct-on-phone /
 * wrong-on-widget problems, and all three are now pinned here from both ends.
 */
class SnapshotOfTest {

    private val vehicle = {
        Vehicle(
            vin = "V1", regId = "r1", name = "Hy", model = "M",
            generation = "5", brandIndicator = "H", isEv = true, odometer = "12345",
        )
    }

    private fun snapshot(state: UiState = UiState(), status: VehicleStatus? = null) =
        snapshotOf(vehicle(), status, state)

    // --- effective generation honours the user override ----------------------

    @Test
    fun generation_carriesEffectiveOverrideNotRawApiValue() {
        // No override: raw "5" is stored as "3" for a modern ccNC unit.
        assertEquals("3", snapshot().generation)
        // Forced Gen5W -> stored as "2".
        val gen5 = UiState(platforms = mapOf("V1" to VehiclePlatform.GEN5W))
        assertEquals("2", snapshot(gen5).generation)
    }

    @Test
    fun generation_kiaKeepsRawGeneration() {
        // platformOverridable false: the stored string cannot change anything,
        // so snapshotOf leaves the raw API value alone.
        val kia = vehicle()
        val kia5 = kia.copy(brandIndicator = "K")
        assertEquals(
            "5",
            snapshotOf(kia5, null, UiState()).generation,
        )
    }

    // --- battery/fuel follow the effective powertrain -------------------------

    @Test
    fun hasBattery_phevViaOverride() {
        assertTrue(snapshot().hasBattery)
        val gas = vehicle().copy(isEv = false)
        assertFalse(snapshotOf(gas, null, UiState()).hasBattery)
        val phev = snapshotOf(gas, null, UiState(powertrains = mapOf(gas.vin to Powertrain.PHEV)))
        assertTrue(phev.hasBattery)
    }

    // --- location fallback ----------------------------------------------------

    @Test
    fun location_fallsBackToStateWhenStatusHasNone() {
        val loc = com.bloo.bluelink.data.GeoLocation(34.289, -118.765, speed = 0.0)
        val state = UiState(locations = mapOf("V1" to loc))
        val s = snapshot(state, status = VehicleStatus(engine = false)) // no gps in status
        assertEquals(34.289, s.lat)
        assertEquals(-118.765, s.lon)
    }

    @Test
    fun location_statusWinsOverCached() {
        val cached = com.bloo.bluelink.data.GeoLocation(1.0, 2.0)
        val fresh = com.bloo.bluelink.data.VehicleLocation(
            coord = com.bloo.bluelink.data.Coord(34.5, -118.5),
            speed = com.bloo.bluelink.data.Speed(23.0),
        )
        val status = VehicleStatus(vehicleLocation = fresh)
        val s = snapshot(UiState(locations = mapOf("V1" to cached)), status)
        assertEquals(34.5, s.lat)
        assertEquals(23.0, s.speedMph)
    }

    @Test
    fun snapshot_mapsTextFieldsThrough() {
        val state = UiState(
            licensePlates = mapOf("V1" to "ABC-123"),
            lastServiceMiles = mapOf("V1" to 91_000),
            serviceIntervalMiles = mapOf("V1" to 10_000),
        )
        val s = snapshot(state)
        assertEquals("ABC-123", s.licensePlate)
        assertEquals(91_000, s.lastServiceMiles)
        assertEquals(10_000, s.serviceIntervalMiles)
        assertEquals("12345", s.odometer)
    }

    // --- applyOrder ----------------------------------------------------------

    private val cars = {
        (1..3).map { i ->
            Vehicle(
                vin = "V$i", regId = "r$i", name = "C$i", model = "M",
                generation = "5", brandIndicator = "H", isEv = true,
            )
        }
    }

    @Test
    fun applyOrder_emptyOrderIsIdentity() {
        assertEquals(cars(), applyOrder(cars(), emptyList()))
    }

    @Test
    fun applyOrder_reordersAndSkipsUnknownVins() {
        val ordered = applyOrder(cars(), listOf("V3", "V1", "V999"))
        assertEquals(listOf("V3", "V1", "V2"), ordered.map { it.vin })
    }

    @Test
    fun applyOrder_newCarsAppendAtTheEnd() {
        val ordered = applyOrder(cars(), listOf("V2"))
        assertEquals(listOf("V2", "V1", "V3"), ordered.map { it.vin })
    }

    @Test
    fun applyOrder_preservesRemainingRelativeOrder() {
        val ordered = applyOrder(cars(), listOf("V3"))
        assertEquals(listOf("V3", "V1", "V2"), ordered.map { it.vin })
    }
}
