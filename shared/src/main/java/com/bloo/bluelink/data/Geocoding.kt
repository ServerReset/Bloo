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
 * How long the non-blocking geocoder is given to answer before we give up and show raw coordinates
 * instead. See [reverseGeocode] for why this only bounds the API 33+ path.
 */
const val GEOCODE_TIMEOUT_MS = 6_000L

/**
 * Turn coordinates into a human place name (long and compact forms -- see [GeocodedPlace]), or null
 * if that isn't possible.
 */
suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): GeocodedPlace? {
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
