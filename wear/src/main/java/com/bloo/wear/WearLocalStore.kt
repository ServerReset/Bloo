package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearLocalStore by preferencesDataStore(name = "bloo_wear_local")

object WearTiles {
    const val SUMMARY = "summary"
    const val LOCK = "lock"
    const val CLIMATE = "climate"
    const val COMFORT = "comfort"
    const val PRESETS = "presets"
    const val CHARGE = "charge"
    const val LIMITS = "limits"
    const val LOCATION = "location"
    const val WEATHER = "weather"
    const val INFO = "info"
    const val DIAGNOSTICS = "diagnostics"
    const val ASSIST = "assist"
    const val MORE = "more"

    const val SMART_CLIMATE = "smart_climate"

    val DEFAULT_ORDER = listOf(
        SUMMARY, LOCK, CLIMATE, SMART_CLIMATE, COMFORT, PRESETS, CHARGE, LIMITS,
        LOCATION, WEATHER, INFO, DIAGNOSTICS, ASSIST, MORE,
    )

    val LABELS = mapOf(
        SUMMARY to "Summary",
        LOCK to "Lock / Unlock",
        CLIMATE to "Climate",
        SMART_CLIMATE to "Smart Climate",
        COMFORT to "Comfort",
        PRESETS to "Presets",
        CHARGE to "Charge",
        LIMITS to "Charge Limits",
        LOCATION to "Location",
        WEATHER to "Weather",
        INFO to "Info",
        DIAGNOSTICS to "Diagnostics",
        ASSIST to "Assist",
        MORE to "More",
    )
}

/**
 * The bridge between the phone's detail "pebbles" and the watch's tiles. One
 * phone pebble can expand into several watch tiles (e.g. the Climate pebble
 * becomes Climate, Smart Climate, Comfort and Presets), and the watch reorders
 * by whole pebble so the two stay in lock-step. Pebble keys match the phone's
 * `DEFAULT_SECTIONS`.
 */
object WearPebbles {
    /** Pebble order on the phone (mirrors the app's DEFAULT_SECTIONS). */
    val DEFAULT_ORDER = listOf(
        "summary", "controls", "charge", "ai", "climate",
        "info", "location", "weather", "trips", "diagnostics",
    )

    /** Each pebble → the watch tiles it owns, in display order. */
    private val TO_TILES = mapOf(
        "summary" to listOf(WearTiles.SUMMARY),
        "controls" to listOf(WearTiles.LOCK),
        "charge" to listOf(WearTiles.CHARGE, WearTiles.LIMITS),
        "ai" to emptyList(),
        "climate" to listOf(WearTiles.CLIMATE, WearTiles.SMART_CLIMATE, WearTiles.COMFORT, WearTiles.PRESETS),
        "info" to listOf(WearTiles.INFO),
        "location" to listOf(WearTiles.LOCATION),
        "weather" to listOf(WearTiles.WEATHER),
        "trips" to emptyList(),           // Trips lives inside the More tile.
        "diagnostics" to listOf(WearTiles.DIAGNOSTICS),
    )

    /** Watch-only tiles with no phone pebble — always appended, in this order. */
    private val TAIL = listOf(WearTiles.ASSIST, WearTiles.MORE)

    val LABELS = mapOf(
        "summary" to "Summary",
        "controls" to "Controls",
        "charge" to "Charge",
        "ai" to "AI Summary",
        "climate" to "Climate",
        "info" to "Info",
        "location" to "Location",
        "weather" to "Weather",
        "trips" to "Trips",
        "diagnostics" to "Diagnostics",
    )

    /** Pebbles the user may reorder (Summary is pinned first, like the phone). */
    fun reorderable(order: List<String>): List<String> =
        normalize(order).filter { it != "summary" }

    /** Clean an incoming order: known keys only, summary first, append any
     *  missing defaults so a new pebble never disappears. */
    fun normalize(order: List<String>): List<String> {
        val known = order.filter { it in DEFAULT_ORDER }.distinct()
        val merged = listOf("summary") + known.filter { it != "summary" } +
            DEFAULT_ORDER.filter { it !in known && it != "summary" }
        return merged.distinct()
    }

    /** Expand a pebble order into the flat watch-tile order. */
    fun tilesFor(pebbleOrder: List<String>): List<String> {
        val src = normalize(pebbleOrder)
        val tiles = ArrayList<String>()
        for (p in src) tiles += TO_TILES[p].orEmpty()
        tiles += TAIL
        return tiles.distinct()
    }
}

data class WearLocalSettings(
    val fontScale: Float = 1f,
    val tileOrder: List<String> = WearTiles.DEFAULT_ORDER,
)

class WearLocalStore(private val context: Context) {

    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyTileOrder = stringPreferencesKey("tile_order")

    val flow: Flow<WearLocalSettings> = context.wearLocalStore.data.map { prefs ->
        val fontScale = (prefs[keyFontScale] ?: 1f).coerceIn(0.8f, 1.4f)
        val savedOrder = prefs[keyTileOrder]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: WearTiles.DEFAULT_ORDER
        // Merge: keep saved order, append any new keys not yet in the saved list.
        val merged = savedOrder.filter { it in WearTiles.DEFAULT_ORDER } +
            WearTiles.DEFAULT_ORDER.filter { it !in savedOrder }
        WearLocalSettings(fontScale = fontScale, tileOrder = merged)
    }

    suspend fun setFontScale(f: Float) {
        context.wearLocalStore.edit { it[keyFontScale] = f.coerceIn(0.8f, 1.4f) }
    }

    suspend fun setTileOrder(order: List<String>) {
        context.wearLocalStore.edit { it[keyTileOrder] = order.joinToString(",") }
    }
}
