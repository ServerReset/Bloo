@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
package com.bloo.bluelink.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.formatSpeed

/** The icon for a condition, picking a sun/moon variant by day vs night. */
internal fun weatherIcon(code: WeatherCode, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code.toCode(), isDay)

@Composable
internal fun weatherTint(code: WeatherCode, isDay: Boolean): Color =
    com.bloo.uicommon.weatherTint(code.toCode(), isDay, MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * A compact one-line weather readout: icon, temperature and condition, with a
 * small caption (place name) underneath. Used inside the Location pebble.
 */
@Composable
internal fun WeatherStripe(weather: Weather, fahrenheit: Boolean, caption: String) {
    val tint = weatherTint(weather.condition, weather.isDay)
    Row(
        Modifier
            .fillMaxWidth()
            .outlinedPanel(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        Icon(weatherIcon(weather.condition, weather.isDay), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
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
 * Full weather detail for [weather]: icon, big temperature, condition, and
 * feels-like/high-low/humidity/wind rows. Used inside [LocationPebble] for the
 * car's own local weather -- this used to be the standalone "Weather" pebble's
 * body (a separate global readout of the user's configured "home" location,
 * shown identically on every car), folded in here once the Location pebble
 * became the one place a car's surroundings are shown. `state.homeWeather` and
 * [AppViewModel.loadHomeWeather] still exist -- [ClimatePebble] falls back to
 * home weather for its smart-climate ambient estimate when a car has no fix of
 * its own yet -- only the dedicated garage card for it is gone.
 */
@Composable
internal fun WeatherDetail(weather: Weather, fahrenheit: Boolean, metric: Boolean) {
    val tint = weatherTint(weather.condition, weather.isDay)
    // ONE child, not five. This is rendered inside [PopVisible]'s AnimatedVisibility, and
    // AnimatedVisibility's own layout places every root composable it is given at the SAME
    // origin -- it is a slot for one child (or for a container that stacks several). A bare
    // Row followed by four StatusRows therefore stacked all five on top of each other, which
    // is the reported "overlap on tons of the text" in the weather stats. A Column gives the
    // slot the single child it expects, and spaces the rows by the same 12dp the location
    // pebble's own Column was providing before this AnimatedVisibility sat between them.
    Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
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
        StatusRow("Feels like", weather.feelsLikeLabel(fahrenheit))
        weather.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
        weather.humidity?.let { StatusRow("Humidity", "$it%") }
        StatusRow("Wind", formatSpeed(weather.windKph, metric))
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
