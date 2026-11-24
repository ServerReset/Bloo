package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-JVM tests for the forecast half of [Weather]: the [HourPoint]/[DayPoint] helpers
 * the redesigned weather section of the Location pebble relies on to label the hourly strip and the
 * multi-day rows.
 *
 * These pin the parsing rules that would otherwise fail silently -- a bad hour label
 * just reads "" and a bad weekday just reads the raw date, neither of which crashes or
 * looks obviously wrong, so nothing else would catch a regression here.
 */
class WeatherForecastTest {

    // ---- HourPoint: hour-of-day off the ISO-local timestamp ----

    @Test
    fun parsesHourOfDayFromTheApiTimestamp() {
        assertEquals(0, HourPoint("2024-06-01T00:00", 12.0, 0, null).hour)
        assertEquals(14, HourPoint("2024-06-01T14:00", 12.0, 0, null).hour)
        assertEquals(23, HourPoint("2024-06-01T23:00", 12.0, 0, null).hour)
    }

    /** An unparseable timestamp yields null rather than 0 -- "unknown" must not read as
     *  midnight, the same yield-nothing rule the rest of the app follows. */
    @Test
    fun unparseableHourIsNullNotMidnight() {
        assertNull(HourPoint("garbage", 12.0, 0, null).hour)
        assertNull(HourPoint("2024-06-01", 12.0, 0, null).hour)
        assertNull(HourPoint("2024-06-01T", 12.0, 0, null).hour)
    }

    @Test
    fun conditionBucketsTheWmoCode() {
        assertEquals(WeatherCode.CLEAR, HourPoint("2024-06-01T12:00", 20.0, 0, 0).condition)
        assertEquals(WeatherCode.RAIN, HourPoint("2024-06-01T12:00", 20.0, 63, 80).condition)
        assertEquals(WeatherCode.UNKNOWN, HourPoint("2024-06-01T12:00", 20.0, -1, null).condition)
    }

    // ---- DayPoint: weekday label and condition ----

    @Test
    fun dayConditionBucketsTheWmoCode() {
        assertEquals(WeatherCode.THUNDERSTORM, DayPoint("2024-06-01", 25.0, 15.0, 95).condition)
        assertEquals(WeatherCode.SNOW, DayPoint("2024-06-01", -1.0, -8.0, 73).condition)
    }

    // ---- Weather.highLowLabel: the first daily element is the day's high/low ----

    @Test
    fun highLowLabelReadsTheDailyBoundsAndConvertsUnits() {
        val w = Weather(
            tempC = 20.0, feelsLikeC = 20.0, highC = 25.0, lowC = 15.0,
            windKph = 10.0, humidity = 50, isDay = true, code = 0,
        )
        assertEquals("H:25°  L:15°", w.highLowLabel(fahrenheit = false))
        // 25°C = 77°F, 15°C = 59°F
        assertEquals("H:77°  L:59°", w.highLowLabel(fahrenheit = true))
    }

    /** Either bound missing yields null -- a lone high is not a meaningful "high/low". */
    @Test
    fun highLowLabelIsNullWhenEitherBoundIsMissing() {
        val onlyHigh = Weather(20.0, 20.0, 25.0, null, 10.0, 50, true, 0)
        val onlyLow = Weather(20.0, 20.0, null, 15.0, 10.0, 50, true, 0)
        assertNull(onlyHigh.highLowLabel(fahrenheit = true))
        assertNull(onlyLow.highLowLabel(fahrenheit = true))
    }

    // ---- defaults: an old-shaped reading renders no strip/rows, not a crash ----

    @Test
    fun hourlyAndDailyDefaultToEmptySoAnOlderReadingStillRenders() {
        val w = Weather(20.0, 20.0, 25.0, 15.0, 10.0, 50, true, 0)
        assertEquals(emptyList(), w.hourly)
        assertEquals(emptyList(), w.daily)
    }

    private fun day(vararg hours: Int) = hours.map { h -> HourPoint("2024-06-01T%02d:00".format(h), 10.0 + h, 0, null) } +
        HourPoint("2024-06-02T00:00", 9.0, 0, null)

    private fun weatherAt(local: String?, points: List<HourPoint>) =
        Weather(tempC = 10.0, feelsLikeC = 10.0, highC = null, lowC = null, windKph = 0.0, humidity = null, isDay = true, code = 0, hourly = points, localTime = local)

    @Test fun theNextHoursStartAtTheCurrentHourNotMidnight() {
        val w = weatherAt("2024-06-01T14:20", day(0, 1, 2, 13, 14, 15, 16))
        assertEquals(listOf("2024-06-01T14:00", "2024-06-01T15:00", "2024-06-01T16:00"), w.upcomingHours().take(3).map { it.time })
    }

    @Test fun theForecastPlacesOwnClockWinsOverThePhones() {
        // The car is somewhere it is 22:00 while the phone's clock says 09:00: the next hour is 22:00 there.
        val w = weatherAt("2024-06-01T22:05", day(9, 10, 22, 23))
        assertEquals("2024-06-01T22:00", w.upcomingHours(phoneHour = 9).first().time)
    }

    @Test fun lateAtNightTheNextHoursRollIntoTomorrow() {
        val w = weatherAt("2024-06-01T23:30", day(22, 23))
        assertEquals(listOf("2024-06-01T23:00", "2024-06-02T00:00"), w.upcomingHours().map { it.time })
    }

    @Test fun withoutThePlacesClockThePhonesHourIsUsed() {
        val w = weatherAt(null, day(0, 1, 13, 14))
        assertEquals("2024-06-01T13:00", w.upcomingHours(phoneHour = 13).first().time)
    }
}
