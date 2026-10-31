package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartClimateTest {
    /** A reading at [feelsF] (apparent) and [tempF] (air), with everything else calm unless a test changes it. */
    private fun weather(
        tempF: Double = 80.0,
        feelsF: Double = tempF,
        humidity: Int? = 40,
        windKph: Double = 5.0,
        isDay: Boolean = true,
        hourly: List<HourPoint> = emptyList(),
    ) = Weather(
        tempC = (tempF - 32) * 5 / 9,
        feelsLikeC = (feelsF - 32) * 5 / 9,
        highC = null,
        lowC = null,
        windKph = windKph,
        humidity = humidity,
        isDay = isDay,
        code = 0,
        hourly = hourly,
    )

    private fun hour(tempF: Double) = HourPoint(time = "2026-10-30T12:00", tempC = (tempF - 32) * 5 / 9, code = 0, precipProbability = null)

    @Test
    fun withNoExtrasItMatchesTheOldRuleOnTheFeelsLikeTemperature() {
        // 88 feels-like, no humidity or wind to speak of, no forecast: the plan is the existing rule's answer for 88.
        val plan = smartClimatePlan(weather(tempF = 88.0, humidity = 30), fahrenheit = true)
        assertEquals(smartClimateTargetF(88), plan.targetF)
    }

    @Test
    fun humidityOnAHotDayCoolsALittleMore() {
        val dry = smartClimatePlan(weather(tempF = 88.0, humidity = 30))
        val humid = smartClimatePlan(weather(tempF = 88.0, humidity = 80))
        assertEquals(dry.targetF - 2, humid.targetF)
        assertTrue(humid.reasons.any { it.contains("Humid") })
    }

    @Test
    fun nightOnAHotDayCoolsALittleForSleep() {
        val day = smartClimatePlan(weather(tempF = 88.0, humidity = 30, isDay = true))
        val night = smartClimatePlan(weather(tempF = 88.0, humidity = 30, isDay = false))
        assertEquals(day.targetF - 1, night.targetF)
    }

    @Test
    fun aWarmingForecastStartsCoolerOnAHotDay() {
        val calm = smartClimatePlan(weather(tempF = 85.0, humidity = 30, hourly = listOf(hour(85.0), hour(86.0), hour(86.0))))
        val warming = smartClimatePlan(weather(tempF = 85.0, humidity = 30, hourly = listOf(hour(85.0), hour(92.0), hour(93.0))))
        assertEquals(calm.targetF - 2, warming.targetF)
        assertTrue(warming.reasons.any { it.startsWith("Warming") })
    }

    @Test
    fun pastChoicesAtASimilarTemperaturePullTheTarget() {
        val base = smartClimatePlan(weather(tempF = 88.0, humidity = 30))
        // The person has always set 74 when it is about this warm, warmer than the model's 65: the plan moves toward that, and says so.
        val learned = smartClimatePlan(
            weather(tempF = 88.0, humidity = 30),
            listOf(ClimateChoice(87, 74), ClimateChoice(89, 74), ClimateChoice(88, 74)),
        )
        assertTrue(learned.targetF > base.targetF)
        assertTrue(learned.reasons.any { it.startsWith("You usually set") })
    }

    @Test
    fun choicesFarAwayInTemperatureDoNotCount() {
        val base = smartClimatePlan(weather(tempF = 88.0, humidity = 30))
        val far = smartClimatePlan(weather(tempF = 88.0, humidity = 30), listOf(ClimateChoice(50, 66)))
        assertEquals(base.targetF, far.targetF)
    }

    @Test
    fun theTargetStaysInTheCarsRange() {
        val plan = smartClimatePlan(weather(tempF = 110.0, humidity = 90, isDay = false), listOf(ClimateChoice(110, 50)))
        assertTrue(plan.targetF in CLIMATE_TEMP_RANGE_F)
    }

    @Test
    fun choicesRoundTripThroughTheStoredText() {
        val choices = listOf(ClimateChoice(88, 70), ClimateChoice(52, 78))
        assertEquals(choices, decodeClimateChoices(encodeClimateChoices(choices)))
    }

    @Test
    fun aMalformedPairIsSkippedNotFatal() {
        assertEquals(listOf(ClimateChoice(88, 70)), decodeClimateChoices("88:70,garbage,x:y"))
    }
}
