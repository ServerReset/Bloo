package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [ChargerApi]'s Open Charge Map parsing and request building. The sample
 * body follows the documented `/v3/poi/` shape (PascalCase fields, nested AddressInfo /
 * OperatorInfo / StatusType / Connections), trimmed to the fields the app reads.
 */
class ChargerApiTest {

    private val sample = """
        [
          {
            "ID": 101,
            "StatusType": {"IsOperational": true, "Title": "Operational"},
            "OperatorInfo": {"Title": "Electrify America"},
            "AddressInfo": {"Title": "Main St Plaza", "Latitude": 40.5, "Longitude": -74.25},
            "Connections": [
              {"ConnectionType": {"Title": "CCS (Type 1)"}, "PowerKW": 150.0},
              {"ConnectionType": {"Title": "CHAdeMO"}, "PowerKW": 50.0},
              {"ConnectionType": {"Title": "CCS (Type 1)"}, "PowerKW": 350.0}
            ]
          },
          {
            "ID": 102,
            "StatusType": {"IsOperational": false},
            "OperatorInfo": {"Title": "(Unknown Operator)"},
            "AddressInfo": {"Title": "", "Latitude": 40.6, "Longitude": -74.3},
            "Connections": [{"ConnectionType": {"Title": "Type 2"}}]
          },
          {
            "ID": 103,
            "AddressInfo": {"Title": "No coordinates"}
          },
          {
            "ID": 104,
            "AddressInfo": {"Title": "Bare", "Latitude": 1.0, "Longitude": 2.0}
          }
        ]
    """.trimIndent()

    @Test
    fun parsesNameCoordinatesNetworkAndPlugs() {
        val s = ChargerApi.parseStations(sample).first { it.id == 101 }
        assertEquals("Main St Plaza", s.name)
        assertEquals(40.5, s.latitude)
        assertEquals(-74.25, s.longitude)
        assertEquals("Electrify America", s.network)
        assertEquals(listOf("CCS (Type 1)", "CHAdeMO"), s.connectorTypes)
        assertTrue(s.operational)
    }

    @Test
    fun maxKwIsTheFastestConnector() {
        assertEquals(350.0, ChargerApi.parseStations(sample).first { it.id == 101 }.maxKw)
    }

    @Test
    fun anExplicitNotOperationalStatusIsKept() {
        assertFalse(ChargerApi.parseStations(sample).first { it.id == 102 }.operational)
    }

    @Test
    fun unknownOperatorAndBlankTitleFallBack() {
        val s = ChargerApi.parseStations(sample).first { it.id == 102 }
        assertNull(s.network)
        assertEquals("Charging station", s.name)
        assertNull(s.maxKw)
    }

    @Test
    fun aMissingStatusCountsAsOperational() {
        assertTrue(ChargerApi.parseStations(sample).first { it.id == 104 }.operational)
    }

    @Test
    fun entriesWithoutCoordinatesAreDropped() {
        assertEquals(listOf(101, 102, 104), ChargerApi.parseStations(sample).map { it.id })
    }

    @Test
    fun anEmptyResultIsAnEmptyListNotAnError() {
        assertEquals(emptyList(), ChargerApi.parseStations("[]"))
    }

    @Test
    fun theSearchUrlUsesOpenChargeMapAndNeverCarriesTheKey() {
        val url = ChargerApi.searchUrl(40.5, -74.25, 25.0, 150)
        assertTrue(url.startsWith("https://api.openchargemap.io/v3/poi/?"))
        assertTrue("latitude=40.5" in url && "longitude=-74.25" in url)
        assertTrue("distance=25.0" in url && "distanceunit=Miles" in url && "maxresults=150" in url)
        assertFalse("key=" in url)
    }
}
