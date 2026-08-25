package com.bloo.bluelink.ui

import com.bloo.bluelink.data.EvStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins for the AI prompt/state-text layer ([AiText.kt]).
 *
 * These strings are the ONLY thing the on-device model sees about the cars,
 * so they are facts by construction: a sentence claiming "the doors are
 * unlocked" for a car that never reported its doors was a real shipped bug
 * (carText's Priority 1 doc), and the "driving" phrasing inherits the same
 * all-or-nothing discipline. Deterministic over (Vehicle, status, UiState),
 * so they pin on a JVM.
 */
class AiTextTest {

    private val car = Vehicle(
        vin = "V1", regId = "r1", name = "Ioniq 6", model = "Ioniq 6",
        generation = "5", brandIndicator = "H", isEv = true, odometer = "1000",
    )

    @Test
    fun fmtTimeToFull_underAnHour() {
        assertEquals("45 minutes", fmtTimeToFull(45))
        assertEquals("58 minutes", fmtTimeToFull(58))
    }

    @Test
    fun fmtTimeToFull_singlesAndMinutes() {
        assertEquals("1 hour", fmtTimeToFull(60))
        assertEquals("1 hour 5 minutes", fmtTimeToFull(65))
        assertEquals("2 hours 10 minutes", fmtTimeToFull(130))
        assertEquals("2 hours", fmtTimeToFull(120))
    }

    @Test
    fun carText_neverReportsUnreportedDoors() {
        // doorLock == null -> no door sentence at all. The old bug folded null
        // into "unlocked", and the summary told the user their car was open.
        val text = carText(car, VehicleStatus(), UiState())
        assertTrue("door" !in text, "unreported doors must not be described: $text")
        val actual = carText(car, VehicleStatus(doorLock = true), UiState())
        assertTrue("locked" in actual, actual)
    }

    @Test
    fun carText_noStatusSaysSo() {
        val text = carText(car, null, UiState())
        assertTrue("No live status" in text)
        assertTrue(text.startsWith("Vehicle:"), text)
    }

    @Test
    fun carText_chargingTimeUsesTheSharedPhrase() {
        // minutesToFull is DERIVED (remainTime2.atc with a positive value),
        // not a constructor arg: the derivation itself is worth pinning here
        // because "fully charged in 0 minutes" was a real glitch when the
        // estimate reported 0.
        val status = VehicleStatus(
            evStatus = EvStatus(
                batteryCharge = true,
                remainTime2 = com.bloo.bluelink.data.RemainTime2(
                    atc = com.bloo.bluelink.data.TimeValue(value = 95.0),
                ),
            ),
        )
        val text = carText(car, status, UiState())
        assertTrue("1 hour 35 minutes" in text, text)
    }

    @Test
    fun carText_drivingLabelCreditsTheEffectivePowertrain() {
        // isDriving reads the speed the same way the header does: from the
        // car's LOCATIONS map (the fallback path), any status speed is only an
        // alternative -- the point of the pin is that the AI label agrees with
        // the header for the same input.
        val driving = UiState(
            locations = mapOf(
                car.vin to com.bloo.bluelink.data.GeoLocation(1.0, 2.0, speed = 35.0),
            ),
        )
        val text = carText(car, VehicleStatus(), driving)
        assertTrue("driving" in text, text)
        // A PHEV read as gas still reports the battery because hasBattery
        // follows the EFFECTIVE powertrain (the same rule snapshotOf uses).
        val gasCar = car.copy(isEv = false)
        val phev = UiState(powertrains = mapOf(gasCar.vin to com.bloo.bluelink.data.Powertrain.PHEV))
        val battText = carText(gasCar, VehicleStatus(), phev)
        assertTrue("battery" in battText || "charging" in battText, battText)
    }

    @Test
    fun summaryPrompt_prependsTheVehicleLine() {
        val text = summaryPrompt(car, null, UiState())
        assertTrue(text.startsWith("Ioniq 6 vehicle status:"), text)
        assertTrue("No live status" in text)
    }
}
