package com.bloo.wear

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs instead of rethrowing an uncaught exception out of
// every read — this backs live tiles/complications, which must not throw.
private val Context.wearLocalStore by preferencesDataStore(
    name = "bloo_wear_local",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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
    const val AI = "ai"

    val DEFAULT_ORDER = listOf(
        SUMMARY, LOCK, CHARGE, LIMITS, AI, CLIMATE, SMART_CLIMATE, COMFORT, PRESETS,
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
        AI to "AI Summary",
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
        "ai" to listOf(WearTiles.AI),
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

/** A pool of concrete Tile services the user can add to their watch face — one
 *  per car, mirroring the phone's BlooTile1..12 Quick Settings pool. */
object WearTilePool {
    const val SIZE = 4
}

data class WearLocalSettings(
    val fontScale: Float = 1f,
    val unitSystem: String = "imperial",
    val tileOrder: List<String> = WearTiles.DEFAULT_ORDER,
    /** Which action chips the glanceable Tile shows (subset of [TILE_CHIP_ACTIONS]). */
    val tileActions: List<String> = listOf("lock", "climate"),
    /** Per pool-slot pinned car VIN; null = unconfigured (follows the selected car).
     *  Sized [WearTilePool.SIZE]. */
    val tileCarVins: List<String?> = List(WearTilePool.SIZE) { null },
    /** Update-check debounce/snooze/enable state - see WearViewModel's check. */
    val updateChecksEnabled: Boolean = true,
    val updateLastCheckedAt: Long = 0L,
    val updateSnoozeUntil: Long = 0L,
)

/** The actions a Tile chip can perform, in canonical order. */
val TILE_CHIP_ACTIONS = listOf("lock", "climate", "charge")

class WearLocalStore(private val context: Context) {

    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyTileOrder = stringPreferencesKey("tile_order")
    private val keyTileActions = stringPreferencesKey("tile_actions")
    private fun keyTileCarVin(index: Int) = stringPreferencesKey("tile_car_vin_$index")
    private val keyUnitSystem = stringPreferencesKey("unit_system")
    private val keyUpdateChecksEnabled = booleanPreferencesKey("update_checks_enabled")
    private val keyUpdateLastCheckedAt = longPreferencesKey("update_last_checked_at")
    private val keyUpdateSnoozeUntil = longPreferencesKey("update_snooze_until")

    // Pre-pool single-tile setting; migrated into slot 0 the first time that slot
    // is touched, and read as a fallback for slot 0 until then.
    private val keyTileCarVinLegacy = stringPreferencesKey("tile_car_vin")

    val flow: Flow<WearLocalSettings> = context.wearLocalStore.data.map { prefs ->
        val fontScale = (prefs[keyFontScale] ?: 1f).coerceIn(0.8f, 1.4f)
        val savedOrder = prefs[keyTileOrder]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: WearTiles.DEFAULT_ORDER
        // Merge: keep saved order, append any new keys not yet in the saved list.
        val merged = savedOrder.filter { it in WearTiles.DEFAULT_ORDER } +
            WearTiles.DEFAULT_ORDER.filter { it !in savedOrder }
        val actions = prefs[keyTileActions]
            ?.split(",")
            ?.filter { it in TILE_CHIP_ACTIONS }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("lock", "climate")
        val tileCarVins = (0 until WearTilePool.SIZE).map { i ->
            val v = prefs[keyTileCarVin(i)]?.takeIf { it.isNotBlank() }
            if (v == null && i == 0) prefs[keyTileCarVinLegacy]?.takeIf { it.isNotBlank() } else v
        }
        WearLocalSettings(
            fontScale = fontScale,
            unitSystem = prefs[keyUnitSystem] ?: "imperial",
            tileOrder = merged,
            tileActions = actions,
            tileCarVins = tileCarVins,
            updateChecksEnabled = prefs[keyUpdateChecksEnabled] ?: true,
            updateLastCheckedAt = prefs[keyUpdateLastCheckedAt] ?: 0L,
            updateSnoozeUntil = prefs[keyUpdateSnoozeUntil] ?: 0L,
        )
    }

    suspend fun setFontScale(f: Float) {
        context.wearLocalStore.edit { it[keyFontScale] = f.coerceIn(0.8f, 1.4f) }
    }

    suspend fun setUnitSystem(value: String) {
        context.wearLocalStore.edit { it[keyUnitSystem] = value }
    }

    suspend fun setUpdateChecksEnabled(enabled: Boolean) {
        context.wearLocalStore.edit { it[keyUpdateChecksEnabled] = enabled }
    }

    suspend fun setUpdateLastCheckedAt(millis: Long) {
        context.wearLocalStore.edit { it[keyUpdateLastCheckedAt] = millis }
    }

    suspend fun setUpdateSnoozeUntil(millis: Long) {
        context.wearLocalStore.edit { it[keyUpdateSnoozeUntil] = millis }
    }

    // The last tile chip clickable id that was actually executed. Tile State
    // (including lastClickableId) is persisted by the system and re-delivered on
    // every subsequent onTileRequest - including freshness/push refreshes - so
    // BlooTileService must dedupe or a single tap's command re-fires on every
    // later background render. Ids carry a per-render nonce, so equality here
    // means "this exact tap was already handled".
    private val keyTileLastClick = stringPreferencesKey("tile_last_click")

    suspend fun tileLastClick(): String? =
        context.wearLocalStore.data.first()[keyTileLastClick]

    suspend fun setTileLastClick(id: String) {
        context.wearLocalStore.edit { it[keyTileLastClick] = id }
    }

    suspend fun setTileOrder(order: List<String>) {
        context.wearLocalStore.edit { it[keyTileOrder] = order.joinToString(",") }
    }

    suspend fun setTileActions(actions: List<String>) {
        val clean = actions.filter { it in TILE_CHIP_ACTIONS }.distinct().take(2).ifEmpty { listOf("lock") }
        context.wearLocalStore.edit { it[keyTileActions] = clean.joinToString(",") }
    }

    /** Pin pool slot [index] to a car. Pass null to unconfigure/clear that slot. */
    suspend fun setTileCarVin(index: Int, vin: String?) {
        context.wearLocalStore.edit { prefs ->
            if (vin.isNullOrBlank()) prefs.remove(keyTileCarVin(index)) else prefs[keyTileCarVin(index)] = vin
            if (index == 0) prefs.remove(keyTileCarVinLegacy)
        }
    }
}
