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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

// ─────────────────────────────────────────────────────────────────────────────
//  Watch-local persistence
//
//  Everything in this file is watch-ONLY state: preferences and secrets that
//  live on the wrist and are never authoritative on the phone. (The phone-synced
//  mirror — settings/presets/climate/extras — lives in WearSyncedStore.) The one
//  bridge back to the phone is WearLocalPayload, which mirrors just the display
//  scale, unit system and PIN-lock *enable flag + timing* for the phone's
//  settings backup — never the PIN hash itself (see the PIN section below).
//
//  DataStore requires exactly ONE delegate instance per file for the whole
//  process, so the delegate lives at top level. A corruption handler resets a
//  file damaged by an interrupted write / power loss to empty prefs instead of
//  rethrowing an uncaught exception out of every read — this store backs live
//  tiles and complications, which must never throw.
// ─────────────────────────────────────────────────────────────────────────────

private val Context.wearLocalStore by preferencesDataStore(
    name = "bloo_wear_local",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The full catalogue of watch-only Tile (glance card) identifiers — both the
 * flat set of individual keys (used for per-slot ordering / card dispatch) and,
 * via [DEFAULT_ORDER], their default display order before a user reorders them.
 *
 * NOTE: these are in-memory card identifiers only. Nothing here is persisted as
 * a stored key, so cards can be added/removed freely without a migration; the
 * only persisted ordering is the *pebble* order (see [WearPebbles]).
 */
object WearTiles {
    const val SUMMARY = "summary"
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
        SUMMARY, CHARGE, LIMITS, AI, CLIMATE, SMART_CLIMATE, COMFORT, PRESETS,
        LOCATION, WEATHER, INFO, DIAGNOSTICS, ASSIST, MORE,
    )
}

/**
 * The bridge between the phone's detail "pebbles" and the watch's tiles. One
 * phone pebble can expand into several watch tiles (e.g. the Climate pebble
 * becomes Climate, Smart Climate, Comfort and Presets), and the watch reorders
 * by whole pebble so the two stay in lock-step. Pebble keys match the phone's
 * `DEFAULT_SECTIONS`, and the pebble order is the only tile-layout state that is
 * ever persisted (synced from the phone) — so it is the migration-sensitive one.
 */
object WearPebbles {
    /** Pebble order on the phone (mirrors the app's DEFAULT_SECTIONS). */
    val DEFAULT_ORDER = listOf(
        "summary", "controls", "charge", "climate", "ai",
        "info", "location", "weather", "trips", "diagnostics",
    )

    /**
     * Each pebble → the watch tiles it owns, in display order.
     *
     * Two pebbles own no tile of their own and expand to nothing:
     *  - "controls": its lock/unlock button was one swipe from the identical
     *    control already on SummaryCard's hero row, so the standalone Lock tile
     *    was dropped rather than duplicated. It is left mapped to an empty list
     *    (not deleted) because pebble order is all that's persisted — no stored
     *    tile key exists anywhere to migrate or orphan.
     *  - "trips": lives inside the More tile, so it too owns no tile here.
     */
    private val TO_TILES = mapOf(
        "summary" to listOf(WearTiles.SUMMARY),
        "controls" to emptyList(),
        "charge" to listOf(WearTiles.CHARGE, WearTiles.LIMITS),
        "ai" to listOf(WearTiles.AI),
        "climate" to listOf(WearTiles.CLIMATE, WearTiles.SMART_CLIMATE, WearTiles.COMFORT, WearTiles.PRESETS),
        "info" to listOf(WearTiles.INFO),
        "location" to listOf(WearTiles.LOCATION),
        "weather" to listOf(WearTiles.WEATHER),
        "trips" to emptyList(),
        "diagnostics" to listOf(WearTiles.DIAGNOSTICS),
    )

    /** Watch-only tiles with no phone pebble — always appended, in this order. */
    private val TAIL = listOf(WearTiles.ASSIST, WearTiles.MORE)

    /** Human-readable pebble labels for the reorder UI. */
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

    /**
     * Clean an incoming order: keep only known keys, force summary first, then
     * append any missing defaults so a newly-introduced pebble never silently
     * disappears from a device that stored an older order.
     */
    fun normalize(order: List<String>): List<String> {
        val known = order.filter { it in DEFAULT_ORDER }.distinct()
        val merged = listOf("summary") +
            known.filter { it != "summary" } +
            DEFAULT_ORDER.filter { it !in known && it != "summary" }
        return merged.distinct()
    }

    /**
     * Expand a pebble order into the flat watch-tile order, dropping any pebble
     * in [hiddenPebbles] (synced from the phone's per-car hidden-section
     * setting).
     *
     * Filtering happens AFTER [normalize] so a pebble the user hid is excluded
     * here without normalize() mistaking it for a brand-new unknown pebble that
     * must be appended back in. The [TAIL] (watch-only tiles) is always present.
     */
    fun tilesFor(pebbleOrder: List<String>, hiddenPebbles: Set<String> = emptySet()): List<String> {
        val tiles = ArrayList<String>()
        for (pebble in normalize(pebbleOrder)) {
            if (pebble in hiddenPebbles) continue
            tiles += TO_TILES[pebble].orEmpty()
        }
        tiles += TAIL
        return tiles.distinct()
    }
}

/**
 * A pool of concrete Tile services the user can add to their watch face — one
 * per car, mirroring the phone's BlooTile1..12 Quick Settings pool.
 */
object WearTilePool {
    const val SIZE = 4
}

/** The actions a Tile chip can perform, in canonical order. */
val TILE_CHIP_ACTIONS = listOf("lock", "climate", "charge")

/** Valid [WearLocalSettings.pinLockTiming] values — same vocabulary as the phone's LockTiming. */
val PIN_LOCK_TIMINGS = setOf("off", "immediate", "1min", "5min", "10min")

/**
 * The reactive snapshot of every watch-local setting. Every field is fully
 * defaulted/coerced/validated by [WearLocalStore.flow] so a collector never
 * sees a raw or partial preferences state.
 */
data class WearLocalSettings(
    val fontScale: Float = 1f,
    val unitSystem: String = "imperial",
    /** Which action chips the glanceable Tile shows (subset of [TILE_CHIP_ACTIONS]). */
    val tileActions: List<String> = listOf("lock", "climate"),
    /**
     * Per pool-slot pinned car VIN; null = unconfigured (follows the selected
     * car). Sized [WearTilePool.SIZE].
     */
    val tileCarVins: List<String?> = List(WearTilePool.SIZE) { null },
    /** Update-check debounce/snooze state — see WearViewModel's check. */
    val updateLastCheckedAt: Long = 0L,
    val updateSnoozeUntil: Long = 0L,
    /**
     * Whether the watch's own PIN lock is armed. Only meaningful when [hasPin]
     * is also true — turning this on with no PIN set yet is a no-op gate.
     */
    val pinLockEnabled: Boolean = false,
    /** "off"/"immediate"/"1min"/"5min"/"10min" — see [PIN_LOCK_TIMINGS]. */
    val pinLockTiming: String = "immediate",
    /**
     * Whether a PIN has actually been set. Deliberately just a boolean here —
     * the salted hash itself is never exposed through this reactive flow, only
     * through the dedicated suspend verify/set/clear functions below.
     */
    val hasPin: Boolean = false,
)

/**
 * DataStore-backed store for all watch-local preferences and the on-device PIN.
 *
 * Reads flow reactively through [flow]; writes are individual suspend functions.
 * Constructing a new instance per call site is cheap and safe — the underlying
 * DataStore is the single process-wide top-level delegate above.
 */
class WearLocalStore(private val context: Context) {

    // ── Preference keys ─────────────────────────────────────────────────────
    // Every string below is a PERSISTED key. Renaming any one silently orphans
    // a user's stored value, so they are frozen for data-migration safety.
    private val keyFontScale = floatPreferencesKey("font_scale")
    private val keyTileActions = stringPreferencesKey("tile_actions")
    private fun keyTileCarVin(index: Int) = stringPreferencesKey("tile_car_vin_$index")
    private val keyUnitSystem = stringPreferencesKey("unit_system")
    private val keyUpdateLastCheckedAt = longPreferencesKey("update_last_checked_at")
    private val keyUpdateSnoozeUntil = longPreferencesKey("update_snooze_until")
    private val keyPinLockEnabled = booleanPreferencesKey("pin_lock_enabled")
    private val keyPinLockTiming = stringPreferencesKey("pin_lock_timing")
    private val keyPinSalt = stringPreferencesKey("pin_salt")
    private val keyPinHash = stringPreferencesKey("pin_hash")

    // Pre-pool single-tile setting; migrated into slot 0 the first time that
    // slot is touched, and read as a fallback for slot 0 until then.
    private val keyTileCarVinLegacy = stringPreferencesKey("tile_car_vin")

    // The last tile-chip clickable id actually executed, PER POOL SLOT
    // (BlooTile1..4 each have their own independently-persisted Wear Tile
    // State/lastClickableId). A single shared key meant a background render of
    // one pinned tile could be mistaken for a fresh tap just because a DIFFERENT
    // tile was tapped more recently, re-firing a stale command. Tile State
    // (including lastClickableId) is persisted by the system and re-delivered on
    // every subsequent onTileRequest — including freshness/push refreshes — so
    // BlooTileService must dedupe or a single tap's command re-fires on every
    // later background render. Ids carry a per-render nonce, so equality here
    // means "this exact tap was already handled".
    private fun keyTileLastClick(poolIndex: Int) = stringPreferencesKey("tile_last_click_$poolIndex")

    // ── Reactive read ────────────────────────────────────────────────────────

    /**
     * Reactive view of every watch-local setting, derived straight from the
     * DataStore Preferences [Flow]. Any write anywhere in this class triggers
     * DataStore to emit a new snapshot, which this `map` turns into a
     * fully-populated [WearLocalSettings] — applying defaults, coercion and
     * validation for every field so collectors (e.g. WearViewModel) stay in
     * sync automatically without polling and never see a raw/partial state.
     */
    val flow: Flow<WearLocalSettings> = context.wearLocalStore.data.map { prefs ->
        // Clamp to the same [0.8, 1.4] range the writer clamps — defence in
        // depth against an out-of-range value that somehow reached DataStore.
        val fontScale = (prefs[keyFontScale] ?: 1f).coerceIn(0.8f, 1.4f)

        val tileActions = prefs[keyTileActions]
            ?.split(",")
            ?.filter { it in TILE_CHIP_ACTIONS }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("lock", "climate")

        // Per-slot VIN list: for slot 0, if the new per-slot key was never
        // written, fall back to the pre-pool single-VIN key so an install that
        // pinned a car before the pool existed keeps showing that car in slot 0
        // until it's explicitly changed (setTileCarVin clears the legacy key
        // once slot 0 is touched).
        val tileCarVins = (0 until WearTilePool.SIZE).map { i ->
            val v = prefs[keyTileCarVin(i)]?.takeIf { it.isNotBlank() }
            if (v == null && i == 0) prefs[keyTileCarVinLegacy]?.takeIf { it.isNotBlank() } else v
        }

        WearLocalSettings(
            fontScale = fontScale,
            unitSystem = prefs[keyUnitSystem] ?: "imperial",
            tileActions = tileActions,
            tileCarVins = tileCarVins,
            updateLastCheckedAt = prefs[keyUpdateLastCheckedAt] ?: 0L,
            updateSnoozeUntil = prefs[keyUpdateSnoozeUntil] ?: 0L,
            pinLockEnabled = prefs[keyPinLockEnabled] ?: false,
            pinLockTiming = prefs[keyPinLockTiming]?.takeIf { it in PIN_LOCK_TIMINGS } ?: "immediate",
            // hasPin is derived purely from presence of the stored hash — the
            // hash value itself is never surfaced through this flow.
            hasPin = prefs[keyPinHash] != null,
        )
    }
        // Off the UI thread. This decode splits a stored CSV, filters it, and loops the tile
        // pool per slot -- and it is read with .first() from a dozen viewModelScope.launch
        // blocks, all of which resume on Main.immediate. Cheap per call, on a watch, a dozen
        // times, on the thread drawing the frame.
        .flowOn(Dispatchers.Default)

    // ── Appearance ────────────────────────────────────────────────────────────

    /** Persist the display scale, clamped to the same [0.8, 1.4] range [flow] re-clamps on read. */
    suspend fun setFontScale(f: Float) {
        context.wearLocalStore.edit { it[keyFontScale] = f.coerceIn(0.8f, 1.4f) }
    }

    /**
     * Persist "imperial" or "metric" verbatim — no validation here; [flow]
     * falls back to "imperial" if this is ever anything else.
     */
    suspend fun setUnitSystem(value: String) {
        context.wearLocalStore.edit { it[keyUnitSystem] = value }
    }

    // ── Update-check bookkeeping ───────────────────────────────────────────────

    /** Record when the last update check ran, used by the update-check debounce elsewhere. */
    suspend fun setUpdateLastCheckedAt(millis: Long) {
        context.wearLocalStore.edit { it[keyUpdateLastCheckedAt] = millis }
    }

    /** Record until when update-check reminders should be suppressed after the user snoozes one. */
    suspend fun setUpdateSnoozeUntil(millis: Long) {
        context.wearLocalStore.edit { it[keyUpdateSnoozeUntil] = millis }
    }

    // ── Tiles ──────────────────────────────────────────────────────────────────

    /**
     * Read back the last-handled clickable id for one pool slot, so
     * BlooTileService can compare it against the id on an incoming
     * onTileRequest and decide whether that tap has already been executed.
     */
    suspend fun tileLastClick(poolIndex: Int): String? =
        context.wearLocalStore.data.first()[keyTileLastClick(poolIndex)]

    /** Record that clickable [id] has now been handled for pool slot [poolIndex]. */
    suspend fun setTileLastClick(poolIndex: Int, id: String) {
        context.wearLocalStore.edit { it[keyTileLastClick(poolIndex)] = id }
    }

    /**
     * Persist which action chips the glanceable Tile shows. Filters to known
     * [TILE_CHIP_ACTIONS] (dropping anything unrecognised), de-dupes, caps at
     * two (the Tile only has room for two chips), and never allows an empty
     * result — falling back to just "lock" rather than storing a Tile with no
     * actions at all. Stored as a single comma-joined string since DataStore
     * Preferences has no native list type.
     */
    suspend fun setTileActions(actions: List<String>) {
        val clean = actions.filter { it in TILE_CHIP_ACTIONS }.distinct().take(2).ifEmpty { listOf("lock") }
        context.wearLocalStore.edit { it[keyTileActions] = clean.joinToString(",") }
    }

    /** Pin pool slot [index] to a car. Pass null to unconfigure/clear that slot. */
    suspend fun setTileCarVin(index: Int, vin: String?) {
        context.wearLocalStore.edit { prefs ->
            if (vin.isNullOrBlank()) prefs.remove(keyTileCarVin(index)) else prefs[keyTileCarVin(index)] = vin
            // Retire the pre-pool legacy key the moment slot 0 is explicitly set.
            if (index == 0) prefs.remove(keyTileCarVinLegacy)
        }
    }

    // ── PIN lock ───────────────────────────────────────────────────────────────
    // The PIN is never stored in plaintext and never leaves the watch: only a
    // salted SHA-256 hash lives in DataStore, and only [verifyPin] ever reads it
    // back (to compare, not to reveal). WearComms/WearLocalPayload mirror just
    // the enabled flag + timing to the phone for its settings backup — never the
    // hash. There is deliberately no attempt-counting or lockout here (or
    // anywhere in this store); every verify is an independent, un-rate-limited
    // comparison, and the caller (WearViewModel) owns any UX around repeated
    // failures.

    /**
     * Arm/disarm the lock without touching the stored PIN itself — see
     * [WearLocalSettings.pinLockEnabled] for why this is gated by [hasPin]
     * rather than meaningful on its own.
     */
    suspend fun setPinLockEnabled(enabled: Boolean) {
        context.wearLocalStore.edit { it[keyPinLockEnabled] = enabled }
    }

    /**
     * Persist how long after the watch app is put away the lock should engage.
     * Falls back to "immediate" for any value not in [PIN_LOCK_TIMINGS] rather
     * than storing garbage.
     */
    suspend fun setPinLockTiming(value: String) {
        context.wearLocalStore.edit {
            it[keyPinLockTiming] = value.takeIf { v -> v in PIN_LOCK_TIMINGS } ?: "immediate"
        }
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

    /**
     * Check [rawPin] against the stored salted hash. Reads the salt and hash
     * fresh from DataStore each call (rather than caching them) so this always
     * checks against whatever is currently persisted. Returns false — rather
     * than throwing — if no PIN is set, the salt fails to decode, or the hash
     * doesn't match; the caller cannot distinguish "no PIN set" from "wrong PIN"
     * from this return value alone.
     */
    suspend fun verifyPin(rawPin: String): Boolean {
        val prefs = context.wearLocalStore.data.first()
        val saltB64 = prefs[keyPinSalt] ?: return false
        val expected = prefs[keyPinHash] ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return hashPin(rawPin, salt) == expected
    }

    /**
     * SHA-256 the salt concatenated with the UTF-8 PIN bytes (salt fed into the
     * digest first, then the PIN), producing a deterministic lowercase hex
     * string. The salt is per-installation-random ([setPin] generates a fresh
     * one each time via [SecureRandom]), so the same PIN produces a different
     * hash on every device/reset, defeating precomputed rainbow-table lookups
     * against the stored hash.
     */
    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .apply { update(salt) }
            .digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
