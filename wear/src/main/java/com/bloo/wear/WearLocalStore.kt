package com.bloo.wear

import android.content.Context
import android.util.Base64
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
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
        "summary", "controls", "charge", "climate", "ai",
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

    /** Expand a pebble order into the flat watch-tile order, dropping any pebble
     *  in [hiddenPebbles] (synced from the phone's per-car hidden-section
     *  setting). Filtering happens after [normalize] so a pebble the user hid
     *  is still excluded here without being mistaken by normalize() for a new,
     *  unknown pebble that must be appended back in. */
    fun tilesFor(pebbleOrder: List<String>, hiddenPebbles: Set<String> = emptySet()): List<String> {
        val src = normalize(pebbleOrder)
        val tiles = ArrayList<String>()
        for (p in src) {
            if (p in hiddenPebbles) continue
            tiles += TO_TILES[p].orEmpty()
        }
        tiles += TAIL
        return tiles.distinct()
    }
}

/** A pool of concrete Tile services the user can add to their watch face — one
 *  per car, mirroring the phone's BlooTile1..12 Quick Settings pool. */
object WearTilePool {
    const val SIZE = 4
}

/** Valid [WearLocalSettings.pinLockTiming] values, same vocabulary as the phone's LockTiming. */
val PIN_LOCK_TIMINGS = setOf("off", "immediate", "1min", "5min", "10min")

data class WearLocalSettings(
    val fontScale: Float = 1f,
    val unitSystem: String = "imperial",
    /** Which action chips the glanceable Tile shows (subset of [TILE_CHIP_ACTIONS]). */
    val tileActions: List<String> = listOf("lock", "climate"),
    /** Per pool-slot pinned car VIN; null = unconfigured (follows the selected car).
     *  Sized [WearTilePool.SIZE]. */
    val tileCarVins: List<String?> = List(WearTilePool.SIZE) { null },
    /** Update-check debounce/snooze/enable state - see WearViewModel's check. */
    val updateChecksEnabled: Boolean = true,
    val updateLastCheckedAt: Long = 0L,
    val updateSnoozeUntil: Long = 0L,
    /** Whether the watch's own PIN lock is armed. Only meaningful when [hasPin]
     *  is also true -- turning this on with no PIN set yet is a no-op gate. */
    val pinLockEnabled: Boolean = false,
    /** "off"/"immediate"/"1min"/"5min"/"10min" -- see [PIN_LOCK_TIMINGS]. */
    val pinLockTiming: String = "immediate",
    /** Whether a PIN has actually been set. Deliberately just a boolean here --
     *  the salted hash itself is never exposed through this reactive flow, only
     *  through the dedicated suspend verify/set/clear functions below. */
    val hasPin: Boolean = false,
)

/** The actions a Tile chip can perform, in canonical order. */
val TILE_CHIP_ACTIONS = listOf("lock", "climate", "charge")

class WearLocalStore(private val context: Context) {

    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyTileActions = stringPreferencesKey("tile_actions")
    private fun keyTileCarVin(index: Int) = stringPreferencesKey("tile_car_vin_$index")
    private val keyUnitSystem = stringPreferencesKey("unit_system")
    private val keyUpdateChecksEnabled = booleanPreferencesKey("update_checks_enabled")
    private val keyUpdateLastCheckedAt = longPreferencesKey("update_last_checked_at")
    private val keyUpdateSnoozeUntil = longPreferencesKey("update_snooze_until")
    private val keyPinLockEnabled = booleanPreferencesKey("pin_lock_enabled")
    private val keyPinLockTiming = stringPreferencesKey("pin_lock_timing")
    private val keyPinSalt = stringPreferencesKey("pin_salt")
    private val keyPinHash = stringPreferencesKey("pin_hash")

    // Pre-pool single-tile setting; migrated into slot 0 the first time that slot
    // is touched, and read as a fallback for slot 0 until then.
    private val keyTileCarVinLegacy = stringPreferencesKey("tile_car_vin")

    val flow: Flow<WearLocalSettings> = context.wearLocalStore.data.map { prefs ->
        val fontScale = (prefs[keyFontScale] ?: 1f).coerceIn(0.8f, 1.4f)
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
            tileActions = actions,
            tileCarVins = tileCarVins,
            updateChecksEnabled = prefs[keyUpdateChecksEnabled] ?: true,
            updateLastCheckedAt = prefs[keyUpdateLastCheckedAt] ?: 0L,
            updateSnoozeUntil = prefs[keyUpdateSnoozeUntil] ?: 0L,
            pinLockEnabled = prefs[keyPinLockEnabled] ?: false,
            pinLockTiming = prefs[keyPinLockTiming]?.takeIf { it in PIN_LOCK_TIMINGS } ?: "immediate",
            hasPin = prefs[keyPinHash] != null,
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

    // The last tile chip clickable id that was actually executed, PER POOL
    // SLOT (BlooTile1..4 each have their own independently-persisted Wear
    // Tile State/lastClickableId) -- a single shared key meant a background
    // render of one pinned tile could be mistaken for a fresh tap just
    // because a DIFFERENT tile was tapped more recently, re-firing a stale
    // command. Tile State (including lastClickableId) is persisted by the
    // system and re-delivered on every subsequent onTileRequest - including
    // freshness/push refreshes - so BlooTileService must dedupe or a single
    // tap's command re-fires on every later background render. Ids carry a
    // per-render nonce, so equality here means "this exact tap was already
    // handled".
    private fun keyTileLastClick(poolIndex: Int) = stringPreferencesKey("tile_last_click_$poolIndex")

    suspend fun tileLastClick(poolIndex: Int): String? =
        context.wearLocalStore.data.first()[keyTileLastClick(poolIndex)]

    suspend fun setTileLastClick(poolIndex: Int, id: String) {
        context.wearLocalStore.edit { it[keyTileLastClick(poolIndex)] = id }
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

    // --- PIN lock -----------------------------------------------------------
    // The PIN is never stored in plaintext and never leaves the watch: only a
    // salted SHA-256 hash lives in DataStore, and only [verifyPin] ever reads
    // it back (to compare, not to reveal). WearComms/WearLocalPayload mirror
    // just the enabled flag + timing to the phone for its settings backup --
    // see that payload's doc comment for why the hash itself is excluded.

    suspend fun setPinLockEnabled(enabled: Boolean) {
        context.wearLocalStore.edit { it[keyPinLockEnabled] = enabled }
    }

    suspend fun setPinLockTiming(value: String) {
        context.wearLocalStore.edit { it[keyPinLockTiming] = value.takeIf { v -> v in PIN_LOCK_TIMINGS } ?: "immediate" }
    }

    /** Salt+hash [rawPin] and store it, arming the lock. Caller validates format first. */
    suspend fun setPin(rawPin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        context.wearLocalStore.edit {
            it[keyPinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[keyPinHash] = hashPin(rawPin, salt)
            it[keyPinLockEnabled] = true
        }
    }

    /** Remove the PIN entirely and disarm the lock. */
    suspend fun clearPin() {
        context.wearLocalStore.edit {
            it.remove(keyPinSalt)
            it.remove(keyPinHash)
            it[keyPinLockEnabled] = false
        }
    }

    suspend fun verifyPin(rawPin: String): Boolean {
        val prefs = context.wearLocalStore.data.first()
        val saltB64 = prefs[keyPinSalt] ?: return false
        val expected = prefs[keyPinHash] ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return hashPin(rawPin, salt) == expected
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(salt)
        }.digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
