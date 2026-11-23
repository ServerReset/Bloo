package com.bloo.bluelink.data

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins how the charging screens write energy, money and a brand-given distance, in either unit. */
class ChargeFormatTest {
    private lateinit var saved: Locale

    @BeforeTest
    fun pinLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(saved)

    @Test
    fun energyHasOneDecimal() = assertEquals("12.3 kWh", formatKwh(12.34))

    @Test
    fun moneyCarriesItsCurrencyWhenKnown() {
        assertEquals("12.40 CAD", formatMoney(12.4, "CAD"))
        assertEquals("12.40", formatMoney(12.4, null))
    }

    @Test
    fun distanceFollowsTheUnit() {
        assertEquals("10.0 km", formatKmDistance(10.0, metric = true))
        assertEquals("6.2 mi", formatKmDistance(10.0, metric = false))
        assertEquals("161 km", formatKmDistance(160.9, metric = true, decimals = 0))
        assertEquals("100 mi", formatKmDistance(160.9, metric = false, decimals = 0))
    }
}

/** Pins the forecast's bare degrees: a whole number in the preferred unit with no unit letter. */
class WeatherDegreesTest {
    @Test
    fun degreesInEitherUnit() {
        assertEquals("39°", weatherDegrees(3.9, fahrenheit = true))
        assertEquals("4°", weatherDegrees(3.9, fahrenheit = false))
    }
}
