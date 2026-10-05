package com.bloo.bluelink.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.ScrollState
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.HourPoint
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.weatherTemp
import kotlin.math.roundToInt

/** The icon for a condition, picking a sun/moon variant by day vs night. */
internal fun weatherIcon(code: WeatherCode, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code.toCode(), isDay)

/** A weather symbol in its condition's own tint -- the icon + tint + size every weather surface repeated. */
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
 * A compact one-line weather readout: icon, temperature and condition, with a
 * small caption (place name) underneath. Used inside the Location pebble when it
 * renders collapsed on the cover, where the full [WeatherBlock] has no room.
 */
@Composable
internal fun WeatherStripe(weather: Weather, fahrenheit: Boolean, caption: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .outlinedPanel(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        WeatherGlyph(weather.condition, weather.isDay, 30.dp, describe = false)
        Column(Modifier.weight(1f)) {
            Text(weather.condition.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RollingNumber(
            text = weather.tempLabel(fahrenheit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The full, information-dense weather block for ONE location: a big current
 * conditions hero (icon, temperature, condition), the next several hours as a
 * horizontal strip, the day's detail rows (feels-like/humidity/wind/high-low) and a
 * multi-day forecast. This is the redesigned replacement for the old
 * [WeatherDetail], which showed only current conditions.
 *
 * [title] labels which location this is ("At the car" / "Your location" / the merged
 * "Here & at the car"); it is shown as a small caption so two stacked blocks are
 * distinguishable at a glance. [subtitle] is the place name when one is known.
 */
@Composable
internal fun WeatherBlock(
    weather: Weather,
    fahrenheit: Boolean,
    metric: Boolean,
    title: String,
    subtitle: String? = null,
    /**
     * When true this is one half of a merged (car and phone co-located) readout, so the
     * block drops its own heading and the surrounding [WeatherLocations] draws one shared
     * heading instead -- avoids saying "Here & at the car" twice.
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
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The big current-conditions hero: 64dp condition icon, display-temperature, condition + feels-like. */
@Composable
private fun CurrentConditions(weather: Weather, fahrenheit: Boolean, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapSection),
    ) {
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
            Text(weather.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The next several hours as a horizontal strip: hourly cell (hour, icon, temperature,
 * optional precip %). Starts at the hour nearest the reading so the leftmost cell is
 * "now"-ish rather than the API's midnight. Scrolls horizontally when more hours exist
 * than fit, so a glance lands on the nearest hours first.
 */
@Composable
private fun WeatherHourlyStrip(weather: Weather, fahrenheit: Boolean) {
    // Nearest hour to whenever the reading was fetched, so the strip is anchored to "now".
    // HourPoint.time is the LOCATION's local wall-clock; the device's hour is a good-enough
    // proxy for "which cell is now" and avoids re-parsing the API's zone-offset strings.
    val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val startIndex = weather.hourly.indexOfFirst { (it.hour ?: 0) >= nowHour }.coerceAtLeast(0)
    val hours = weather.hourly.drop(startIndex).take(12)
    if (hours.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Text("Next hours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val scroll = rememberScrollState()
        val viewportPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val blurOk = canBlurBackdrops()
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { viewportPx.intValue = it.width }
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            hours.forEachIndexed { i, h ->
                HourCell(
                    hour = h,
                    fahrenheit = fahrenheit,
                    label = if (i == 0) "Now" else hourLabel(h.hour),
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        WeatherGlyph(hour.condition, true, 22.dp)
        Text(
            com.bloo.bluelink.data.weatherTemp(hour.tempC, fahrenheit),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            hour.precipProbability?.takeIf { it > 0 }?.let { "$it%" } ?: " ",
            style = MaterialTheme.typography.labelSmall,
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
 * The multi-day forecast: one row per day, "Today" for the first, weekday name
 * otherwise, with an icon, condition word and the day's high/low. The first
 * [MAX_FORECAST_DAYS] days are shown so the card stays bounded.
 */
@Composable
private fun WeatherForecastRow(weather: Weather, fahrenheit: Boolean) {
    val days = weather.daily.take(MAX_FORECAST_DAYS)
    if (days.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(GapHairline)) {
        Text("Forecast", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        days.forEachIndexed { i, day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                Text(
                    if (i == 0) "Today" else weekdayName(day.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.width(72.dp),
                    maxLines = 1,
                )
                WeatherGlyph(day.condition, true, 20.dp)
                Text(
                    day.condition.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val hi = (if (fahrenheit) day.highC?.let { it * 9 / 5 + 32 } else day.highC)?.roundToInt()
                val lo = (if (fahrenheit) day.lowC?.let { it * 9 / 5 + 32 } else day.lowC)?.roundToInt()
                Text(
                    buildString {
                        if (hi != null) append("$hi°")
                        if (hi != null && lo != null) append(" / ")
                        if (lo != null) append("$lo°")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "Thu" for an ISO date, falling back to the raw string when it can't be parsed. */
private fun weekdayName(date: String): String {
    val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return date
    return parsed.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}

/**
 * How close (miles) the phone must be to the car for their two weather blocks to be
 * merged into one "Here & at the car" readout rather than shown as two. Roughly
 * "same neighbourhood" -- close enough that the car's weather and yours are the same
 * weather, so showing two copies would be redundant.
 */
private const val WEATHER_MERGE_MILES = 7.0

/** How many day rows the multi-day forecast draws. */
private const val MAX_FORECAST_DAYS = 5

/**
 * The Location pebble's weather: the car's weather and (when the phone has a fix) the
 * phone's own, as either ONE merged block when you are within [WEATHER_MERGE_MILES] of
 * the car, or TWO clearly-labelled blocks when you are not. The merged case draws a
 * single shared heading and one set of current/forecast content (the two are the same
 * weather), while the separate case labels each location so a glance tells them apart.
 *
 * [place] is the car location's place name, [devicePlace] the phone's (may be null).
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
    val merged = phone != null && milesApart != null && milesApart <= WEATHER_MERGE_MILES
    Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        if (merged) {
            WeatherLocationHeading(
                title = "Here & at the car",
                subtitle = when {
                    place != null && devicePlace != null && place != devicePlace -> "$place · $devicePlace"
                    else -> place ?: devicePlace
                },
                weather = car,
            )
            WeatherBlock(car, fahrenheit, metric, title = "Here & at the car", merged = true)
        } else {
            WeatherBlock(
                car, fahrenheit, metric,
                title = "At the car",
                subtitle = place ?: "Car location",
            )
            if (phone != null) {
                WeatherBlock(
                    phone, fahrenheit, metric,
                    title = "Your location",
                    subtitle = devicePlace,
                )
            }
        }
    }
}

// --- Service & links ------------------------------------------------------


/**
 * The one safe Activity launch.
 *
 * `startActivity` from a non-Activity Context (`Application`, a `BroadcastReceiver`) throws
 * unless `FLAG_ACTIVITY_NEW_TASK` is set, and throws again when no app can serve the intent —
 * so every open-a-thing helper used to hand-roll the same `addFlags` + `runCatching` pair, of
 * which the codebase had accumulated several (maps, custom tabs, dial, share, release pages,
 * the OTA installer). All of them route through this now, so "can I open this" is answered
 * exactly once and a new external-intent surface can't silently skip the guard.
 *
 * True when something actually launched, so callers that need to know fell-back from served
 * (the update tile's "you must dismiss it yourself" path) can tell the difference; silently
 * ignoring a failed open is how an action button starts reading as dead.
 */
internal fun Context.tryStart(intent: Intent): Boolean = runCatching {
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}.isSuccess

/**
 * The one permission check.
 *
 * Five objects kept a `fun hasPermission(context)` that differed only in the permission
 * string and the OS level where the check stops being needed — and each had to be re-read
 * (or, more often, forgotten) by every new caller. One extension answers it: [minSdk]
 * encodes "below this OS level the permission is unconditionally granted", so a caller
 * states the OS-version knowledge it already has rather than re-deriving it.
 */
@Suppress("ObsoleteSdkInt") // minSdk is a caller-supplied OS level, not a literal
internal fun Context.hasPermission(permission: String, minSdk: Int = 1): Boolean =
    android.os.Build.VERSION.SDK_INT < minSdk ||
        androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * Opens the car's location in the device's default Maps app -- a `geo:` intent
 * rather than hardcoding Google Maps, since the OS resolves it to whatever the user
 * actually has set. Shared by [LocationPebble]'s own "Open in maps" button and
 * [CarMapFullScreenDialog]'s [MapFeature] row so the two never drift on the URI
 * format.
 */
internal fun openInExternalMaps(context: Context, location: GeoLocation, label: String) {
    val uri = (
        "geo:${location.latitude},${location.longitude}" +
            "?q=${location.latitude},${location.longitude}($label)"
    ).toUri()
    context.tryStart(Intent(Intent.ACTION_VIEW, uri))
}

/**
 * Shares the car's location through the system share sheet -- a Google-Maps link, so any
 * receiving app can resolve it. The second real [MapFeature] the expanded map's bottom row
 * gained (see [MapFeatureRow]); the share chooser is deliberately the OS's own, not a
 * hand-rolled contact picker.
 */
internal fun shareLocation(context: Context, location: GeoLocation, label: String) {
    val text = "$label: https://maps.google.com/?q=${location.latitude},${location.longitude}"
    context.tryStart(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share location",
        ),
    )
}

internal fun openUrl(context: Context, url: String) {
    val uri = url.toUri()
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .onFailure { context.tryStart(Intent(Intent.ACTION_VIEW, uri)) }
}

internal fun openApp(context: Context, packages: List<String>, fallbackUrl: String) {
    for (p in packages) {
        context.packageManager.getLaunchIntentForPackage(p)?.let {
            if (context.tryStart(it)) return
        }
    }
    openUrl(context, fallbackUrl)
}

internal fun dial(context: Context, number: String) {
    context.tryStart(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
}


/** How wide the strip's soft edge is: cells blur and fade as they slide into it and sharpen as they leave it. */
private val ScrollEdgeFade = 72.dp

/**
 * A cell of a horizontally scrolling strip that melts into blur as it nears an edge that has more
 * content past it, and comes back into focus as it scrolls toward the middle -- the same soft edge a
 * faded list has, with focus going as well as opacity. An edge with nothing beyond it stays sharp, so a
 * strip at rest is never blurry. Everything is read in the layer block, so scrolling re-records the
 * layer only, never recomposes; [blur] false (battery saver, pre-Android 12) keeps just the fade.
 */
@Composable
private fun Modifier.scrollEdgeBlur(scroll: ScrollState, viewportPx: androidx.compose.runtime.State<Int>, blur: Boolean): Modifier {
    var x by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var w by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fadePx = with(density) { ScrollEdgeFade.toPx() }
    val maxBlurPx = with(density) { 12.dp.toPx() }
    return this
        .onPlaced { x = it.positionInParent().x; w = it.size.width.toFloat() }
        .graphicsLayer {
            val vp = viewportPx.value.toFloat()
            if (vp <= 0f) return@graphicsLayer
            val left = x - scroll.value
            val right = left + w
            val leftGate = (scroll.value / fadePx).coerceIn(0f, 1f)
            val rightGate = ((scroll.maxValue - scroll.value) / fadePx).coerceIn(0f, 1f)
            val intoLeft = ((fadePx - left) / fadePx).coerceIn(0f, 1f) * leftGate
            val intoRight = ((right - (vp - fadePx)) / fadePx).coerceIn(0f, 1f) * rightGate
            val t = maxOf(intoLeft, intoRight)
            alpha = 1f - 0.8f * t
            renderEffect = if (blur && t > 0.03f) {
                androidx.compose.ui.graphics.BlurEffect(maxBlurPx * t, maxBlurPx * t, androidx.compose.ui.graphics.TileMode.Decal)
            } else {
                null
            }
        }
}
