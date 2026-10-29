package com.bloo.bluelink.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.ui.ColorPalette
import com.bloo.bluelink.ui.CustomPaletteData
import com.bloo.bluelink.ui.FontChoice
import com.bloo.bluelink.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * App appearance preferences, kept separate from the session so sign-out keeps them. A thin typed
 * wrapper over one Jetpack DataStore<Preferences> ([Context.settingsDataStore]), a flat key-value
 * bag.
 */
class SettingsStore(internal val context: Context) {
    /**
     * Per-car keys are instead built ad hoc with string interpolation (see e.g. [seatConfig]) since
     * Preferences DataStore has no notion of a keyed sub-namespace - this object only holds the
     * ones that are the same for the whole app.
     */
    internal object Keys {
        val THEME = stringPreferencesKey(PrefKeys.THEME_MODE)
        val FONT = stringPreferencesKey(PrefKeys.FONT_CHOICE)
        val DYNAMIC = stringPreferencesKey(PrefKeys.DYNAMIC_COLOR)
        val PALETTE = stringPreferencesKey(PrefKeys.COLOR_PALETTE)
        val CUSTOM_PALETTES = stringPreferencesKey(PrefKeys.CUSTOM_PALETTES)
        val ACTIVE_CUSTOM_PALETTE_ID = stringPreferencesKey(PrefKeys.ACTIVE_CUSTOM_PALETTE_ID)
        val WEATHER_LAT = stringPreferencesKey(PrefKeys.WEATHER_LAT)
        val WEATHER_LON = stringPreferencesKey(PrefKeys.WEATHER_LON)
        val WEATHER_LABEL = stringPreferencesKey(PrefKeys.WEATHER_LABEL)
        val WEATHER_FOLLOWS_DEVICE = stringPreferencesKey(PrefKeys.WEATHER_FOLLOWS_DEVICE)
        val BIOMETRIC = stringPreferencesKey(PrefKeys.BIOMETRIC_LOCK)
        val LOCK_TIMING = stringPreferencesKey(PrefKeys.LOCK_TIMING)
        val FLIPPED = stringPreferencesKey(PrefKeys.COLUMNS_FLIPPED)
        val UI_SCALE = stringPreferencesKey(PrefKeys.UI_SCALE)
        val VIBRANCY = stringPreferencesKey(PrefKeys.VIBRANCY)
        val GLASS_CLARITY = stringPreferencesKey(PrefKeys.GLASS_CLARITY)
        val ULTRA_GLASS = stringPreferencesKey(PrefKeys.ULTRA_GLASS)
        val HAPTICS = stringPreferencesKey(PrefKeys.HAPTICS_ENABLED)
        val SEAMLESS_INSTALL_SHIZUKU = stringPreferencesKey(PrefKeys.SEAMLESS_INSTALL_SHIZUKU)
        val MOTION_BLUR = stringPreferencesKey(PrefKeys.MOTION_BLUR)
        // Fractions (0f..1f) of the search bubble's own drag range, not raw dp -- the display
        // doesn't change size between sessions, but a fraction still degrades gracefully if it ever
        // did, where a raw dp coordinate could clamp to a corner it wasn't actually dropped near.
        val SEARCH_BUBBLE_X = stringPreferencesKey(PrefKeys.SEARCH_BUBBLE_X)
        val SEARCH_BUBBLE_Y = stringPreferencesKey(PrefKeys.SEARCH_BUBBLE_Y)
        val AURORA = stringPreferencesKey(PrefKeys.AURORA_BACKGROUND)
        val UNIT_SYSTEM = stringPreferencesKey(PrefKeys.UNIT_SYSTEM)
        val TEMP_UNIT = stringPreferencesKey(PrefKeys.TEMP_UNIT)
        val DISTANCE_UNIT = stringPreferencesKey(PrefKeys.DISTANCE_UNIT)
        val LAST_VIN = stringPreferencesKey(PrefKeys.LAST_VEHICLE_VIN)
        val ORDER = stringPreferencesKey(PrefKeys.VEHICLE_ORDER)
        val SETTINGS_MODE = stringPreferencesKey(PrefKeys.SETTINGS_MODE)
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
        /** Text/UI scale multiplier (0.85 to 1.3). */
        val uiScale: Float = 1f,
        /** Colour vibrancy multiplier (0.5 to 1.6, 1 = default). */
        val vibrancy: Float = 1f,
        /**
         * How transparent the backing of floating glass is: 0 = solid, 1 = none (one of
         * [com.bloo.bluelink.ui.GlassStops]). Defaults to "Clear" (0.90) -- the second clearest
         * stop, so the glass reads as glass out of the box.
         */
        val glassClarity: Float = 0.8f,
        /**
         * Ultra glass: glass on EVERY surface, not just the floating chrome -- cards and panels
         * included. See GlassChrome.glassCardFill.
         */
        val ultraGlass: Boolean = false,
        /** Show an aurora gradient as the app background instead of solid surface. */
        val auroraBackground: Boolean = false,
        /** Unit system: "imperial" (miles, mph, F) or "metric" (km, km/h, C). */
        val unitSystem: String = "imperial",
        /** Haptic feedback across the UI. */
        val hapticsEnabled: Boolean = true,
        /**
         * When on, this device installs downloaded updates silently via Shizuku (local ADB) instead
         * of the tap-through system installer. Off by default; device-local capability (Shizuku may
         * not be present on other devices), so it never roams via Drive sync (see
         * SyncMerge.DEVICE_LOCAL_KEYS).
         */
        val seamlessInstallShizuku: Boolean = false,
        /** The user's motion blur switch. The blur also stops in Battery Saver, which is decided by the device, not here. */
        val motionBlur: Boolean = true,
        /**
         * Opt-in "liquid glass" appearance. Off by default = current look; when on, floating chrome
         * and cards use real backdrop refraction (API 31+) or an enhanced-frosted fallback below
         * that.
         */
    )

    // A reactive view of every appearance preference: re-emits a freshly decoded Appearance
    // snapshot on any DataStore change (here, or via Drive sync). Each field falls back to its
    // default when the key is absent or fails to parse, because this flow is collected at launch
    // and must never crash.
    /** Every stored preference by key, live: what search reads so it can show any value in the store. */
    val allPrefs: Flow<Map<String, Any>> = context.settingsDataStore.data.map { it.toNameMap() }

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            // "AMOLED"/"SYSTEM_AMOLED" are legacy values remapped to the nearest modern mode, so an
            // install that had AMOLED selected keeps a dark theme instead of reverting to System.
            themeMode = when (val raw = prefs[Keys.THEME]) {
                "AMOLED" -> ThemeMode.DARK
                "SYSTEM_AMOLED" -> ThemeMode.SYSTEM
                null -> ThemeMode.SYSTEM
                else -> enumOrNull<ThemeMode>(raw) ?: ThemeMode.SYSTEM
            },
            fontChoice = enumOrNull<FontChoice>(prefs[Keys.FONT])
                ?: FontChoice.SYSTEM,
            dynamicColor = prefs.flag(Keys.DYNAMIC, true),
            colorPalette = enumOrNull<ColorPalette>(prefs[Keys.PALETTE])
                ?: ColorPalette.BLUE,
            customPalettes = prefs[Keys.CUSTOM_PALETTES]?.let { json ->
                decodeJsonOr(paletteJson, paletteListSerializer, json, emptyList())
            } ?: emptyList(),
            activeCustomPaletteId = prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID],
            weatherLat = prefs[Keys.WEATHER_LAT]?.toDoubleOrNull(),
            weatherLon = prefs[Keys.WEATHER_LON]?.toDoubleOrNull(),
            weatherLabel = prefs[Keys.WEATHER_LABEL],
            weatherFollowsDevice = prefs[Keys.WEATHER_FOLLOWS_DEVICE]?.toBoolean() ?: false,
            biometricLock = prefs.flag(Keys.BIOMETRIC, false),
            lockTiming = enumOrNull<LockTiming>(prefs[Keys.LOCK_TIMING])
                ?: LockTiming.IMMEDIATE,
            columnsFlipped = prefs.flag(Keys.FLIPPED, false),
            // Clamp on read: a corrupt/hand-edited/foreign backup with e.g. ui_scale="10" would
            // otherwise scale the whole UI 10x and lock the user out of Settings, so a bad stored
            // value can never take effect.
            uiScale = (prefs.floatOr(Keys.UI_SCALE, 1f)).coerceIn(0.85f, 1.3f),
            vibrancy = (prefs.floatOr(Keys.VIBRANCY, 1f)).coerceIn(0.5f, 1.6f),
            glassClarity = (prefs.floatOr(Keys.GLASS_CLARITY, 0.8f)).coerceIn(0f, 1f),
            ultraGlass = prefs.flag(Keys.ULTRA_GLASS, false),
            hapticsEnabled = prefs.flag(Keys.HAPTICS, true),
            auroraBackground = prefs.flag(Keys.AURORA, false),
            unitSystem = prefs[Keys.UNIT_SYSTEM] ?: "imperial",
            // Shared rule -- see FormatUtils.useFahrenheit for why this stopped being written out
            // separately per surface. The per-measurement overrides only apply in Advanced mode,
            // where they can be set; in Simple mode the one Units choice is global.
            useFahrenheit = resolveFahrenheit(prefs[Keys.UNIT_SYSTEM], prefs[Keys.TEMP_UNIT].takeIf { prefs[Keys.SETTINGS_MODE] == "advanced" }),
            metricDistance = resolveMetricDistance(prefs[Keys.UNIT_SYSTEM], prefs[Keys.DISTANCE_UNIT].takeIf { prefs[Keys.SETTINGS_MODE] == "advanced" }),
            tempUnit = prefs[Keys.TEMP_UNIT] ?: "auto",
            distanceUnit = prefs[Keys.DISTANCE_UNIT] ?: "auto",
            seamlessInstallShizuku = prefs.flag(Keys.SEAMLESS_INSTALL_SHIZUKU, false),
            motionBlur = prefs.flag(Keys.MOTION_BLUR, true),
        )
    }
        // Off the main thread. This is a ~40-field decode including two JSON parses (custom
        // palettes and per-car palette ids), and DataStore only guarantees the FILE read is off
        // main -- a map{} transform runs in the collector's context.
        .flowOn(Dispatchers.Default)

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
    }.flowOn(Dispatchers.Default)

    // --- Device sync identity + registry (all device-local: see SyncMerge.DEVICE_LOCAL_KEYS) ---
    //
    // These describe THIS install's participation in the shared Drive file. They
    // never travel in the portable backup (they're in DEVICE_LOCAL_KEYS, so
    // editTracked never marks them dirty and buildExport never emits them). They're
    // written with the RAW DataStore (context.settingsDataStore.edit), NOT
    // editTracked, precisely so touching them can't pollute the dirty set or trip a
    // content-hash change.

    internal val devicesJson = BlooJson
    internal val deviceListSerializer = ListSerializer(SyncMerge.SyncDevice.serializer())

    /** Outcome of one [performMainToMainSync] pass. */
    data class MainToMainSyncOutcome(
        /** False when sync isn't configured, or was skipped (Wi-Fi-only, not on Wi-Fi). */
        val ran: Boolean,
        val imported: Boolean,
        val uploaded: Boolean,
        /** The timestamp this pass recorded as the last-sync time (unchanged if !ran). */
        val syncedAtMs: Long,
        /**
         * A user-facing reason the pass didn't fully succeed, or null if it did (or wasn't
         * configured - that's not a failure).
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

    // --- Per-car climate settings + presets ------------------------------

    internal val climateJson = BlooJson
    internal val presetListSerializer = ListSerializer(ClimatePreset.serializer())

    // --- Custom colour palettes ------------------------------------------

    internal val paletteJson = BlooJson
    internal val paletteListSerializer = ListSerializer(CustomPaletteData.serializer())

    // --- Full settings backup --------------------------------------------

    internal val backupJson = BlooBackupJson

    /** The settings-backup format version. */
    internal val BACKUP_VERSION = SyncMerge.BACKUP_VERSION

    /**
     * Preference keys that describe THIS device's own Drive-sync wiring (a content:// URI this app
     * instance was granted permission for, local bookkeeping of when it last synced, its own
     * Wi-Fi-only preference, and which keys it's changed locally since its last sync) - never
     * portable, so never included in or restored from a settings backup.
     */
    internal val DEVICE_LOCAL_KEYS = SyncMerge.DEVICE_LOCAL_KEYS

    /**
     * How recently a file in `cars/` must have been written to be spared by [pruneOrphanPhotos].
     */
    internal val MIN_ORPHAN_AGE_MS = 10 * 60 * 1000L
}

/**
 * The store as `name -> value`: the ONE view of the preferences that both Drive sync's export and
 * search's live index are built from, so what syncs and what can be found cannot drift apart.
 */
/** A stored whole number, or [default] when it was never written or does not parse. */
internal fun Preferences.intOr(key: Preferences.Key<String>, default: Int): Int = this[key]?.toIntOrNull() ?: default

/** A stored decimal, or [default] when it was never written or does not parse. */
internal fun Preferences.floatOr(key: Preferences.Key<String>, default: Float): Float = this[key]?.toFloatOrNull() ?: default

/** Saves a true/false setting as its text, the form [flag] reads back. */
internal suspend fun SettingsStore.putFlag(key: Preferences.Key<String>, value: Boolean) =
    editTracked { it[key] = value.toString() }

/** A stored true/false preference, or [default] when it has never been written or is not a boolean. */
internal fun Preferences.flag(key: Preferences.Key<String>, default: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default

internal fun androidx.datastore.preferences.core.Preferences.toNameMap(): Map<String, Any> =
    asMap().entries.associate { it.key.name to it.value }
