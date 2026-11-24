package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Canadian half-degree setpoint, encoded and decoded, and how it reads in each unit. */
class CanadaTempTest {
    private val api = CanadaApi(Brand.entries.first { it.isCanada })

    @Test fun aHalfDegreeSetpointKeepsItsHalfForACelsiusUser() {
        // 22.5°C on a 2020+ car is index 0x11 in the 14-31.5 table.
        val decoded = api.hexTempToC("11H", unit = 0, modelYear = 2022)
        assertEquals("22.5", decoded)
        assertEquals("22.5°C", degLabel(decoded!!, fahrenheit = false, sourceUnit = 0), "it used to read 23°C")
        assertEquals("73°F", degLabel(decoded, fahrenheit = true, sourceUnit = 0))
    }

    @Test fun aWholeDegreeReadsWithoutAFraction() {
        assertEquals("16", api.hexTempToC("04H", unit = 0, modelYear = 2022))
    }

    @Test fun everyFahrenheitSetpointRoundTrips() {
        for (f in 62..82) {
            for (year in listOf(2018, 2022)) {
                val hex = api.tempToHex(f, year)
                val celsius = api.hexTempToC(hex, unit = 0, modelYear = year)!!.toDouble()
                // Within the table's half degree.
                assertEquals(fToC(f.toDouble()), celsius, 0.26, "$f°F ($year) -> $hex -> $celsius°C")
            }
        }
    }

    @Test fun anythingThatIsNotAnEncodedSetpointPassesThrough() {
        assertEquals("OFF", api.hexTempToC("OFF", unit = 0, modelYear = 2022))
        assertEquals("0AH", api.hexTempToC("0AH", unit = 1, modelYear = 2022))
        assertEquals("21", api.hexTempToC("21", unit = 0, modelYear = 2022))
    }
}
