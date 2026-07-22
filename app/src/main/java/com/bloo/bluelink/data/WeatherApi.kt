package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Current conditions for a single point, normalised from the Open-Meteo response.
 * Temperatures are kept in Celsius; the UI converts to the user's chosen unit.
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
    /** When this reading was fetched (wall-clock millis). */
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    /** Same shape as [WearWeather], for mirroring a fetched reading to the watch. */
    fun toWear() = WearWeather(
        tempC = tempC, feelsLikeC = feelsLikeC, highC = highC, lowC = lowC,
        windKph = windKph, humidity = humidity, isDay = isDay, code = code,
    )
    // Straightforward Celsius-to-Fahrenheit conversions; kept as separate small
    // functions (rather than converting once at fetch time) so the canonical
    // stored value always stays Celsius and callers pick the unit at display time.
    fun tempF(): Double = tempC * 9 / 5 + 32
    fun feelsLikeF(): Double = feelsLikeC * 9 / 5 + 32
    // highC/lowC are nullable (Open-Meteo's daily block can be absent), so these
    // propagate null through rather than converting a missing reading into 32°F.
    fun highF(): Double? = highC?.let { it * 9 / 5 + 32 }
    fun lowF(): Double? = lowC?.let { it * 9 / 5 + 32 }

    /** Temperature as a rounded, unit-suffixed string. */
    fun tempLabel(fahrenheit: Boolean): String =
        if (fahrenheit) "${tempF().toInt()}°F" else "${tempC.toInt()}°C"

    // No unit suffix here (just the degree glyph) -- this is meant for compact UI
    // spots that already show the primary temp with its own unit label nearby.
    fun feelsLikeLabel(fahrenheit: Boolean): String =
        if (fahrenheit) "${feelsLikeF().toInt()}°" else "${feelsLikeC.toInt()}°"

    /**
     * "H:hi°  L:lo°" label for the day's high/low, in whichever unit is requested.
     * Returns null if either bound is missing (rather than a partial label) since
     * a lone high or lone low isn't a meaningful "high/low" summary.
     */
    fun highLowLabel(fahrenheit: Boolean): String? {
        val hi = (if (fahrenheit) highF() else highC)?.toInt() ?: return null
        val lo = (if (fahrenheit) lowF() else lowC)?.toInt() ?: return null
        return "H:$hi°  L:$lo°"
    }

    /** Maps the raw WMO [code] to the coarser [WeatherCode] bucket used by the UI. */
    val condition: WeatherCode get() = WeatherCode.from(code)
}

/**
 * The WMO weather codes Open-Meteo returns, grouped into the handful of
 * conditions worth distinguishing in the UI, each with a short label.
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
         * Buckets a raw WMO weather code into one of the coarser [WeatherCode]
         * values the UI actually distinguishes. Open-Meteo's WMO codes carry finer
         * distinctions (e.g. separate codes for light/moderate/heavy drizzle,
         * or with/without thunder+hail) that Bloo doesn't need separate icons or
         * labels for, so several raw codes intentionally map to the same bucket.
         * Any code not explicitly listed (including negative sentinel values used
         * elsewhere when a code couldn't be determined) falls through to UNKNOWN.
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
 * Free, key-less weather from Open-Meteo (https://open-meteo.com). Used both for
 * a user-set "home" location and for the live position of each car.
 */
object WeatherApi {

    // ignoreUnknownKeys so Open-Meteo adding new response fields later doesn't
    // break parsing; isLenient to tolerate minor JSON quirks from the API.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // A dedicated client (not shared with other API callers in the app) with
    // generous-but-bounded timeouts: weather is a nice-to-have, so we'd rather
    // give a flaky connection a real chance to succeed than fail fast, but still
    // cap it so a hung request doesn't block a caller indefinitely.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class Response(
        val current: Current? = null,
        val daily: Daily? = null,
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
        @SerialName("temperature_2m_max") val max: List<Double>? = null,
        @SerialName("temperature_2m_min") val min: List<Double>? = null,
    )

    /**
     * Fetch current conditions for [lat]/[lon], or null on any failure.
     *
     * Mechanism: runs on [Dispatchers.IO] since this is a blocking network call
     * (OkHttp's synchronous `execute()`, not the async callback API). Builds a
     * single GET request asking Open-Meteo for both the "current" block (temp,
     * feels-like, humidity, wind, day/night, weather code) and a one-day "daily"
     * block (used only for today's high/low), with wind pre-converted to km/h by
     * the API itself and the timezone auto-detected from the coordinates so the
     * "day" for the high/low matches the location's own calendar day rather than
     * the device's.
     *
     * The whole body is wrapped in [runCatching] and every failure path --
     * network/IO exception, non-2xx response, empty body, malformed JSON, or a
     * response missing the fields we actually need (`current` block or its
     * temperature) -- collapses to a plain `null` via early `return@use null`
     * exits or [getOrNull]. Callers therefore only need to handle "got a Weather"
     * vs. "didn't," never distinguish *why* a fetch failed.
     *
     * Fields with sensible fallbacks default rather than propagate a null: a
     * missing "feels like" falls back to the actual temperature, missing wind
     * falls back to 0, and a missing/malformed day flag defaults to daytime (1).
     * A missing weather code becomes -1, which [WeatherCode.from] maps to UNKNOWN.
     */
    suspend fun fetch(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                "wind_speed_10m,is_day,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&wind_speed_unit=kmh&forecast_days=1&timezone=auto"
            val request = Request.Builder().url(url).get().build()
            // .use{} ensures the response body/connection is closed either way,
            // even on one of the early return@use null exits below.
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
                )
            }
        }.getOrNull()
    }
}
