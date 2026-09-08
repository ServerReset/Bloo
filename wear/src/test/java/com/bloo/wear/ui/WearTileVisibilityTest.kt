package com.bloo.wear.ui

import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearWeather
import com.bloo.wear.CarView
import com.bloo.wear.WearTiles
import com.bloo.wear.WearUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [visibleTiles] -- which cards a car actually shows.
 *
 * This encodes a contract that is easy to state and easy to break silently: standalone,
 * the watch has every feature, because with no phone reachable it is the only way to
 * reach the car at all; linked to a phone, it simplifies to quick glances and controls.
 * Every rule here is a conditional over state pushed from another device, and getting one
 * wrong means a card that quietly never appears -- there is no error, just an absence.
 *
 * The [car] helper spells out all 56 CarView fields once so each test can say only what it
 * is actually about. CarView deliberately has no default values (a missing field at a real
 * call site should be a compile error, not a silent zero), so a test fixture is the right
 * place to absorb that, not the production type.
 */
class WearTileVisibilityTest {

    private fun car(
        hasBattery: Boolean = true,
        brand: Brand = Brand.HYUNDAI,
        lat: Double? = 37.0,
        lon: Double? = -122.0,
        hasLiveStatus: Boolean = true,
        doorsOpen: List<String> = emptyList(),
    ) = CarView(
        vin = "VIN1", name = "Ioniq 5", model = "Ioniq 5", brand = brand,
        hasBattery = hasBattery, percent = 70, rangeMi = 200, locked = true,
        climateOn = false, charging = false, pluggedIn = false, chargerLabel = null,
        timeToFullMin = null, acLimit = 80, dcLimit = 80, chargeLimit = 80,
        fetchedAt = 0L, doorsOpen = doorsOpen, windowsOpen = emptyList(),
        trunkOpen = false, hoodOpen = false, tireWarning = false, battery12v = null,
        lowFuel = false, washerLow = false, brakeLow = false, keyFobLow = false,
        odometer = null, lat = lat, lon = lon, locationName = null,
        tripsSupported = true, hornLightsSupported = true, engineOn = false,
        accessoryOn = false, defrostOn = false, tempSetting = null, tempSettingUnit = null,
        tireFl = false, tireFr = false, tireRl = false, tireRr = false,
        steerHeat = false, mirrorHeat = false, rearDefrost = false,
        seatFl = null, seatFr = null, seatRl = null, seatRr = null,
        battery12vHealth = null, battery12vNeedsAttention = false, fuelLevel = null,
        hasLiveStatus = hasLiveStatus, licensePlate = null,
        lastServiceMiles = null, serviceIntervalMiles = null,
    )

    // tempC/feelsLikeC are the only WearWeather fields without defaults; the values are
    // irrelevant here -- visibleTiles only asks whether a reading EXISTS.
    private val weather = WearExtras(homeWeather = WearWeather(tempC = 20.0, feelsLikeC = 20.0))

    // ---- the standalone / linked contract ----------------------------------

    @Test
    fun `standalone shows the deep tiles, a connected phone hides them`() {
        val c = car()
        val standalone = visibleTiles(WearUi(phoneConnected = false, extras = weather), c)
        val linked = visibleTiles(WearUi(phoneConnected = true, extras = weather), c)

        for (deep in PHONE_AVAILABLE_DEEP_TILES) {
            assertTrue(standalone.contains(deep), "standalone should keep $deep")
            assertFalse(linked.contains(deep), "a connected phone should hide $deep")
        }
        // The everyday controls survive either way -- that is what "simplify" means here.
        for (kept in listOf(WearTiles.SUMMARY, WearTiles.CLIMATE, WearTiles.CHARGE, WearTiles.MORE)) {
            assertTrue(standalone.contains(kept), "standalone should keep $kept")
            assertTrue(linked.contains(kept), "linked should keep $kept")
        }
    }

    @Test
    fun `hiding is a simplification, not a capability gate`() {
        // Every deep tile must be reachable standalone; only AI is genuinely phone-only.
        val standalone = visibleTiles(WearUi(phoneConnected = false, extras = weather), car())
        assertFalse(PHONE_AVAILABLE_DEEP_TILES.any { it !in standalone })
        assertFalse(standalone.contains(WearTiles.AI), "AI needs the phone's on-device model")
    }

    // ---- per-tile capability rules -----------------------------------------

    @Test
    fun `AI needs both the setting and a phone`() {
        fun tiles(enabled: Boolean, connected: Boolean) = visibleTiles(
            WearUi(
                phoneConnected = connected,
                extras = weather,
                settings = com.bloo.bluelink.data.WearSettingsPayload(aiEnabled = enabled),
            ),
            car(),
        )
        assertTrue(tiles(enabled = true, connected = true).contains(WearTiles.AI))
        assertFalse(tiles(enabled = false, connected = true).contains(WearTiles.AI))
        assertFalse(tiles(enabled = true, connected = false).contains(WearTiles.AI))
    }

    @Test
    fun `charge tiles need a battery, and limits also need a brand that reports them`() {
        val ui = WearUi(phoneConnected = false, extras = weather)
        val ev = visibleTiles(ui, car(hasBattery = true))
        assertTrue(ev.contains(WearTiles.CHARGE))
        assertTrue(ev.contains(WearTiles.LIMITS))

        val gas = visibleTiles(ui, car(hasBattery = false))
        assertFalse(gas.contains(WearTiles.CHARGE))
        assertFalse(gas.contains(WearTiles.LIMITS))

        // Canada never reports charge targets, so the editor would be permanently dead.
        val canada = visibleTiles(ui, car(brand = Brand.HYUNDAI_CA))
        assertFalse(canada.contains(WearTiles.LIMITS), "CA cannot report charge targets")
        assertTrue(canada.contains(WearTiles.CHARGE), "start/stop charging still works in CA")
    }

    @Test
    fun `location needs coordinates`() {
        val ui = WearUi(phoneConnected = false, extras = weather)
        assertTrue(visibleTiles(ui, car()).contains(WearTiles.LOCATION))
        assertFalse(visibleTiles(ui, car(lat = null, lon = null)).contains(WearTiles.LOCATION))
    }

    @Test
    fun `weather shows when known, or standalone with a location to fetch from`() {
        // Nothing known and a phone present: the phone would have pushed it, so no card.
        assertFalse(
            visibleTiles(WearUi(phoneConnected = true), car()).contains(WearTiles.WEATHER),
        )
        // Nothing known but standalone with coordinates: the watch can fetch it itself.
        assertTrue(
            visibleTiles(WearUi(phoneConnected = false), car()).contains(WearTiles.WEATHER),
        )
        // Standalone with no location either: nothing to fetch from.
        assertFalse(
            visibleTiles(WearUi(phoneConnected = false), car(lat = null, lon = null))
                .contains(WearTiles.WEATHER),
        )
        // Already known: shown regardless of the link.
        assertTrue(
            visibleTiles(WearUi(phoneConnected = true, extras = weather), car())
                .contains(WearTiles.WEATHER),
        )
    }

    @Test
    fun `diagnostics needs live status`() {
        val ui = WearUi(phoneConnected = false, extras = weather)
        assertTrue(visibleTiles(ui, car(hasLiveStatus = true)).contains(WearTiles.DIAGNOSTICS))
        assertFalse(visibleTiles(ui, car(hasLiveStatus = false)).contains(WearTiles.DIAGNOSTICS))
    }

    // ---- alerts and hidden sections ----------------------------------------

    @Test
    fun `an alert prepends its own tile ahead of everything`() {
        val ui = WearUi(phoneConnected = false, extras = weather)
        assertFalse(visibleTiles(ui, car()).contains(TILE_ALERTS))
        val withAlert = visibleTiles(ui, car(doorsOpen = listOf("front left")))
        assertEquals(TILE_ALERTS, withAlert.first(), "an open door outranks the summary")
    }

    @Test
    fun `a section hidden on the phone stays hidden here`() {
        val ui = WearUi(
            phoneConnected = false,
            extras = weather,
            settings = com.bloo.bluelink.data.WearSettingsPayload(
                hiddenSections = mapOf("VIN1" to setOf("location")),
            ),
        )
        assertFalse(visibleTiles(ui, car()).contains(WearTiles.LOCATION))
        assertTrue(visibleTiles(ui, car()).contains(WearTiles.SUMMARY))
    }

    @Test
    fun `no tile is ever listed twice`() {
        val tiles = visibleTiles(
            WearUi(phoneConnected = false, extras = weather),
            car(doorsOpen = listOf("front left")),
        )
        assertEquals(tiles.size, tiles.distinct().size)
    }
}
