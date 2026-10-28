package com.bloo.bluelink.wear

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.privatePrefs

/**
 * Which car the tile shows: the one the app was last showing, so the tile and the face agree about the car and
 * about what it can do. Plain prefs (a VIN is not a secret on its own, and the tile must read it without the app
 * running); a car that has since been removed falls back to the store's own selection.
 */
internal object WearTileCar {
    private const val KEY = "vin"

    private fun prefs(context: Context) =
        context.privatePrefs("bloo_watch_tile")

    /** Remembers [vin]; true when that changed the choice (so the tile needs redrawing). */
    fun save(context: Context, vin: String): Boolean {
        if (prefs(context).getString(KEY, null) == vin) return false
        prefs(context).edit { putString(KEY, vin) }
        return true
    }

    /** The remembered VIN, or null. */
    fun saved(context: Context): String? = prefs(context).getString(KEY, null)

    fun pick(context: Context, cars: List<VehicleSnapshot>): VehicleSnapshot? {
        val vin = prefs(context).getString(KEY, null) ?: return null
        return cars.firstOrNull { it.vin == vin }
    }
}
