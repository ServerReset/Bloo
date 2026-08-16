package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.Vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct tests of the settings-search command parser and the two dynamic
 * toggle indexes (ToggleSettings, VehicleToggleSettings). Everything here was
 * `private` in SettingsScreen.kt -- unreachable outside a running Compose
 * screen -- until this file needed it bumped to `internal`; a Compose search
 * bar can't be driven by a plain JVM test, but the parsing and indexing logic
 * behind it doesn't touch Compose or Android at all, so it can be pinned
 * directly instead of only ever being checked by eye.
 *
 * The command tests matter beyond "does this string parse right": a false
 * positive here means [parseVehicleCommand] fires a real command against a
 * real car through [TileCommandRunner] that the user never actually asked
 * for, and a false negative silently drops one they did.
 */
class SettingsSearchTest {
    private val civic = Vehicle(
        vin = "1HGCM82633A123456", regId = "reg1", name = "Civic",
        model = "Civic", generation = "11", brandIndicator = "H", isEv = false,
    )
    private val ioniq = Vehicle(
        vin = "KM8K33AGXPU000001", regId = "reg2", name = "Ioniq 5",
        model = "Ioniq 5", generation = "1", brandIndicator = "H", isEv = true,
    )

    // --- parseClimateTemperature ---

    @Test
    fun temperature_prepositionForm() {
        assertEquals(64, parseClimateTemperature("start climate at 64", metric = false))
        assertEquals(70, parseClimateTemperature("heat civic to 70", metric = false))
    }

    @Test
    fun temperature_degreesForm() {
        assertEquals(72, parseClimateTemperature("set it to 72 degrees", metric = false))
    }

    @Test
    fun temperature_superlatives() {
        assertEquals(com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F.first, parseClimateTemperature("coldest", metric = false))
        assertEquals(com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F.last, parseClimateTemperature("warmest", metric = false))
    }

    @Test
    fun temperature_metricNoUnit_convertsCelsius() {
        // No explicit unit + metric=true reads the number as Celsius, per
        // parseClimateTemperature's own doc ("start climate at 20" on metric
        // means 20C, not 20F) -- so it must NOT come back as the literal 20.
        val f = parseClimateTemperature("start climate at 20", metric = true)
        assertNotNull(f)
        assertTrue(f!! > 20, "expected a Celsius-to-Fahrenheit conversion, got $f")
    }

    @Test
    fun temperature_bareNumberIsNotATemperature() {
        // The exact regression parseClimateTemperature's doc warns about: a
        // bare digit that's naming the CAR, not a temperature.
        assertNull(parseClimateTemperature("lock my ioniq 5", metric = false))
    }

    // --- parseVehicleCommand: existing phrasings, unaffected by this round ---

    @Test
    fun command_lockAndUnlock() {
        assertEquals("lock", parseVehicleCommand("lock the car")?.cmd)
        // "unlock" contains "lock" -- must not fall into the lock branch.
        assertEquals("unlock", parseVehicleCommand("unlock my civic")?.cmd)
    }

    @Test
    fun command_climateStartStopSmart() {
        assertEquals("climate_on", parseVehicleCommand("start the climate")?.cmd)
        assertEquals("smart", parseVehicleCommand("start smart climate")?.climateTarget)
        assertEquals("climate_off", parseVehicleCommand("stop the climate")?.cmd)
        val withTemp = parseVehicleCommand("start climate at 68")
        assertEquals("climate_on", withTemp?.cmd)
        assertEquals(TileCommandRunner.TEMP_PREFIX + "68", withTemp?.climateTarget)
    }

    @Test
    fun command_defrost() {
        val cmd = parseVehicleCommand("defrost the windshield")
        assertEquals("climate_on", cmd?.cmd)
        assertTrue(cmd?.climateTarget?.endsWith(TileCommandRunner.DEFROST_SUFFIX) == true)
    }

    @Test
    fun command_chargeLimitBeatsChargeStart() {
        // "set the charge limit to 80" contains "charg" -- the limit branch
        // must win, not the generic charge-start branch.
        val cmd = parseVehicleCommand("set the charge limit to 80")
        assertEquals("charge_limit", cmd?.cmd)
        assertEquals("80", cmd?.climateTarget)
    }

    @Test
    fun command_chargeStartStop() {
        assertEquals("charge_on", parseVehicleCommand("start charging")?.cmd)
        assertEquals("charge_off", parseVehicleCommand("stop charging")?.cmd)
    }

    @Test
    fun command_lightsAndHorn() {
        assertEquals("lights", parseVehicleCommand("flash the lights")?.cmd)
        assertEquals("horn", parseVehicleCommand("honk the horn")?.cmd)
    }

    @Test
    fun command_unrecognizedIsNull() {
        assertNull(parseVehicleCommand("what's my range"))
        assertNull(parseVehicleCommand("Ioniq 5"))
    }

    // --- parseVehicleCommand: the new bare heat/cool/warm phrasing ---

    @Test
    fun command_bareHeatToTemperature() {
        val cmd = parseVehicleCommand("heat civic to 80")
        assertEquals("climate_on", cmd?.cmd)
        assertEquals(TileCommandRunner.TEMP_PREFIX + "80", cmd?.climateTarget)
    }

    @Test
    fun command_bareCoolToTemperature() {
        val cmd = parseVehicleCommand("cool civic to 65")
        assertEquals("climate_on", cmd?.cmd)
        assertEquals(TileCommandRunner.TEMP_PREFIX + "65", cmd?.climateTarget)
    }

    @Test
    fun command_bareWarmToTemperature() {
        val cmd = parseVehicleCommand("warm the ioniq to 72")
        assertEquals("climate_on", cmd?.cmd)
        assertEquals(TileCommandRunner.TEMP_PREFIX + "72", cmd?.climateTarget)
    }

    @Test
    fun command_bareHeatVerbWithNoTemperatureIsNotACommand() {
        // The guard the new branch relies on: "heat" alone, with no number
        // attached at all, must NOT be read as a command -- otherwise every
        // unrelated sentence containing "heat" or "cool" would misfire.
        assertNull(parseVehicleCommand("heat civic"))
        assertNull(parseVehicleCommand("is the heat on"))
    }

    @Test
    fun command_heatDoesNotShadowExistingStartClimatePhrasing() {
        // "warm the car up" already matched via the existing "warm ... up"
        // pattern with no temperature at all -- confirms the new bare-verb
        // branch (which requires temp != null) never gets a chance to run
        // here and doesn't change this existing result.
        val cmd = parseVehicleCommand("warm the car up")
        assertEquals("climate_on", cmd?.cmd)
        assertEquals("default", cmd?.climateTarget)
    }

    // --- ToggleSettings: the app-wide dynamic toggle index ---

    @Test
    fun toggleSettings_hasNoDuplicateTitles() {
        val titles = ToggleSettings.map { it.title }
        assertEquals(titles.size, titles.toSet().size, "duplicate title in ToggleSettings: $titles")
    }

    @Test
    fun toggleSettings_checkedReflectsAppearance() {
        val spec = ToggleSettings.first { it.title == "Aurora background" }
        val on = SettingsStore.Appearance(auroraBackground = true)
        val off = SettingsStore.Appearance(auroraBackground = false)
        assertTrue(spec.checked(on, SettingsStore.NotificationPrefs(), UiState()))
        assertFalse(spec.checked(off, SettingsStore.NotificationPrefs(), UiState()))
    }

    @Test
    fun toggleSettings_gatedEntriesRespectVisibility() {
        val aiSpec = ToggleSettings.first { it.title == "On-device AI" }
        assertFalse(aiSpec.visible(UiState(aiSupported = false)))
        assertTrue(aiSpec.visible(UiState(aiSupported = true)))
    }

    // --- VehicleToggleSettings: the per-car dynamic toggle index ---

    @Test
    fun vehicleToggleSettings_coversEverySeatPositionHeatAndColdPlusSteeringPlusSections() {
        // 4 seat positions x (heat, cool) + steering wheel + every hideable section.
        val expected = SeatPositions.size * 2 + 1 + com.bloo.bluelink.data.HIDEABLE_SECTIONS.size
        assertEquals(expected, VehicleToggleSettings.size)
    }

    @Test
    fun vehicleToggleSettings_titlesIncludeTheVehicleName() {
        VehicleToggleSettings.forEach { spec ->
            assertTrue(spec.title(civic).endsWith(civic.name), "expected \"${spec.title(civic)}\" to end with ${civic.name}")
        }
    }

    @Test
    fun vehicleToggleSettings_seatHeatChecksThatVehiclesSeatConfig() {
        val driverHeat = VehicleToggleSettings.first { it.label == "Driver seat heat" }
        val stateOn = UiState(seatConfigs = mapOf(civic.vin to com.bloo.bluelink.data.SeatConfig(driverHeat = true)))
        val stateOff = UiState(seatConfigs = mapOf(civic.vin to com.bloo.bluelink.data.SeatConfig(driverHeat = false)))
        assertTrue(driverHeat.checked(civic, stateOn))
        assertFalse(driverHeat.checked(civic, stateOff))
        // A car with no seatConfigs entry at all falls back to SeatConfig()'s
        // own default (driverHeat = true) rather than throwing -- covers the
        // ioniq case where nothing has been configured yet.
        assertTrue(driverHeat.checked(ioniq, UiState()))
    }

    @Test
    fun vehicleToggleSettings_aiSectionRespectsAiEnabledGate() {
        val aiSection = VehicleToggleSettings.first { it.label == "Show AI summary" }
        assertFalse(aiSection.visible(civic, UiState(aiEnabled = false)))
        assertTrue(aiSection.visible(civic, UiState(aiEnabled = true)))
    }

    @Test
    fun vehicleToggleSettings_sectionShownIsInvertedFromHidden() {
        val chargeSection = VehicleToggleSettings.first { it.label == "Show Charge / fuel" }
        val hidden = UiState(hiddenPebbles = setOf("${civic.vin}:charge"))
        val shown = UiState(hiddenPebbles = emptySet())
        assertFalse(chargeSection.checked(civic, hidden))
        assertTrue(chargeSection.checked(civic, shown))
    }
}
