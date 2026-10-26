package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Battery12V
import com.bloo.bluelink.data.EvStatus
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.TimeValue
import com.bloo.bluelink.data.RemainTime2
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search answers with what the app knows: typing "battery" shows every car's battery, with its live value, not only an AI sentence. */
class SearchFactsTest {
    private fun car(vin: String, name: String) =
        Vehicle(vin = vin, regId = "r-$vin", name = name, model = "2023 IONIQ 5", generation = "5", brandIndicator = "H", isEv = true)

    private val daisy = car("V1", "Daisy")
    private val lana = car("V2", "Lana")

    private fun state(): UiState = UiState(
        vehicles = listOf(daisy, lana),
        statuses = mapOf(
            "V1" to VehicleStatus(
                doorLock = true,
                battery = Battery12V(batSoc = 87),
                evStatus = EvStatus(batteryCharge = true, batteryStatus = 80, batteryPlugin = 2, remainTime2 = RemainTime2(atc = TimeValue(value = 80.0))),
            ),
            "V2" to VehicleStatus(doorLock = false, evStatus = EvStatus(batteryStatus = 45, batteryPlugin = 0)),
        ),
    )

    private val appearance = SettingsStore.Appearance()
    private fun hits(query: String) = rankFacts(searchTokens(query), carFacts(state(), appearance))

    @Test fun batteryShowsTheBatteryOfEveryCarAndTheTwelveVoltOneToo() {
        val h = hits("battery")
        assertEquals(listOf("Battery", "Battery", "12V battery"), h.map { it.label }.sortedBy { if (it == "Battery") 0 else 1 })
        assertEquals(setOf("Daisy", "Lana"), h.filter { it.label == "Battery" }.map { it.car }.toSet())
    }

    @Test fun aFactCarriesTheLiveValueAndWhatTheCarIsDoing() {
        val daisyBattery = hits("battery").first { it.car == "Daisy" && it.label == "Battery" }
        assertEquals("80%", daisyBattery.value)
        assertTrue(daisyBattery.detail.orEmpty().contains("Charging"), "detail was ${daisyBattery.detail}")
        assertTrue(daisyBattery.detail.orEmpty().contains("1h 20m to full"), "detail was ${daisyBattery.detail}")
    }

    @Test fun theBatteryOutranksTheTwelveVoltBatteryForTheWordBattery() {
        assertEquals("Battery", hits("battery").first().label)
    }

    @Test fun aCarNameAnswersWithThatCarsFactsOnly() {
        val h = hits("daisy")
        assertTrue(h.isNotEmpty())
        assertTrue(h.all { it.car == "Daisy" })
    }

    @Test fun lockedAndUnlockedAreAnswers() {
        val doors = hits("locked").filter { it.label == "Doors" }
        assertEquals(setOf("Locked", "Unlocked"), doors.map { it.value }.toSet())
    }

    @Test fun aCarWithNoReadingYetAnswersNothing() {
        val none = UiState(vehicles = listOf(daisy))
        assertTrue(carFacts(none, appearance).isEmpty())
    }

    @Test fun anEmptyQueryAsksForNothing() {
        assertTrue(rankFacts(emptyList(), carFacts(state(), appearance)).isEmpty())
    }

    private fun pieces(query: String) = rankPieces(searchTokens(query), carFacts(state(), appearance))

    @Test fun batteryLiftsTheChargePieceOfEveryCarAndTheTwelveVoltOne() {
        val p = pieces("battery")
        assertEquals(setOf("V1" to FactTopic.CHARGE, "V2" to FactTopic.CHARGE, "V1" to FactTopic.TWELVE_VOLT), p.map { it.vin to it.topic }.toSet())
        assertEquals(p.size, p.map { it.vin to it.topic }.toSet().size, "each piece appears once")
        assertEquals(FactTopic.CHARGE, p.first().topic)
    }

    @Test fun rangeAndBatteryAreOnePiecePerCarNotTwo() {
        val p = pieces("battery range").filter { it.vin == "V1" && it.topic == FactTopic.CHARGE }
        assertEquals(1, p.size)
    }

    @Test fun lockedLiftsTheDoorsPiece() {
        assertTrue(pieces("locked").any { it.topic == FactTopic.DOORS })
    }
}
