package com.bloo.bluelink.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.bloo.bluelink.data.GeoLocation

// --- Service & links: the one place each external launch goes through ---

/**
 * The one safe Activity launch. True when something actually launched, so callers that need to know
 * fell-back from served (the update tile's "you must dismiss it yourself" path) can tell the
 * difference; silently ignoring a failed open is how an action button starts reading as dead.
 */
internal fun Context.tryStart(intent: Intent): Boolean = runCatching {
    if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}.isSuccess

/**
 * The one permission check. Five objects kept a `fun hasPermission(context)` that differed only in
 * the permission string and the OS level where the check stops being needed — and each had to be
 * re-read (or, more often, forgotten) by every new caller.
 */
@Suppress("ObsoleteSdkInt") // minSdk is a caller-supplied OS level, not a literal
internal fun Context.hasPermission(permission: String, minSdk: Int = 1): Boolean =
    android.os.Build.VERSION.SDK_INT < minSdk ||
        androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * The one location-permission check, accepting EITHER accuracy. The map's "you are here" dot and
 * the distance-to-car readout only need an approximate fix, and Android 12+ lets a user grant
 * COARSE without FINE -- a FINE-only test read that as "no permission" and silently hid the dot
 * forever, no matter how many times the map was reopened.
 */
internal fun Context.hasLocationPermission(): Boolean =
    hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Opens the car's location in the device's default Maps app -- a `geo:` intent rather than
 * hardcoding Google Maps, since the OS resolves it to whatever the user actually has set. Shared by
 * [LocationPebble]'s own "Open in maps" button and [CarMapFullScreenDialog]'s [MapFeature] row so
 * the two never drift on the URI format.
 */
internal fun openInExternalMaps(context: Context, location: GeoLocation, label: String) {
    val uri = (
        "geo:${location.latitude},${location.longitude}" +
            "?q=${location.latitude},${location.longitude}($label)"
    ).toUri()
    context.tryStart(Intent(Intent.ACTION_VIEW, uri))
}

/**
 * Shares the car's location through the system share sheet -- a Google-Maps link, so any receiving
 * app can resolve it.
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
