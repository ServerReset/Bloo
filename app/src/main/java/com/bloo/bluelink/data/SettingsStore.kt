package com.bloo.bluelink.data

import androidx.compose.runtime.Immutable
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.ui.ColorPalette
import com.bloo.bluelink.ui.CustomPaletteData
import com.bloo.bluelink.ui.FontChoice
import com.bloo.bluelink.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import androidx.core.graphics.scale

/**
 * App appearance preferences, kept separate from the session so sign-out keeps them. A thin typed
 * wrapper over one Jetpack DataStore<Preferences> ([Context.settingsDataStore]), a flat key-value
 * bag.
 */
class SettingsStore(internal val context: Context) {

    /** All strongly-typed, non-interpolated preference keys used directly by name below. */
    internal object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("font_choice")
        val DYNAMIC = stringPreferencesKey("dynamic_color")
        val PALETTE = stringPreferencesKey("color_palette")
        val CUSTOM_PALETTES = stringPreferencesKey("custom_palettes")
        val ACTIVE_CUSTOM_PALETTE_ID = stringPreferencesKey("active_custom_palette_id")
        val WEATHER_LAT = stringPreferencesKey("weather_lat")
        val WEATHER_LON = stringPreferencesKey("weather_lon")
        val WEATHER_LABEL = stringPreferencesKey("weather_label")
        val WEATHER_FOLLOWS_DEVICE = stringPreferencesKey("weather_follows_device")
        val BIOMETRIC = stringPreferencesKey("biometric_lock")
        val LOCK_TIMING = stringPreferencesKey("lock_timing")
        val FLIPPED = stringPreferencesKey("columns_flipped")
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val VIBRANCY = stringPreferencesKey("vibrancy")
        val GLASS_CLARITY = stringPreferencesKey("glass_clarity")
        val ULTRA_GLASS = stringPreferencesKey("ultra_glass")
        val HAPTICS = stringPreferencesKey("haptics_enabled")
        val PEBBLE_OUTLINE = stringPreferencesKey("pebble_outline")
        val SEAMLESS_INSTALL_SHIZUKU = stringPreferencesKey("seamless_install_shizuku")
        // Fractions (0f..1f) of the search bubble's own drag range, not raw dp -- the display
        // doesn't change size between sessions, but a fraction still degrades gracefully if it ever
        // did, where a raw dp coordinate could clamp to a corner it wasn't actually dropped near.
        val SEARCH_BUBBLE_X = stringPreferencesKey("search_bubble_x")
        val SEARCH_BUBBLE_Y = stringPreferencesKey("search_bubble_y")
        val AURORA = stringPreferencesKey("aurora_background")
        val AURORA_MOTION = stringPreferencesKey("aurora_motion")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val LAST_VIN = stringPreferencesKey("last_vehicle_vin")
        val ORDER = stringPreferencesKey("vehicle_order")
        val SETTINGS_MODE = stringPreferencesKey("settings_mode")
        /** Per-VIN default climate preset ID for the one-tap Start button in advanced mode. */
        const val DEFAULT_CLIMATE_PRESET_PREFIX = "default_climate_preset_"
    }

    @Immutable
    data class Appearance(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val fontChoice: FontChoice = FontChoice.SYSTEM,
        val dynamicColor: Boolean = true,
        /**
         * Which built-in palette to use when dynamic colour is off and no custom palette is active.
         */
        val colorPalette: ColorPalette = ColorPalette.BLUE,
        /** User-saved custom colour palettes. */
        val customPalettes: List<CustomPaletteData> = emptyList(),
        /** ID of the active custom palette, or null to use a built-in palette. */
        val activeCustomPaletteId: String? = null,
        /** User-set weather location (latitude/longitude/place label), or null if unset. */
        val weatherLat: Double? = null,
        val weatherLon: Double? = null,
        val weatherLabel: String? = null,
        /**
         * False whenever a place is set explicitly, or the location is cleared -- see
         * [setWeatherLocation].
         */
        val weatherFollowsDevice: Boolean = false,
        /** True for °F, false for °C. Derived from [unitSystem], unless [tempUnit] overrides it. */
        val useFahrenheit: Boolean = true,
        /**
         * True for km (and km/h), false for miles. Derived from [unitSystem], unless [distanceUnit]
         * overrides it.
         */
        val metricDistance: Boolean = false,
        /** "auto" (follow [unitSystem]), "f" or "c". */
        val tempUnit: String = "auto",
        /** "auto" (follow [unitSystem]), "mi" or "km". */
        val distanceUnit: String = "auto",
        val biometricLock: Boolean = false,
        /** When the biometric lock re-engages after leaving the foreground. */
        val lockTiming: LockTiming = LockTiming.IMMEDIATE,
        /** In the wide expanded view, put pebbles on the left, controls right. */
        val columnsFlipped: Boolean = false,
        /** Text/UI scale multiplier (0.85–1.3). */
        val uiScale: Float = 1f,
        /** Colour vibrancy multiplier (0.5–1.6, 1 = default). */
        val vibrancy: Float = 1f,
        /**
         * How transparent the backing of floating glass is: 0 = solid, 1 = none (one of
         * [com.bloo.bluelink.ui.GlassStops]). Defaults to "Clear" (0.90) -- the second clearest
         * stop, so the glass reads as glass out of the box.
         */
        val glassClarity: Float = 0.95f,
        /**
         * Ultra glass: glass on EVERY surface, not just the floating chrome -- cards and panels
         * included. See GlassChrome.glassCardFill.
         */
        val ultraGlass: Boolean = false,
        /** Show an aurora gradient as the app background instead of solid surface. */
        val auroraBackground: Boolean = false,
        /** Aurora motion mode: "off", "static", "motion". */
        val auroraMotion: String = "static",
        /** Unit system: "imperial" (miles, mph, F) or "metric" (km, km/h, C). */
        val unitSystem: String = "imperial",
        /** Haptic feedback across the UI. */
        val hapticsEnabled: Boolean = true,
        /** Hairline rim on pebbles/hero card. */
        val pebbleOutline: Boolean = false,
        /**
         * Off by default; device-local capability (Shizuku may not be present on other devices), so
         * it never roams via Drive sync (see SyncMerge.DEVICE_LOCAL_KEYS).
         */
        val seamlessInstallShizuku: Boolean = false,
        /**
         * Opt-in "liquid glass" appearance. Off by default = current look; when on, floating chrome
         * and cards use real backdrop refraction (API 31+) or an enhanced-frosted fallback below
         * that.
         */
    )

    // A reactive view of every appearance preference: re-emits a freshly decoded Appearance
    // snapshot on any DataStore change (here, or via Drive sync).
    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            // "AMOLED"/"SYSTEM_AMOLED" are legacy values remapped to the nearest modern mode, so an
            // install that had AMOLED selected keeps a dark theme instead of reverting to System.
            themeMode = when (val raw = prefs[Keys.THEME]) {
                "AMOLED" -> ThemeMode.DARK
                "SYSTEM_AMOLED" -> ThemeMode.SYSTEM
                null -> ThemeMode.SYSTEM
                else -> runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
            },
            fontChoice = prefs[Keys.FONT]?.let { runCatching { FontChoice.valueOf(it) }.getOrNull() }
                ?: FontChoice.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC]?.toBooleanStrictOrNull() ?: true,
            colorPalette = prefs[Keys.PALETTE]?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() }
                ?: ColorPalette.BLUE,
            customPalettes = prefs[Keys.CUSTOM_PALETTES]?.let { json ->
                decodeJsonOr(paletteJson, paletteListSerializer, json, emptyList())
            } ?: emptyList(),
            activeCustomPaletteId = prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID],
            weatherLat = prefs[Keys.WEATHER_LAT]?.toDoubleOrNull(),
            weatherLon = prefs[Keys.WEATHER_LON]?.toDoubleOrNull(),
            weatherLabel = prefs[Keys.WEATHER_LABEL],
            weatherFollowsDevice = prefs[Keys.WEATHER_FOLLOWS_DEVICE]?.toBoolean() ?: false,
            biometricLock = prefs[Keys.BIOMETRIC]?.toBooleanStrictOrNull() ?: false,
            lockTiming = prefs[Keys.LOCK_TIMING]?.let { runCatching { LockTiming.valueOf(it) }.getOrNull() }
                ?: LockTiming.IMMEDIATE,
            columnsFlipped = prefs[Keys.FLIPPED]?.toBooleanStrictOrNull() ?: false,
            // Clamp on read: a corrupt/hand-edited/foreign backup with e.g. ui_scale="10" would
            // otherwise scale the whole UI 10x and lock the user out of Settings, so a bad stored
            // value can never take effect.
            uiScale = (prefs[Keys.UI_SCALE]?.toFloatOrNull() ?: 1f).coerceIn(0.85f, 1.3f),
            vibrancy = (prefs[Keys.VIBRANCY]?.toFloatOrNull() ?: 1f).coerceIn(0.5f, 1.6f),
            glassClarity = (prefs[Keys.GLASS_CLARITY]?.toFloatOrNull() ?: 0.95f).coerceIn(0f, 1f),
            ultraGlass = prefs[Keys.ULTRA_GLASS]?.toBooleanStrictOrNull() ?: false,
            hapticsEnabled = prefs[Keys.HAPTICS]?.toBooleanStrictOrNull() ?: true,
            auroraBackground = prefs[Keys.AURORA]?.toBooleanStrictOrNull() ?: false,
            auroraMotion = prefs[Keys.AURORA_MOTION] ?: "static",
            unitSystem = prefs[Keys.UNIT_SYSTEM] ?: "imperial",
            // Shared rule -- see FormatUtils.useFahrenheit for why this stopped being written out
            // separately per surface.
            useFahrenheit = resolveFahrenheit(prefs[Keys.UNIT_SYSTEM], prefs[Keys.TEMP_UNIT].takeIf { prefs[Keys.SETTINGS_MODE] == "advanced" }),
            metricDistance = resolveMetricDistance(prefs[Keys.UNIT_SYSTEM], prefs[Keys.DISTANCE_UNIT].takeIf { prefs[Keys.SETTINGS_MODE] == "advanced" }),
            tempUnit = prefs[Keys.TEMP_UNIT] ?: "auto",
            distanceUnit = prefs[Keys.DISTANCE_UNIT] ?: "auto",
            pebbleOutline = prefs[Keys.PEBBLE_OUTLINE]?.toBooleanStrictOrNull() ?: false,
            seamlessInstallShizuku = prefs[Keys.SEAMLESS_INSTALL_SHIZUKU]?.toBooleanStrictOrNull() ?: false,
        )
    }
        // Off the main thread. This is a ~40-field decode including two JSON parses (custom
        // palettes and per-car palette ids), and DataStore only guarantees the FILE read is off
        // main -- a map{} transform runs in the collector's context.
        .flowOn(Dispatchers.Default)

    // Simple appearance setters: each writes one Keys.* string through editTracked() (persist +
    // mark dirty for Drive sync).

    // --- Notifications --------------------------------------------------

    /**
     * App-wide (not per-car) notification toggles and thresholds. [service] gates the persistent
     * foreground-service notification; [doorOpen], [running] and [unlocked] gate the "door left
     * open" / "engine left running" / "left unlocked" alerts, each firing once the condition has
     * held continuously for its paired *Minutes threshold (see
     * [doorOpenSince]/[engineOnSince]/[unlockedSince] below, which track how long the condition has
     * been true per car).
     */
    @Immutable
    data class NotificationPrefs(
        val service: Boolean = true,
        val doorOpen: Boolean = true,
        val doorOpenMinutes: Int = 5,
        val running: Boolean = true,
        val runningMinutes: Int = 10,
        val unlocked: Boolean = true,
        val unlockedMinutes: Int = 10,
        /**
         * The Live Update charging notification -- see [com.bloo.bluelink.data.LiveCharge]'s class
         * doc for what that means precisely and how to verify it's actually working.
         */
        val charging: Boolean = true,
        /**
         * The "Locked" / "Would have locked" notification AutoLock posts when it acts. The lock
         * FAILED notification is deliberately not governed by this: it is always worth seeing.
         */
        val autoLockAlerts: Boolean = true,
        /** Notification when the car's engine is started. */
        val carStarted: Boolean = true,
        /** Notification when charging is complete. */
        val chargeComplete: Boolean = true,
        /** The watch's own "battery is low" notification (the phone has no equivalent). */
        val watchLowBattery: Boolean = true,
    )

    /**
     * Reactive equivalent of [notificationPrefs] for UI that needs to update live when the user
     * changes a toggle in Settings while the screen is open.
     */
    val notifications: Flow<NotificationPrefs> = context.settingsDataStore.data.map { p ->
        decodeNotificationPrefs(p)
    }

    // --- Per-car identity + service (the API has no service-history fields) ---

    // --- Per-car seat capability (the API has no reliable flags) ---------

    // --- First-run onboarding -------------------------------------------

    // --- AutoLock (per car) ------------------------------------------------
    //
    // Ported from the i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock): locks a
    // car automatically when the phone disconnects from its paired Bluetooth device (the
    // head unit), confirmed by Activity Recognition (driving -> walking), after a
    // cancellable grace period, and only when the car's own status says it's safe to
    // (unlocked, engine off, doors/windows closed). See app/.../autolock/ for the state
    // machine, policy and detection plumbing.

    // --- Per-car section order -------------------------------------------

    // --- On-device AI ----------------------------------------------------

    // --- App-icon shortcut selection -------------------------------------

    // --- Device sync identity + registry (all device-local: see SyncMerge.DEVICE_LOCAL_KEYS) ---
    //
    // These describe THIS install's participation in the shared Drive file. They
    // never travel in the portable backup (they're in DEVICE_LOCAL_KEYS, so
    // editTracked never marks them dirty and buildExport never emits them). They're
    // written with the RAW DataStore (context.settingsDataStore.edit), NOT
    // editTracked, precisely so touching them can't pollute the dirty set or trip a
    // content-hash change.

    internal val devicesJson = Json { ignoreUnknownKeys = true }
    internal val deviceListSerializer = ListSerializer(SyncMerge.SyncDevice.serializer())

    /** Outcome of one [performMainToMainSync] pass. */
    data class MainToMainSyncOutcome(
        val ran: Boolean,
        val imported: Boolean,
        val uploaded: Boolean,
        /** The timestamp this pass recorded as the last-sync time (unchanged if !ran). */
        val syncedAtMs: Long,
        /**
         * A user-facing reason the pass didn't fully succeed, or null if it did (or wasn't
         * configured — that's not a failure).
         */
        val error: String? = null,
        /** The merged device registry after this pass (for the ViewModel/Settings). */
        val devices: List<SyncMerge.SyncDevice> = emptyList(),
        /** The primary device id recorded in the file, or null if none. */
        val primaryDeviceId: String? = null,
        /**
         * This device's own sync id (so the UI can mark "this device" / hide "make primary" on self
         * without a second read).
         */
        val selfDeviceId: String? = null,
    )

    /**
     * Result of [testSyncRoundTrip]: [ok] plus a human-readable [message] describing exactly which
     * step passed or failed, for a Settings "Test sync" diagnostic the user can run on a real
     * device.
     */
    data class SyncTestResult(val ok: Boolean, val message: String)

    // --- Dual-column "hot spot" (pebbles pinned under the car-info column) -----

    // --- Per-car powertrain override -------------------------------------

    // --- Per-car head-unit generation override ----------------------------
    //
    // Same shape as the powertrain override right above -- null means "not
    // confirmed by the user yet", callers fall back to the API-derived guess
    // ([com.bloo.bluelink.data.isGen5W]) when this is null. See VehiclePlatform's
    // own doc for which vehicles this has anything real to confirm.

    // --- Per-car climate settings + presets ------------------------------

    internal val climateJson = Json { ignoreUnknownKeys = true }
    internal val presetListSerializer = ListSerializer(ClimatePreset.serializer())

    // --- Custom colour palettes ------------------------------------------

    internal val paletteJson = Json { ignoreUnknownKeys = true }
    internal val paletteListSerializer = ListSerializer(CustomPaletteData.serializer())

    // --- Full settings backup --------------------------------------------

    internal val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** The settings-backup format version. */
    internal val BACKUP_VERSION = SyncMerge.BACKUP_VERSION

    /**
     * A tablet that's Wi-Fi-only and a phone with unlimited data may reasonably want different
     * choices here, same as the Drive URI itself.
     */
    internal val DEVICE_LOCAL_KEYS = SyncMerge.DEVICE_LOCAL_KEYS

    /**
     * Wraps a settings mutation to record which preference keys it actually changed into the
     * "dirty" set — the keys this device has touched locally since its own last successful Drive
     * sync. [performMainToMainSync] protects these from being overwritten by an incoming remote
     * file, so a local edit that hasn't been uploaded yet is never silently lost (field-level merge
     * instead of one whole-file last-write-wins).
     */
    internal suspend fun editTracked(mutate: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { prefs ->
            val before = HashMap(prefs.asMap())
            mutate(prefs)
            val after = prefs.asMap()
            val touched = mutableSetOf<String>()
            after.forEach { (k, v) -> if (before[k] != v) touched += k.name }
            // Value-identical.
            val afterNames = HashSet<String>(after.size)
            after.keys.forEach { afterNames += it.name }
            before.keys.forEach { k -> if (k.name !in afterNames) touched += k.name }
            // Strip BOTH the exact device-local keys AND the per-VIN device-local prefixes (see
            // SyncMerge.DEVICE_LOCAL_PREFIXES for the list -- naming them here just meant this
            // comment fell behind it), matching what the export/hash already exclude via
            // isDeviceLocal — otherwise those transient runtime stamps land in the dirty set and
            // every alert/tile tick fires a redundant full Drive round-trip (the hash is unchanged,
            // so no data corrupts, but it's needless background I/O the prefix design meant to
            // stop).
            touched.removeAll { com.bloo.bluelink.data.SyncMerge.isDeviceLocal(it) }
            if (touched.isNotEmpty()) {
                val dirtyKey = stringPreferencesKey("sync_dirty_keys")
                val existing = prefs.dirtyKeySet()
                prefs[dirtyKey] = (existing + touched).joinToString(",")
            }
        }
    }

    /**
     * Decode the CSV-encoded "sync_dirty_keys" pref into a Set, dropping blanks (an unset/empty
     * value → empty set).
     */
    internal fun Preferences.dirtyKeySet(): Set<String> =
        this[stringPreferencesKey("sync_dirty_keys")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    internal suspend fun dirtyKeys(): Set<String> =
        context.settingsDataStore.data.first().dirtyKeySet()

    /**
     * Reactive view of the dirty set — emits whenever a tracked setting changes (every
     * [editTracked] that touches a portable key appends to it).
     */
    val dirtyKeysFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { prefs ->
            val keys = prefs.dirtyKeySet()
            // One name -> value map, not a scan per dirty key. Tombstoned keys are absent from
            // prefs and biometric as null, so a delete and a later restore of the same key are
            // correctly distinct.
            val byName = prefs.asMap().entries.associate { it.key.name to it.value }
            // NUL separator below, and written as the ESCAPE rather than the character.
            keys to keys.sorted().joinToString("\u0000") { "$it=${byName[it]}" }.hashCode()
        }
        .distinctUntilChanged()
        .map { it.first }

    /**
     * Done inside a single edit{} so a concurrent [editTracked] can't race between our read and
     * write; if nothing dirty remains the key is removed entirely.
     */
    internal suspend fun clearDirtyKeys(keys: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            val dirtyKey = stringPreferencesKey("sync_dirty_keys")
            val remaining = prefs.dirtyKeySet() - keys
            if (remaining.isEmpty()) prefs.remove(dirtyKey) else prefs[dirtyKey] = remaining.joinToString(",")
        }
    }

    /**
     * How recently a file in `cars/` must have been written to be spared by [pruneOrphanPhotos].
     */
    internal val MIN_ORPHAN_AGE_MS = 10 * 60 * 1000L

    // --- Chargers ----------------------------------------------------------

    // --- Weather ---------------------------------------------------------

}
