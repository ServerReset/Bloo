package com.bloo.bluelink.data

import android.content.Context

/**
 * The one permission check, for the phone, the widget and the watch. Five objects once kept a `fun hasPermission(context)` that
 * differed only in the permission string and the OS level where the check stops being needed; the watch and several phone files
 * still wrote it out by hand.
 */
@Suppress("ObsoleteSdkInt") // minSdk is a caller-supplied OS level, not a literal
fun Context.hasPermission(permission: String, minSdk: Int = 1): Boolean =
    android.os.Build.VERSION.SDK_INT < minSdk ||
        androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * The one location-permission check, accepting EITHER accuracy. The map's "you are here" dot and the distance-to-car readout only
 * need an approximate fix, and Android 12+ lets a user grant COARSE without FINE -- a FINE-only test read that as "no permission"
 * and silently hid the dot forever.
 */
fun Context.hasLocationPermission(): Boolean =
    hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
