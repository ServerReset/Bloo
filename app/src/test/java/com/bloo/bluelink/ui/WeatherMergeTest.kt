package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Weather
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The weather card's car-vs-phone merge decision: one merged "Here & at the car" block when the
 * phone is close enough that it is the same weather, two labelled blocks otherwise (or when there is
 * no phone fix to compare at all).
 */
class WeatherMergeTest {
    private fun weather() = Weather(
        tempC = 20.0, feelsLikeC = 20.0, highC = 25.0, lowC = 15.0,
        windKph = 10.0, humidity = 50, isDay = true, code = 0,
    )

    @Test
    fun mergesWhenThePhoneIsCloseToTheCar() {
        assertTrue(weatherLocationsMerge(weather(), 0.0))
        assertTrue(weatherLocationsMerge(weather(), 3.0))
        assertTrue(weatherLocationsMerge(weather(), 7.0))
    }

    @Test
    fun staysSeparateWhenFarOrUnknown() {
        assertFalse(weatherLocationsMerge(weather(), 7.1))
        assertFalse(weatherLocationsMerge(weather(), 100.0))
        // No phone fix at all -> nothing to merge with.
        assertFalse(weatherLocationsMerge(weather(), null))
        // No phone weather -> nothing to merge.
        assertFalse(weatherLocationsMerge(null, 1.0))
    }
}
