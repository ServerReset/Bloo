package com.bloo.bluelink.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * How long the non-blocking geocoder is given to answer before we give up and show raw
 * coordinates instead. See [reverseGeocode] for why this only bounds the API 33+ path.
 */
const val GEOCODE_TIMEOUT_MS = 6_000L

/**
 * Turn coordinates into a human place name, or null if that isn't possible.
 *
 * One implementation, because there were two and they were not equivalent. The watch
 * used the non-blocking [Geocoder.GeocodeListener] API on 33+ with a legacy fallback
 * below it, and checked [Geocoder.isPresent] first. The phone used only the deprecated
 * blocking overload, on every device, and checked nothing -- so the phone was still
 * doing the thing the watch's own comment says it moved off because "the legacy
 * overload can hang". The phone is also the surface that calls this from a Locate tap
 * the user is waiting on.
 *
 * Only the leaf, [formatPlaceName], had been shared.
 *
 * The timeout deliberately wraps only the 33+ branch, and that asymmetry is the point
 * rather than an oversight. `suspendCancellableCoroutine` gives the listener API a real
 * suspension point, so [withTimeoutOrNull] can resume its caller at
 * [GEOCODE_TIMEOUT_MS] and abandon a slow lookup. The legacy overload is a blocking
 * call with no suspension point inside it, so wrapping it the same way -- which both
 * copies did -- cannot resume early: structured concurrency waits for the block to
 * finish regardless, and the only thing gained is a number that looks like a bound and
 * isn't. It is dropped here instead of being carried across, so nothing claims a
 * guarantee it can't keep. On pre-33 devices this is no worse than before; those
 * platforms simply have no cancellable geocoder to offer.
 *
 * [Geocoder.isPresent] is checked up front: on a device with no geocoder backend the
 * call would otherwise be guaranteed to fail, which the phone had been paying for.
 */
suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull()?.let { formatPlaceName(it) })
                    }

                    override fun onError(message: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { formatPlaceName(it) }
            }.getOrNull()
        }
    }
}
