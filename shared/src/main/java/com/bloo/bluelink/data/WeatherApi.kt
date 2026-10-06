package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Current conditions for a single point, normalised from the Open-Meteo response. Temperatures are
 * kept in Celsius; the UI converts to the user's chosen unit. A self-contained, key-less Open-Meteo
 * GET over okhttp.
 */
data class Weather(
    val tempC: Double,
    val feelsLikeC: Double,
    val highC: Double?,
    val lowC: Double?,
    val windKph: Double,
    val humidity: Int?,
    val isDay: Boolean,
    /** WMO weather interpretation code (see [WeatherCode]). */
    val code: Int,
    /**
     * Empty when Open-Meteo's hourly block is absent (an old cached reading, or a malformed
     * response), in which case the strip simply doesn't render rather than inventing points.
     */
    val hourly: List<HourPoint> = emptyList(),
    /**
     * The daily forecast, today first. [highC]/[lowC] above are the FIRST element of this for
     * backward compatibility; the rest drive the multi-day rows.
     */
    val daily: List<DayPoint> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    fun feelsLikeF(): Double = feelsLikeC * 9 / 5 + 32
    // highC/lowC are nullable (Open-Meteo's daily block can be absent), so these propagate null
    // through rather than converting a missing reading into 32°F.
    fun highF(): Double? = highC?.let { it * 9 / 5 + 32 }
    fun lowF(): Double? = lowC?.let { it * 9 / 5 + 32 }

    /**
     * Temperature as a rounded, unit-suffixed string. Delegates to the shared [weatherTemp] so the
     * C-to-F-and-round rule lives in exactly one place; this was a byte-for-byte second copy of it,
     * and weatherTemp's own KDoc had wrongly claimed the watch was "its only caller" while the
     * phone reached this identical copy instead.
     */
    fun tempLabel(fahrenheit: Boolean): String = weatherTemp(tempC, fahrenheit)

    // No unit suffix here (just the degree glyph) -- this is meant for compact UI spots that
    // already show the primary temp with its own unit label nearby.
    fun feelsLikeLabel(fahrenheit: Boolean): String =
        if (fahrenheit) "${feelsLikeF().roundToInt()}°" else "${feelsLikeC.roundToInt()}°"

    /**
     * "H:hi°  L:lo°" label for the day's high/low, in whichever unit is requested. Returns null if
     * either bound is missing (rather than a partial label) since a lone high or lone low isn't a
     * meaningful "high/low" summary.
     */
    fun highLowLabel(fahrenheit: Boolean): String? {
        val hi = (if (fahrenheit) highF() else highC)?.roundToInt() ?: return null
        val lo = (if (fahrenheit) lowF() else lowC)?.roundToInt() ?: return null
        return "H:$hi°  L:$lo°"
    }

    /** Maps the raw WMO [code] to the coarser [WeatherCode] bucket used by the UI. */
    val condition: WeatherCode get() = WeatherCode.from(code)
}

/**
 * One hour of the forecast: the ISO-local timestamp Open-Meteo labels the hour with (kept as the
 * raw string, e.g.
 */
data class HourPoint(
    val time: String,
    val tempC: Double,
    val code: Int,
    val precipProbability: Int?,
) {
    /** The hour-of-day (0..23) parsed off [time]'s "…THH:00" tail, or null if unparseable. */
    val hour: Int? get() = time.substringAfter('T', "").substringBefore(':').toIntOrNull()
    val condition: WeatherCode get() = WeatherCode.from(code)
}

/**
 * One day of the forecast: the ISO-local date ("2024-06-01"), the max/min temperature in Celsius,
 * and the WMO code representative of the day.
 */
data class DayPoint(
    val date: String,
    val highC: Double?,
    val lowC: Double?,
    val code: Int,
) {
    val condition: WeatherCode get() = WeatherCode.from(code)
    /** The "MM-DD" tail of [date], or the whole string if it has no dash. */
    val shortDate: String get() = date.substringAfter('-').takeIf { it.isNotBlank() } ?: date
}

/**
 * The WMO weather codes Open-Meteo returns, grouped into the handful of conditions worth
 * distinguishing in the UI, each with a short label.
 */
enum class WeatherCode(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    SNOW("Snow"),
    SHOWERS("Showers"),
    THUNDERSTORM("Thunderstorm"),
    UNKNOWN("—");

    /** A representative WMO integer for this condition — round-trips through [from]. */
    fun toCode(): Int = when (this) {
        CLEAR -> 0; PARTLY_CLOUDY -> 1; CLOUDY -> 3; FOG -> 45; DRIZZLE -> 51
        RAIN -> 61; SHOWERS -> 80; SNOW -> 71; THUNDERSTORM -> 95; UNKNOWN -> -1
    }

    companion object {
        /**
         * Buckets a raw WMO weather code into one of the coarser [WeatherCode] values the UI
         * actually distinguishes.
         */
        fun from(code: Int): WeatherCode = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            80, 81, 82 -> SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/**
 * Free, key-less weather from Open-Meteo (https://open-meteo.com). Used both for a user-set "home"
 * location and for the live position of each car.
 */
object WeatherApi {

    // ignoreUnknownKeys so Open-Meteo adding new response fields later doesn't break parsing;
    // isLenient to tolerate minor JSON quirks from the API.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // A dedicated client (its own pool) with generous-but-bounded timeouts: weather is a
    // nice-to-have, so give a flaky connection a real chance to succeed, but still cap it so a hung
    // request doesn't block a caller indefinitely. See ApiHttp.shortTimeoutClient.
    private val client: OkHttpClient = ApiHttp.shortTimeoutClient()

    @Serializable
    private data class Response(
        val current: Current? = null,
        val daily: Daily? = null,
        val hourly: Hourly? = null,
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double? = null,
        @SerialName("apparent_temperature") val apparent: Double? = null,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("wind_speed_10m") val windKph: Double? = null,
        @SerialName("is_day") val isDay: Int? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
    )

    @Serializable
    private data class Daily(
        val time: List<String>? = null,
        @SerialName("temperature_2m_max") val max: List<Double>? = null,
        @SerialName("temperature_2m_min") val min: List<Double>? = null,
        @SerialName("weather_code") val weatherCode: List<Int>? = null,
    )

    @Serializable
    private data class Hourly(
        val time: List<String>? = null,
        @SerialName("temperature_2m") val temperature: List<Double>? = null,
        @SerialName("weather_code") val weatherCode: List<Int>? = null,
        @SerialName("precipitation_probability") val precipProbability: List<Int?>? = null,
    )

    /**
     * Fetch current conditions for [lat]/[lon], or null on any failure. Mechanism: runs on
     * [Dispatchers.IO] since this is a blocking network call (OkHttp's synchronous `execute()`, not
     * the async callback API).
     */
    suspend fun fetch(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "wind_speed_10m,is_day,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&wind_speed_unit=kmh&forecast_days=7&timezone=auto"
            val request = Request.Builder().url(url).get().build()
            // .use{} ensures the response body/connection is closed either way, even on one of the
            // early return@use null exits below.
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val parsed = json.decodeFromString(Response.serializer(), body)
                val c = parsed.current ?: return@use null
                val temp = c.temperature ?: return@use null
                Weather(
                    tempC = temp,
                    feelsLikeC = c.apparent ?: temp,
                    highC = parsed.daily?.max?.firstOrNull(),
                    lowC = parsed.daily?.min?.firstOrNull(),
                    windKph = c.windKph ?: 0.0,
                    humidity = c.humidity,
                    isDay = (c.isDay ?: 1) == 1,
                    code = c.weatherCode ?: -1,
                    hourly = parsed.hourly?.toPoints().orEmpty(),
                    daily = parsed.daily?.toPoints().orEmpty(),
                )
            }
        }.getOrNull()
    }

    /**
     * Zips the hourly block's parallel arrays into [HourPoint]s, dropping any hour whose timestamp
     * or temperature the API omitted (rather than emitting a hole the strip would have to
     * special-case).
     */
    private fun Hourly.toPoints(): List<HourPoint> {
        val times = time ?: return emptyList()
        val temps = temperature ?: return emptyList()
        return times.indices.mapNotNull { i ->
            val t = times.getOrNull(i) ?: return@mapNotNull null
            val temp = temps.getOrNull(i) ?: return@mapNotNull null
            HourPoint(
                time = t,
                tempC = temp,
                code = weatherCode?.getOrNull(i) ?: -1,
                precipProbability = precipProbability?.getOrNull(i),
            )
        }
    }

    /** Zips the daily block's parallel arrays into [DayPoint]s the same "no holes" way. */
    private fun Daily.toPoints(): List<DayPoint> {
        val days = time ?: return emptyList()
        return days.mapIndexed { i, d ->
            DayPoint(
                date = d,
                highC = max?.getOrNull(i),
                lowC = min?.getOrNull(i),
                code = weatherCode?.getOrNull(i) ?: -1,
            )
        }
    }
}
