package com.bloo.bluelink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.HourPoint
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.weatherTemp
import kotlin.math.roundToInt
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.canBlurBackdrops
import com.bloo.uicommon.weatherIcon
import com.bloo.uicommon.weatherTint

/** The icon for a condition, picking a sun/moon variant by day vs night. */
internal fun weatherIcon(code: WeatherCode, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code.toCode(), isDay)

/**
 * A weather symbol in its condition's own tint -- the icon + tint + size every weather surface
 * repeated.
 */
@Composable
internal fun WeatherGlyph(code: WeatherCode, isDay: Boolean, size: Dp, describe: Boolean = true) {
    Icon(
        weatherIcon(code, isDay),
        contentDescription = if (describe) code.label else null,
        tint = weatherTint(code, isDay),
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun weatherTint(code: WeatherCode, isDay: Boolean): Color =
    com.bloo.uicommon.weatherTint(code.toCode(), isDay, MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * A compact one-line weather readout: icon, temperature and condition, with a small [caption] underneath
 * (none when the pebble's own header already says where the car is). Used inside the Location pebble when it renders collapsed in a glance context,
 * where the full [WeatherBlock] has no room.
 */
@Composable
internal fun WeatherStripe(weather: Weather, fahrenheit: Boolean, caption: String?) {
    Row(
        Modifier.fillMaxWidth().outlinedPanel(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        WeatherGlyph(weather.condition, weather.isDay, 30.dp, describe = false)
        Column(Modifier.weight(1f)) {
            RevealText(weather.condition.label, fontWeight = FontWeight.SemiBold, style = PrimaryTextStyle())
            caption?.let { MutedText(it) }
        }
        RollingNumber(text = weather.tempLabel(fahrenheit), style = PrimaryTextStyle(), fontWeight = FontWeight.Bold)
    }
}

/**
 * The full, information-dense weather block for ONE location: a big current conditions hero (icon,
 * temperature, condition), the next several hours as a horizontal strip, the day's detail rows
 * (feels-like/humidity/wind/high-low) and a multi-day forecast.
 */
@Composable
internal fun WeatherBlock(
    weather: Weather,
    fahrenheit: Boolean,
    metric: Boolean,
    title: String,
    subtitle: String? = null,
    /**
     * When true this is one half of a merged (car and phone co-located) readout, so the block drops
     * its own heading and the surrounding [WeatherLocations] draws one shared heading instead --
     * avoids saying "Here & at the car" twice.
     */
    merged: Boolean = false,
) {
    val tint = weatherTint(weather.condition, weather.isDay)
    Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        if (!merged) WeatherLocationHeading(title, subtitle, weather)
        CurrentConditions(weather, fahrenheit, tint)
        if (weather.hourly.isNotEmpty()) WeatherHourlyStrip(weather, fahrenheit)
        WeatherDetailRows(weather, fahrenheit, metric)
        if (weather.daily.isNotEmpty()) WeatherForecastRow(weather, fahrenheit)
    }
}

/** The small "where this weather is" heading shared by a standalone and a merged block. */
@Composable
private fun WeatherLocationHeading(title: String, subtitle: String?, weather: Weather) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
        WeatherGlyph(weather.condition, weather.isDay, 16.dp, describe = false)
        RevealText(title, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            RevealText(subtitle, style = SecondaryTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The big current-conditions hero: 64dp condition icon, display-temperature, condition +
 * feels-like.
 */
@Composable
private fun CurrentConditions(weather: Weather, fahrenheit: Boolean, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapSection)) {
        Icon(
            weatherIcon(weather.condition, weather.isDay),
            contentDescription = weather.condition.label,
            tint = tint,
            modifier = Modifier.size(64.dp),
        )
        Column(Modifier.weight(1f)) {
            RollingNumber(
                text = weather.tempLabel(fahrenheit),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            RevealText(weather.condition.label, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The next several hours as a horizontal strip: hourly cell (hour, icon, temperature, optional
 * precip %). Scrolls horizontally when more hours exist than fit, so a glance lands on the nearest
 * hours first.
 */
@Composable
private fun WeatherHourlyStrip(weather: Weather, fahrenheit: Boolean) {
    val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val startIndex = weather.hourly.indexOfFirst { (it.hour ?: 0) >= nowHour }.coerceAtLeast(0)
    val hours = weather.hourly.drop(startIndex).take(12)
    if (hours.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        MutedText("Next hours")
        val scroll = rememberScrollState()
        val viewportPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val blurOk = canBlurBackdrops()
        // The strip runs to the pebble's own edges (the cards still start at its content inset), so cards melt
        // out and in at the true edge of the card, not at the inset text column.
        Row(
            Modifier.fillMaxWidth().bleedHorizontally(PebbleContentInset)
                .onSizeChanged { viewportPx.intValue = it.width }
                .horizontalScroll(scroll)
                .padding(horizontal = PebbleContentInset),
            horizontalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            hours.forEachIndexed { i, h ->
                HourCell(
                    hour = h, fahrenheit = fahrenheit, label = if (i == 0) "Now" else hourLabel(h.hour),
                    modifier = Modifier.scrollEdgeBlur(scroll, viewportPx, blurOk),
                )
            }
        }
    }
}

/** A 12-hour wall-clock label ("2 PM") from a 0..23 [hour], or empty when unknown. */
private fun hourLabel(hour: Int?): String {
    if (hour == null) return ""
    val normalized = (hour % 12).let { if (it == 0) 12 else it }
    return "$normalized ${if (hour < 12) "AM" else "PM"}"
}

@Composable
private fun HourCell(hour: HourPoint, fahrenheit: Boolean, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.width(56.dp).outlinedPanel(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GapHairline),
    ) {
        RevealText(label, style = SecondaryTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        WeatherGlyph(hour.condition, true, 22.dp)
        RevealText(
            com.bloo.bluelink.data.weatherTemp(hour.tempC, fahrenheit),
            style = PrimaryTextStyle(),
            fontWeight = FontWeight.SemiBold,
        )
        RevealText(
            hour.precipProbability?.takeIf { it > 0 }?.let { "$it%" } ?: " ",
            style = SecondaryTextStyle(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** Feels-like / humidity / wind rows and the day's high-low, as compact [StatusRow]s. */
@Composable
private fun WeatherDetailRows(weather: Weather, fahrenheit: Boolean, metric: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(GapHairline)) {
        StatusRow("Feels like", weather.feelsLikeLabel(fahrenheit))
        weather.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
        weather.humidity?.let { StatusRow("Humidity", "$it%") }
        StatusRow("Wind", formatSpeed(weather.windKph, metric))
    }
}

/**
 * The multi-day forecast: one row per day with a temperature RANGE BAR scaled across the whole week,
 * so a glance shows which day is the warm or cold outlier without reading every number. "Today" for
 * the first row, the weekday name otherwise. The first [MAX_FORECAST_DAYS] days are drawn so the
 * card stays bounded.
 */
@Composable
private fun WeatherForecastRow(weather: Weather, fahrenheit: Boolean) {
    val days = weather.daily.take(MAX_FORECAST_DAYS)
    if (days.isEmpty()) return
    // The week's own extremes: every bar is drawn against these, so the rows compare directly.
    val weekLow = days.mapNotNull { dayTemp(it.lowC, fahrenheit) }.minOrNull()
    val weekHigh = days.mapNotNull { dayTemp(it.highC, fahrenheit) }.maxOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        MutedText("This week")
        days.forEachIndexed { i, day ->
            val hi = dayTemp(day.highC, fahrenheit)
            val lo = dayTemp(day.lowC, fahrenheit)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapRow),
            ) {
                RevealText(
                    if (i == 0) "Today" else weekdayName(day.date),
                    style = SecondaryTextStyle(),
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.width(58.dp),
                    maxLines = 1,
                )
                WeatherGlyph(day.condition, true, 20.dp)
                DayRangeBar(low = lo, high = hi, weekLow = weekLow, weekHigh = weekHigh, modifier = Modifier.weight(1f))
                RevealText(
                    buildString {
                        if (hi != null) append("$hi°")
                        if (hi != null && lo != null) append(" / ")
                        if (lo != null) append("$lo°")
                    },
                    style = SecondaryTextStyle(),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A day's temperature in the display unit, rounded; null when the source is unknown. */
private fun dayTemp(c: Double?, fahrenheit: Boolean): Int? =
    c?.let { (if (fahrenheit) it * 9 / 5 + 32 else it).roundToInt() }

/**
 * One day's low-to-high as a segment on a shared track, positioned against the week's coldest low and
 * warmest high, so every row is comparable at a glance and the warm days visibly lean right.
 */
@Composable
private fun DayRangeBar(low: Int?, high: Int?, weekLow: Int?, weekHigh: Int?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(6.dp)) {
        val r = size.height / 2f
        drawRoundRect(color = track, size = size, cornerRadius = CornerRadius(r))
        if (low == null || high == null || weekLow == null || weekHigh == null) return@Canvas
        val span = (weekHigh - weekLow).coerceAtLeast(1)
        val start = ((low - weekLow).toFloat() / span).coerceIn(0f, 1f) * size.width
        val end = ((high - weekLow).toFloat() / span).coerceIn(0f, 1f) * size.width
        // A minimum, so a single-degree day still shows a mark rather than a hairline.
        val w = (end - start).coerceAtLeast(r * 2f).coerceAtMost(size.width - start)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), accent)),
            topLeft = Offset(start, 0f),
            size = Size(w, size.height),
            cornerRadius = CornerRadius(r),
        )
    }
}

/** "Thu" for an ISO date, falling back to the raw string when it can't be parsed. */
private fun weekdayName(date: String): String {
    val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return date
    return parsed.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}

/**
 * How close (miles) the phone must be to the car for their two weather blocks to be merged into one
 * "Here & at the car" readout rather than shown as two. Roughly "same neighbourhood" -- close
 * enough that the car's weather and yours are the same weather, so showing two copies would be
 * redundant.
 */
private const val WEATHER_MERGE_MILES = 7.0

/** How many day rows the multi-day forecast draws. */
private const val MAX_FORECAST_DAYS = 5

/**
 * Whether the car's and the phone's weather are close enough to show as ONE merged "Here & at the
 * car" block. False (two labelled blocks) whenever there is no phone fix to compare, or it is
 * farther than [WEATHER_MERGE_MILES].
 */
internal fun weatherLocationsMerge(phone: Weather?, milesApart: Double?): Boolean =
    phone != null && milesApart != null && milesApart <= WEATHER_MERGE_MILES

/**
 * The Location pebble's weather: the car's weather and (when the phone has a fix) the phone's own,
 * as either ONE merged block when you are within [WEATHER_MERGE_MILES] of the car, or TWO
 * clearly-labelled blocks when you are not.
 */
@Composable
internal fun WeatherLocations(
    car: Weather,
    phone: Weather?,
    place: String?,
    devicePlace: String?,
    milesApart: Double?,
    fahrenheit: Boolean,
    metric: Boolean,
) {
    val merged = weatherLocationsMerge(phone, milesApart)
    Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        if (merged) {
            WeatherLocationHeading(
                title = "Here & at the car",
                // The car's address is the pebble's own header; say only where the phone is, when that is somewhere else.
                subtitle = if (place == null) devicePlace else devicePlace?.takeIf { it != place },
                weather = car,
            )
            WeatherBlock(car, fahrenheit, metric, title = "Here & at the car", merged = true)
        } else {
            WeatherBlock(car, fahrenheit, metric, title = "At the car", subtitle = if (place == null) "Car location" else null)
            if (phone != null) WeatherBlock(phone, fahrenheit, metric, title = "Your location", subtitle = devicePlace)
        }
    }
}
