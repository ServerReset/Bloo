package com.bloo.bluelink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.ui.ColorPalette
import com.bloo.bluelink.ui.CustomPaletteData
import com.bloo.bluelink.ui.FontChoice
import com.bloo.bluelink.ui.ThemeMode
import com.bloo.bluelink.widget.WidgetInfoField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// A corruption handler so a settings file damaged by an interrupted write / power
// loss resets to empty prefs instead of rethrowing IOException out of every read
// (which crashed the app on launch, since `appearance` is collected eagerly).
private val Context.settingsDataStore by preferencesDataStore(
    name = "bloo_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

// Process-wide serialization for performDriveSync(): the periodic worker, the
// auto-sync-on-refresh collector, and a watch-requested sync can all fire at
// nearly the same moment, and SettingsStore is instantiated fresh at each call
// site (not a singleton) — a per-instance lock wouldn't serialize anything, so
// this lives at module scope instead, same pattern as BlueLinkGate.statusMutex.
private val driveSyncMutex = Mutex()

// A stalled SAF/DocumentsProvider call previously had no bound and could hold
// driveSyncMutex indefinitely; each Drive I/O step in performDriveSync() is
// capped at this long instead.
private const val DRIVE_IO_TIMEOUT_MS = 20_000L

/**
 * Which seat heat/cool functions a specific car actually has (user-configured).
 *
 * The US remote-start climate command addresses four seat positions only —
 * driver, front passenger, rear-left and rear-right — so even on a 7-seater
 * those are the seats that can be controlled remotely. Each is independently
 * heat- and/or cool-capable.
 */
data class SeatConfig(
    val driverHeat: Boolean = true,
    val driverCool: Boolean = false,
    val passHeat: Boolean = true,
    val passCool: Boolean = false,
    val rearLeftHeat: Boolean = false,
    val rearLeftCool: Boolean = false,
    val rearRightHeat: Boolean = false,
    val rearRightCool: Boolean = false,
    /** Whether the car has a heated steering wheel (no reliable API flag). */
    val steeringWheel: Boolean = false,
) {
    val any: Boolean
        get() = driverHeat || driverCool || passHeat || passCool ||
            rearLeftHeat || rearLeftCool || rearRightHeat || rearRightCool
}

/** User-confirmed powertrain (the US API only exposes EV vs gas). */
enum class Powertrain { GAS, HYBRID, PHEV, EV }

/** When the biometric app-lock re-engages after the app leaves the foreground. */
enum class LockTiming(val label: String) {
    OFF("Off"),
    IMMEDIATE("Immediate"),
    AFTER_1_MIN("1 min"),
    AFTER_5_MIN("5 min"),
    AFTER_10_MIN("10 min"),
}

/** Reorderable detail sections (pebbles), in their default order. */
// "climate" ahead of "ai": pre-heating/cooling the car before walking out to
// it is the single most common "glance and go" action this app exists for,
// while AI summary is a passive, network-dependent read -- the old order put
// a "Summarize" button ahead of every actual control on both phone and watch
// (the watch's tile order mirrors this list) for anyone who hasn't
// customized their section order.
val DEFAULT_SECTIONS = listOf("summary", "update", "controls", "charge", "climate", "ai", "info", "location", "weather", "trips", "diagnostics")

/** Pebbles the user may hide (the others are essential). */
val HIDEABLE_SECTIONS = listOf("charge", "climate", "location", "weather", "trips", "info", "diagnostics", "ai")

/** Number of configurable Quick Settings tiles (room for ~two per car). */
const val TILE_COUNT = 12

/**
 * App appearance preferences, kept separate from the session so sign-out keeps them.
 *
 * Mechanically, this class is a thin typed wrapper around a single Jetpack
 * DataStore<Preferences> instance ([Context.settingsDataStore]), which is itself
 * just a flat string/boolean key-value bag persisted to a file on disk. There is
 * no schema migration framework here: every getter reads the current value (or a
 * hardcoded default when the key is absent, which is what "this preference was
 * never set" always looks like) and every setter writes through [editTracked],
 * a wrapper around DataStore's `edit {}` that also records which keys changed so
 * Google Drive sync (see [performDriveSync]) can tell which values are "dirty"
 * (changed locally but not yet uploaded).
 *
 * Because DataStore only stores primitives, anything structured (climate presets,
 * custom palettes, per-widget action lists, the full settings backup itself) is
 * JSON-encoded with kotlinx.serialization into a single string value under one
 * key, then decoded back out on read. Anything keyed per-car interpolates the
 * vehicle's VIN directly into the preference key name (e.g. "plate_$vin",
 * "climate_$vin") rather than using a nested/structured key space, since
 * Preferences DataStore only supports a flat namespace.
 */
class SettingsStore(private val context: Context) {

    /** All strongly-typed, non-interpolated preference keys used directly by
     *  name below. Per-car/per-tile/per-widget keys are instead built ad hoc
     *  with string interpolation (see e.g. [seatConfig], [tileConfig]) since
     *  Preferences DataStore has no notion of a keyed sub-namespace — this
     *  object only holds the ones that are the same for the whole app. */
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("font_choice")
        val DYNAMIC = stringPreferencesKey("dynamic_color")
        val PALETTE = stringPreferencesKey("color_palette")
        val CUSTOM_PALETTES = stringPreferencesKey("custom_palettes")
        val ACTIVE_CUSTOM_PALETTE_ID = stringPreferencesKey("active_custom_palette_id")
        val CAR_PALETTE_IDS = stringPreferencesKey("car_palette_ids")
        val WEATHER_LAT = stringPreferencesKey("weather_lat")
        val WEATHER_LON = stringPreferencesKey("weather_lon")
        val WEATHER_LABEL = stringPreferencesKey("weather_label")
        val BIOMETRIC = stringPreferencesKey("biometric_lock")
        val LOCK_TIMING = stringPreferencesKey("lock_timing")
        val FLIPPED = stringPreferencesKey("columns_flipped")
        val LINKS_IN_APP = stringPreferencesKey("links_in_app")
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val VIBRANCY = stringPreferencesKey("vibrancy")
        val HAPTICS = stringPreferencesKey("haptics_enabled")
        val PEBBLE_OUTLINE = stringPreferencesKey("pebble_outline")
        val AURORA = stringPreferencesKey("aurora_background")
        val AURORA_MOTION = stringPreferencesKey("aurora_motion")
        val AURORA_COLOR_MODE = stringPreferencesKey("aurora_color_mode")
        val AURORA_CUSTOM_COLOR = stringPreferencesKey("aurora_custom_color")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val LAST_VIN = stringPreferencesKey("last_vehicle_vin")
        val ORDER = stringPreferencesKey("vehicle_order")
        val SETTINGS_MODE = stringPreferencesKey("settings_mode")
        /** Watch's own PIN lock enabled/timing, mirrored here purely as a backup
         *  record (see WearLocalPayload's doc comment) -- the phone never reads
         *  or acts on these, and never pushes them back down to the watch. */
        val WATCH_PIN_ENABLED = stringPreferencesKey("watch_pin_lock_enabled")
        val WATCH_PIN_TIMING = stringPreferencesKey("watch_pin_lock_timing")
        /** Per-VIN default climate preset ID for the one-tap Start button in advanced mode. */
        const val DEFAULT_CLIMATE_PRESET_PREFIX = "default_climate_preset_"
    }

    data class Appearance(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val fontChoice: FontChoice = FontChoice.SYSTEM,
        val dynamicColor: Boolean = true,
        /** Which built-in palette to use when dynamic colour is off and no custom palette is active. */
        val colorPalette: ColorPalette = ColorPalette.BLUE,
        /** User-saved custom colour palettes. */
        val customPalettes: List<CustomPaletteData> = emptyList(),
        /** ID of the active custom palette, or null to use a built-in palette. */
        val activeCustomPaletteId: String? = null,
        /** Per-vehicle custom palette overrides: VIN → customPaletteId (absent = use global). */
        val carCustomPaletteIds: Map<String, String> = emptyMap(),
        /** User-set weather location (latitude/longitude/place label), or null if unset. */
        val weatherLat: Double? = null,
        val weatherLon: Double? = null,
        val weatherLabel: String? = null,
        /** True for imperial (°F), false for metric (°C). Derived from [unitSystem]. */
        val useFahrenheit: Boolean = true,
        val biometricLock: Boolean = false,
        /** When the biometric lock re-engages after leaving the foreground. */
        val lockTiming: LockTiming = LockTiming.IMMEDIATE,
        /** In the wide expanded view, put pebbles on the left, controls right. */
        val columnsFlipped: Boolean = false,
        /** Open Hyundai/Genesis links in an in-app browser tab vs the system browser. */
        val linksInApp: Boolean = true,
        /** Text/UI scale multiplier (0.85–1.3). */
        val uiScale: Float = 1f,
        /** Colour vibrancy multiplier (0.5–1.6, 1 = default). */
        val vibrancy: Float = 1f,
        /** Show an aurora gradient as the app background instead of solid surface. */
        val auroraBackground: Boolean = false,
        /** Aurora motion mode: "off", "static", "motion". */
        val auroraMotion: String = "static",
        /** Aurora color mode: "complementary", "material", "custom". */
        val auroraColorMode: String = "complementary",
        /** Custom color hex for aurora (only used when auroraColorMode is "custom"). */
        val auroraCustomColor: String? = null,
        /** Unit system: "imperial" (miles, mph, F) or "metric" (km, km/h, C). */
        val unitSystem: String = "imperial",
        /** Haptic feedback across the UI. */
        val hapticsEnabled: Boolean = true,
        /** Hairline rim on pebbles/hero card. Off by default -- most of the app's
         *  "floating chrome" (buttons, dialogs, the search bar) always has one,
         *  but pebbles are the majority of on-screen surface area, and a rim on
         *  every single one read as busier than most people want as the default. */
        val pebbleOutline: Boolean = false,
        /** Watch's own PIN lock enabled/timing -- a backup record only, mirrored
         *  from the watch. See [SettingsStore.Keys.WATCH_PIN_ENABLED]'s comment. */
        val watchPinLockEnabled: Boolean = false,
        val watchPinLockTiming: String = "immediate",
    )

    // A reactive view of every appearance-related preference at once: each time
    // the underlying DataStore file changes (from any editTracked() call, on
    // this device or, via Drive sync, effectively from another), the Flow
    // re-emits a freshly-decoded Appearance snapshot. Every field below applies
    // the same pattern: read the raw string/boolean for its key, and if it's
    // absent (never set) or fails to parse (enum renamed, corrupt value) fall
    // back to a hardcoded default rather than throwing — this flow is collected
    // eagerly near app launch, so a decode failure here must never crash startup.
    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        Appearance(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            fontChoice = prefs[Keys.FONT]?.let { runCatching { FontChoice.valueOf(it) }.getOrNull() }
                ?: FontChoice.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC]?.toBooleanStrictOrNull() ?: true,
            colorPalette = prefs[Keys.PALETTE]?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() }
                ?: ColorPalette.BLUE,
            customPalettes = prefs[Keys.CUSTOM_PALETTES]?.let { json ->
                runCatching { paletteJson.decodeFromString(paletteListSerializer, json) }.getOrElse { emptyList() }
            } ?: emptyList(),
            activeCustomPaletteId = prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID],
            carCustomPaletteIds = prefs[Keys.CAR_PALETTE_IDS]?.let { json ->
                runCatching {
                    paletteJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
                }.getOrElse { emptyMap() }
            } ?: emptyMap(),
            weatherLat = prefs[Keys.WEATHER_LAT]?.toDoubleOrNull(),
            weatherLon = prefs[Keys.WEATHER_LON]?.toDoubleOrNull(),
            weatherLabel = prefs[Keys.WEATHER_LABEL],
            biometricLock = prefs[Keys.BIOMETRIC]?.toBooleanStrictOrNull() ?: false,
            lockTiming = prefs[Keys.LOCK_TIMING]?.let { runCatching { LockTiming.valueOf(it) }.getOrNull() }
                ?: LockTiming.IMMEDIATE,
            columnsFlipped = prefs[Keys.FLIPPED]?.toBooleanStrictOrNull() ?: false,
            linksInApp = prefs[Keys.LINKS_IN_APP]?.toBooleanStrictOrNull() ?: true,
            // Clamp on read: a corrupt/hand-edited/foreign backup with e.g.
            // ui_scale="10" would otherwise scale the whole UI 10x and lock the
            // user out of Settings, so a bad stored value can never take effect.
            uiScale = (prefs[Keys.UI_SCALE]?.toFloatOrNull() ?: 1f).coerceIn(0.85f, 1.3f),
            vibrancy = (prefs[Keys.VIBRANCY]?.toFloatOrNull() ?: 1f).coerceIn(0.5f, 1.6f),
            hapticsEnabled = prefs[Keys.HAPTICS]?.toBooleanStrictOrNull() ?: true,
            auroraBackground = prefs[Keys.AURORA]?.toBooleanStrictOrNull() ?: false,
            auroraMotion = prefs[Keys.AURORA_MOTION] ?: "static",
            auroraColorMode = prefs[Keys.AURORA_COLOR_MODE] ?: "complementary",
            auroraCustomColor = prefs[Keys.AURORA_CUSTOM_COLOR],
            unitSystem = prefs[Keys.UNIT_SYSTEM] ?: "imperial",
            useFahrenheit = (prefs[Keys.UNIT_SYSTEM] ?: "imperial") != "metric",
            watchPinLockEnabled = prefs[Keys.WATCH_PIN_ENABLED]?.toBooleanStrictOrNull() ?: false,
            watchPinLockTiming = prefs[Keys.WATCH_PIN_TIMING] ?: "immediate",
            pebbleOutline = prefs[Keys.PEBBLE_OUTLINE]?.toBooleanStrictOrNull() ?: false,
        )
    }

    // Simple appearance setters below: each just writes one Keys.* string value
    // through editTracked() (which persists it to DataStore and marks the key
    // dirty for the next Drive sync upload). Booleans are stored as their
    // String.toString() ("true"/"false") rather than a native boolean pref
    // because Preferences DataStore keys are typed per-instance (a
    // booleanPreferencesKey and stringPreferencesKey with the same name are
    // different keys) and this file mixes both conventions depending on when
    // the field was added; the corresponding read side above always parses
    // with toBooleanStrictOrNull() and falls back to the field's default.

    suspend fun setHapticsEnabled(value: Boolean) {
        editTracked { it[Keys.HAPTICS] = value.toString() }
    }

    suspend fun setPebbleOutline(value: Boolean) {
        editTracked { it[Keys.PEBBLE_OUTLINE] = value.toString() }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        editTracked { it[Keys.BIOMETRIC] = enabled.toString() }
    }

    /** Stores the enum's name() as a string; read back with LockTiming.valueOf(),
     *  falling back to LockTiming.IMMEDIATE if the stored name no longer matches
     *  an enum constant (e.g. after a rename). */
    suspend fun setLockTiming(value: LockTiming) {
        editTracked { it[Keys.LOCK_TIMING] = value.name }
    }

    /** Mirrors the watch's own PIN lock enabled/timing for backup purposes only
     *  -- called from WearPhoneService when the watch pushes a change, never
     *  from phone UI (the phone has no control over the watch's PIN lock). */
    suspend fun setWatchPinLock(enabled: Boolean, timing: String) {
        editTracked {
            it[Keys.WATCH_PIN_ENABLED] = enabled.toString()
            it[Keys.WATCH_PIN_TIMING] = timing
        }
    }

    suspend fun setColumnsFlipped(flipped: Boolean) {
        editTracked { it[Keys.FLIPPED] = flipped.toString() }
    }

    // --- Notifications --------------------------------------------------

    /** App-wide (not per-car) notification toggles and thresholds. [service]
     *  gates the persistent foreground-service notification; [doorOpen] and
     *  [running] gate the "door left open" / "engine left running" alerts,
     *  each firing once the condition has held continuously for its paired
     *  *Minutes threshold (see [doorOpenSince]/[engineOnSince] below, which
     *  track how long the condition has been true per car). */
    data class NotificationPrefs(
        val service: Boolean = true,
        val doorOpen: Boolean = true,
        val doorOpenMinutes: Int = 5,
        val running: Boolean = true,
        val runningMinutes: Int = 10,
    )

    /** One-shot read of [NotificationPrefs] (vs. the [notifications] Flow below,
     *  which stays subscribed) — used where a caller just needs the current
     *  values once, e.g. deciding whether to schedule a check at all. */
    suspend fun notificationPrefs(): NotificationPrefs {
        val p = context.settingsDataStore.data.first()
        return NotificationPrefs(
            service = p[booleanPreferencesKey("notify_service")] ?: true,
            doorOpen = p[booleanPreferencesKey("notify_door")] ?: true,
            doorOpenMinutes = p[stringPreferencesKey("notify_door_min")]?.toIntOrNull() ?: 5,
            running = p[booleanPreferencesKey("notify_running")] ?: true,
            runningMinutes = p[stringPreferencesKey("notify_running_min")]?.toIntOrNull() ?: 10,
        )
    }

    /** Reactive equivalent of [notificationPrefs] for UI that needs to update
     *  live when the user changes a toggle in Settings while the screen is open. */
    val notifications: Flow<NotificationPrefs> = context.settingsDataStore.data.map { p ->
        NotificationPrefs(
            service = p[booleanPreferencesKey("notify_service")] ?: true,
            doorOpen = p[booleanPreferencesKey("notify_door")] ?: true,
            doorOpenMinutes = p[stringPreferencesKey("notify_door_min")]?.toIntOrNull() ?: 5,
            running = p[booleanPreferencesKey("notify_running")] ?: true,
            runningMinutes = p[stringPreferencesKey("notify_running_min")]?.toIntOrNull() ?: 10,
        )
    }

    // One setter per NotificationPrefs field; `.let {}` just discards editTracked's
    // Unit return so these can stay one-expression functions (`=` body) rather
    // than needing an explicit block body.
    suspend fun setNotifyService(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_service")] = v }.let {}

    suspend fun setNotifyDoor(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_door")] = v }.let {}

    suspend fun setDoorOpenMinutes(v: Int) =
        editTracked { it[stringPreferencesKey("notify_door_min")] = v.toString() }.let {}

    suspend fun setNotifyRunning(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_running")] = v }.let {}

    suspend fun setRunningMinutes(v: Int) =
        editTracked { it[stringPreferencesKey("notify_running_min")] = v.toString() }.let {}

    // Transient alert bookkeeping (per car), used to fire each alert only once.
    // Mechanism: when AlertWorker (see work/AlertWorker.kt) first observes a
    // door open (or engine running), it stamps "door_since_$vin"/"engine_since_$vin"
    // with the current time via the setters below. On each subsequent check it
    // reads that timestamp back and compares elapsed time against the configured
    // *Minutes threshold; once the threshold is crossed AND the per-condition
    // alertFired(key) flag isn't already set, it fires the notification and
    // flips alertFired to true so it won't repeat. The *Since value is cleared
    // (set to null, which removes the key) as soon as the condition stops being
    // true, so the next occurrence starts timing from zero again.
    suspend fun doorOpenSince(vin: String): Long? =
        context.settingsDataStore.data.first()[stringPreferencesKey("door_since_$vin")]?.toLongOrNull()

    suspend fun setDoorOpenSince(vin: String, value: Long?) {
        editTracked {
            val k = stringPreferencesKey("door_since_$vin")
            if (value == null) it.remove(k) else it[k] = value.toString()
        }
    }

    suspend fun engineOnSince(vin: String): Long? =
        context.settingsDataStore.data.first()[stringPreferencesKey("engine_since_$vin")]?.toLongOrNull()

    suspend fun setEngineOnSince(vin: String, value: Long?) {
        editTracked {
            val k = stringPreferencesKey("engine_since_$vin")
            if (value == null) it.remove(k) else it[k] = value.toString()
        }
    }

    /** Whether a specific alert (identified by an arbitrary caller-defined
     *  [key], typically something like "door_$vin" or "running_$vin") has
     *  already fired for its current occurrence, so callers don't notify twice
     *  for the same continuous door-open/engine-running spell. */
    suspend fun alertFired(key: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("alert_$key")] ?: false

    suspend fun setAlertFired(key: String, value: Boolean) {
        editTracked { it[booleanPreferencesKey("alert_$key")] = value }
    }

    suspend fun setUiScale(value: Float) {
        editTracked { it[Keys.UI_SCALE] = value.toString() }
    }

    suspend fun setVibrancy(value: Float) {
        editTracked { it[Keys.VIBRANCY] = value.toString() }
    }

    suspend fun setLinksInApp(value: Boolean) {
        editTracked { it[Keys.LINKS_IN_APP] = value.toString() }
    }

    suspend fun setAuroraBackground(value: Boolean) {
        editTracked { it[Keys.AURORA] = value.toString() }
    }

    // Both setters below validate the incoming string against the fixed set of
    // legal values and silently fall back to the default if it's anything else
    // (e.g. a stale string from a future app version we don't recognize),
    // rather than storing garbage that the appearance Flow above would then
    // have to re-validate on every read.
    suspend fun setAuroraMotion(value: String) {
        editTracked { it[Keys.AURORA_MOTION] = value.takeIf { it in setOf("off", "static", "motion") } ?: "static" }
    }

    suspend fun setAuroraColorMode(value: String) {
        editTracked { it[Keys.AURORA_COLOR_MODE] = value.takeIf { it in setOf("complementary", "material", "custom") } ?: "complementary" }
    }

    suspend fun setAuroraCustomColor(value: String?) {
        editTracked {
            val key = Keys.AURORA_CUSTOM_COLOR
            if (value.isNullOrBlank()) it.remove(key) else it[key] = value
        }
    }

    suspend fun setUnitSystem(value: String) {
        editTracked { it[Keys.UNIT_SYSTEM] = value.takeIf { it in setOf("imperial", "metric") } ?: "imperial" }
    }

    /** Settings view mode: "simple" or "advanced". */
    suspend fun settingsMode(): String =
        context.settingsDataStore.data.first()[Keys.SETTINGS_MODE] ?: "simple"

    suspend fun setSettingsMode(value: String) {
        editTracked { it[Keys.SETTINGS_MODE] = value }
    }

    /** Per-VIN default climate preset ID for the one-tap Start button. */
    suspend fun defaultClimatePreset(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey(Keys.DEFAULT_CLIMATE_PRESET_PREFIX + vin)]?.takeIf { it.isNotBlank() }

    suspend fun setDefaultClimatePreset(vin: String, id: String?) {
        editTracked {
            val key = stringPreferencesKey(Keys.DEFAULT_CLIMATE_PRESET_PREFIX + vin)
            if (id.isNullOrBlank()) it.remove(key) else it[key] = id
        }
    }

    // --- Per-car identity + service (the API has no service-history fields) ---

    suspend fun licensePlate(vin: String): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("plate_$vin")] ?: ""

    suspend fun setLicensePlate(vin: String, value: String) {
        editTracked {
            val key = stringPreferencesKey("plate_$vin")
            if (value.isBlank()) it.remove(key) else it[key] = value.trim()
        }
    }

    suspend fun lastServiceMiles(vin: String): Int? =
        context.settingsDataStore.data.first()[stringPreferencesKey("svc_last_$vin")]?.toIntOrNull()

    suspend fun setLastServiceMiles(vin: String, value: Int?) {
        editTracked {
            val key = stringPreferencesKey("svc_last_$vin")
            if (value == null) it.remove(key) else it[key] = value.toString()
        }
    }

    suspend fun serviceIntervalMiles(vin: String): Int? =
        context.settingsDataStore.data.first()[stringPreferencesKey("svc_interval_$vin")]?.toIntOrNull()

    suspend fun setServiceIntervalMiles(vin: String, value: Int?) {
        editTracked {
            val key = stringPreferencesKey("svc_interval_$vin")
            if (value == null) it.remove(key) else it[key] = value.toString()
        }
    }

    suspend fun lastVehicleVin(): String? =
        context.settingsDataStore.data.first()[Keys.LAST_VIN]

    suspend fun setLastVehicleVin(vin: String) {
        editTracked { it[Keys.LAST_VIN] = vin }
    }

    /** User-defined display order of vehicles (by VIN). */
    suspend fun vehicleOrder(): List<String> =
        context.settingsDataStore.data.first()[Keys.ORDER]
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    suspend fun setVehicleOrder(order: List<String>) {
        editTracked { it[Keys.ORDER] = order.joinToString("\n") }
    }

    /** Optional user-set photo URL per vehicle (empty = use the default gradient). */
    suspend fun imageUrl(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("img_$vin")]?.takeIf { it.isNotBlank() }

    suspend fun setImageUrl(vin: String, url: String) {
        editTracked {
            val key = stringPreferencesKey("img_$vin")
            if (url.isBlank()) it.remove(key) else it[key] = url.trim()
        }
    }

    // --- Per-car seat capability (the API has no reliable flags) ---------

    /**
     * Reads the per-seat heat/cool capability flags for [vin], each stored under
     * its own short-suffixed key (e.g. "seat_dh_$vin" for driver-heat).
     *
     * Migration mechanism: earlier app versions only tracked one flag per axle
     * (front heat/cool, rear heat/cool) rather than per-individual-seat. Each new
     * per-seat key is looked up first; if it's absent (the user's data predates
     * the per-seat split, or this specific seat was never touched since), the
     * matching old grouped flag is used as the fallback, and if THAT is also
     * absent a hardcoded default applies. This means an existing user's old
     * front-heat=true setting transparently becomes both driver-heat=true and
     * passenger-heat=true the first time this is read, without any explicit
     * one-time migration step or version bump.
     */
    suspend fun seatConfig(vin: String): SeatConfig {
        val p = context.settingsDataStore.data.first()
        fun b(key: String): Boolean? = p[booleanPreferencesKey(key)]
        // Migration: older builds stored grouped front/rear flags.
        val oldFrontHeat = b("seat_fh_$vin")
        val oldFrontCool = b("seat_fc_$vin")
        val oldRearHeat = b("seat_rh_$vin")
        val oldRearCool = b("seat_rc_$vin")
        return SeatConfig(
            driverHeat = b("seat_dh_$vin") ?: oldFrontHeat ?: true,
            driverCool = b("seat_dc_$vin") ?: oldFrontCool ?: false,
            passHeat = b("seat_ph_$vin") ?: oldFrontHeat ?: true,
            passCool = b("seat_pc_$vin") ?: oldFrontCool ?: false,
            rearLeftHeat = b("seat_rlh_$vin") ?: oldRearHeat ?: false,
            rearLeftCool = b("seat_rlc_$vin") ?: oldRearCool ?: false,
            rearRightHeat = b("seat_rrh_$vin") ?: oldRearHeat ?: false,
            rearRightCool = b("seat_rrc_$vin") ?: oldRearCool ?: false,
            steeringWheel = b("seat_sw_$vin") ?: false,
        )
    }

    /** [field] is one of dh/dc/ph/pc/rlh/rlc/rrh/rrc. */
    suspend fun setSeatFlag(vin: String, field: String, value: Boolean) {
        editTracked { it[booleanPreferencesKey("seat_${field}_$vin")] = value }
    }

    // --- First-run onboarding -------------------------------------------

    suspend fun onboardingSeen(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("onboarding_seen")] ?: false

    suspend fun setOnboardingSeen() {
        editTracked { it[booleanPreferencesKey("onboarding_seen")] = true }
    }

    /** True once a car has been through the feature-setup wizard. */
    suspend fun isCarConfigured(vin: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("car_configured_$vin")] ?: false

    suspend fun setCarConfigured(vin: String) {
        editTracked { it[booleanPreferencesKey("car_configured_$vin")] = true }
    }

    // --- Per-car section order -------------------------------------------

    /**
     * The order in which detail-pebble sections should render for [vin],
     * reconciled against [DEFAULT_SECTIONS] so app updates that add a brand-new
     * section (or a user's stored list that's stale/corrupt) still produce a
     * complete, valid ordering rather than silently dropping the new section
     * forever.
     *
     * Mechanism: the saved comma-separated order is read and filtered down to
     * only names still present in DEFAULT_SECTIONS (drops anything renamed or
     * removed since). If nothing valid is left, the whole default order is used
     * as-is. Otherwise, any DEFAULT_SECTIONS entries missing from the saved list
     * (i.e. new since the user last customized their order) are inserted:
     * "summary"/"controls" are always pinned back to the very front (in
     * DEFAULT_SECTIONS order) since they're the primary at-a-glance sections;
     * every other missing section is inserted immediately after the nearest
     * section that precedes it in DEFAULT_SECTIONS order and IS present in the
     * user's list, so a newly-added section lands in a sensible relative spot
     * instead of always being tacked onto the end.
     */
    suspend fun sectionOrder(vin: String): List<String> {
        val saved = context.settingsDataStore.data.first()[stringPreferencesKey("sections_$vin")]
            ?.split(",")?.filter { it.isNotBlank() }
        val valid = saved?.filter { it in DEFAULT_SECTIONS } ?: emptyList()
        if (valid.isEmpty()) return DEFAULT_SECTIONS
        val result = valid.toMutableList()
        val missing = DEFAULT_SECTIONS.filter { it !in result }
        val (lead, trail) = missing.partition { it == "summary" || it == "controls" }
        // Prepend any missing pinned-lead sections in order.
        lead.reversed().forEach { s -> result.add(0, s) }
        // Insert each remaining new section after its nearest preceding sibling in
        // DEFAULT_SECTIONS order so it lands in a sensible position (e.g. "ai" goes
        // right after "charge" rather than being appended at the end).
        for (section in trail) {
            val defIdx = DEFAULT_SECTIONS.indexOf(section)
            val predecessor = DEFAULT_SECTIONS.subList(0, defIdx).lastOrNull { it in result }
            val pos = if (predecessor != null) result.indexOf(predecessor) + 1 else result.size
            result.add(pos, section)
        }
        return result
    }

    suspend fun setSectionOrder(vin: String, order: List<String>) {
        editTracked { it[stringPreferencesKey("sections_$vin")] = order.joinToString(",") }
    }

    /** Shared helper: reads [key] as a comma-separated string and splits it back
     *  into a Set, dropping empty segments (so a stored empty string decodes to
     *  an empty set rather than a set containing one blank element). Used for
     *  every "set of section names" preference (collapsed/hidden sections here)
     *  since Preferences DataStore has no native Set<String> support for
     *  primitives written as plain strings elsewhere in this file. */
    private fun csv(p: androidx.datastore.preferences.core.Preferences, key: String): Set<String> =
        p[stringPreferencesKey(key)]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun collapsedSections(vin: String): Set<String> =
        csv(context.settingsDataStore.data.first(), "collapsed_$vin")

    /** Toggles [section] in or out of [vin]'s collapsed set: reads the current
     *  CSV-encoded set, adds or removes the section, then re-encodes and writes
     *  it back — a read-modify-write pair inside one editTracked() transaction
     *  so a concurrent write to the same key can't be lost between the read and
     *  the write (DataStore's edit{} block runs with the current prefs snapshot
     *  passed in, not a stale one captured earlier). */
    suspend fun setSectionCollapsed(vin: String, section: String, collapsed: Boolean) {
        editTracked {
            val set = csv(it, "collapsed_$vin").toMutableSet()
            if (collapsed) set.add(section) else set.remove(section)
            it[stringPreferencesKey("collapsed_$vin")] = set.joinToString(",")
        }
    }

    suspend fun hiddenSections(vin: String): Set<String> =
        csv(context.settingsDataStore.data.first(), "hidden_$vin")

    /** Same read-modify-write pattern as [setSectionCollapsed], for the
     *  independent "hidden" set (a hidden section is fully removed from view;
     *  a collapsed one is still shown, just closed by default). */
    suspend fun setSectionHidden(vin: String, section: String, hidden: Boolean) {
        editTracked {
            val set = csv(it, "hidden_$vin").toMutableSet()
            if (hidden) set.add(section) else set.remove(section)
            it[stringPreferencesKey("hidden_$vin")] = set.joinToString(",")
        }
    }

    // --- On-device AI ----------------------------------------------------

    suspend fun aiEnabled(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("ai_enabled")] ?: false

    suspend fun setAiEnabled(value: Boolean) {
        editTracked { it[booleanPreferencesKey("ai_enabled")] = value }
    }

    /** When on, AI summaries run automatically on open/refresh/command (vs only on tap). */
    suspend fun aiAuto(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("ai_auto")] ?: false

    suspend fun setAiAuto(value: Boolean) {
        editTracked { it[booleanPreferencesKey("ai_auto")] = value }
    }

    // --- App-icon shortcut selection -------------------------------------

    /** Enabled shortcut ids ("cmd_vin"); null = never customised (show all). */
    suspend fun enabledShortcuts(): Set<String>? {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("enabled_shortcuts")] ?: return null
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setEnabledShortcuts(ids: Set<String>) {
        editTracked { it[stringPreferencesKey("enabled_shortcuts")] = ids.joinToString(",") }
    }

    // --- Quick Settings tiles --------------------------------------------

    /** Per-tile assignment: (vin, command) or null if unassigned.
     *  Each of the [TILE_COUNT] tiles gets two independent string keys, keyed by
     *  its numeric [index] ("tile_0_vin", "tile_0_cmd", "tile_1_vin", …). Both
     *  must be present and non-blank for the tile to count as configured — if
     *  either is missing (e.g. the car was removed and its keys cleared but the
     *  command key survived some other way) the tile is treated as fully
     *  unassigned rather than half-configured. */
    suspend fun tileConfig(index: Int): Pair<String, String>? {
        val p = context.settingsDataStore.data.first()
        val vin = p[stringPreferencesKey("tile_${index}_vin")]?.takeIf { it.isNotBlank() } ?: return null
        val cmd = p[stringPreferencesKey("tile_${index}_cmd")]?.takeIf { it.isNotBlank() } ?: return null
        return vin to cmd
    }

    /** Passing null/blank for either [vin] or [cmd] clears both keys, unassigning
     *  the tile entirely (a tile can't be half-configured — see [tileConfig]). */
    suspend fun setTileConfig(index: Int, vin: String?, cmd: String?) {
        editTracked {
            val vk = stringPreferencesKey("tile_${index}_vin")
            val ck = stringPreferencesKey("tile_${index}_cmd")
            if (vin.isNullOrBlank() || cmd.isNullOrBlank()) { it.remove(vk); it.remove(ck) }
            else { it[vk] = vin; it[ck] = cmd }
        }
    }

    /** Optional user-chosen label shown on the tile (null → derive from state). */
    suspend fun tileLabel(index: Int): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_${index}_label")]?.takeIf { it.isNotBlank() }

    suspend fun setTileLabel(index: Int, label: String?) {
        editTracked {
            val k = stringPreferencesKey("tile_${index}_label")
            if (label.isNullOrBlank()) it.remove(k) else it[k] = label.trim()
        }
    }

    /** What the climate tile runs: "default", "smart", or a preset id. */
    suspend fun tileClimateTarget(index: Int): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_${index}_climate")]
            ?.takeIf { it.isNotBlank() } ?: "default"

    suspend fun setTileClimateTarget(index: Int, target: String?) {
        editTracked {
            val k = stringPreferencesKey("tile_${index}_climate")
            if (target.isNullOrBlank()) it.remove(k) else it[k] = target
        }
    }

    /** When true, tiles run the command in the background; else they open the app. */
    suspend fun tileBackground(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("tile_background")] ?: false

    suspend fun setTileBackground(value: Boolean) {
        editTracked { it[booleanPreferencesKey("tile_background")] = value }
    }

    /** When true, a tile kicks a throttled status refresh when it becomes visible,
     *  so its lock/climate state stays live (at some battery/rate-limit cost). */
    suspend fun tileLiveRefresh(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("tile_live_refresh")] ?: false

    suspend fun setTileLiveRefresh(value: Boolean) {
        editTracked { it[booleanPreferencesKey("tile_live_refresh")] = value }
    }

    /** Last time a tile-driven refresh ran for [vin] (epoch ms), for throttling. */
    suspend fun tileRefreshedAt(vin: String): Long =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_refreshed_$vin")]?.toLongOrNull() ?: 0L

    suspend fun setTileRefreshedAt(vin: String, value: Long) {
        editTracked { it[stringPreferencesKey("tile_refreshed_$vin")] = value.toString() }
    }

    // --- Home-screen widgets -------------------------------------------------

    /** Every per-widget preference suffix, i.e. the set of "widget_<id>_<suffix>"
     *  keys any widget can write. Defined once here so [clearWidgetConfig] can't
     *  drift out of sync with the setters as new per-widget settings are added
     *  (a missed suffix meant a recycled widget id inherited stale config —
     *  e.g. a near-invisible alpha or the wrong layout). */
    private val WIDGET_KEY_SUFFIXES = listOf(
        "vin", "actions", "info", "pending", "auth", "photobg", "loc", "addr", "alpha", "pill", "layout",
    )

    /** Per-widget assignment: (pinned vin, ordered action keys) or null.
     *  [widgetId] is Android's AppWidgetManager-assigned id for that specific
     *  home-screen widget instance, so each placed widget gets its own
     *  independent set of "widget_<id>_*" keys below — unlike tiles (a fixed
     *  [TILE_COUNT] slots) an arbitrary number of widgets can exist, hence
     *  keying by the OS-provided id rather than a small fixed index. Only the
     *  vin key is required for a widget to count as configured; a missing
     *  actions key just means no action buttons were chosen (empty list), not
     *  that the widget is unconfigured. */
    suspend fun widgetConfig(widgetId: Int): Pair<String, List<String>>? {
        val p = context.settingsDataStore.data.first()
        val vin = p[stringPreferencesKey("widget_${widgetId}_vin")]?.takeIf { it.isNotBlank() } ?: return null
        val actions = p[stringPreferencesKey("widget_${widgetId}_actions")]
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return vin to actions
    }

    suspend fun setWidgetConfig(widgetId: Int, vin: String, actions: List<String>) {
        editTracked {
            it[stringPreferencesKey("widget_${widgetId}_vin")] = vin
            it[stringPreferencesKey("widget_${widgetId}_actions")] = actions.joinToString(",")
        }
    }

    suspend fun clearWidgetConfig(widgetId: Int) {
        editTracked {
            // Remove every per-widget key so a re-used widget id starts clean.
            WIDGET_KEY_SUFFIXES.forEach { suffix ->
                it.remove(stringPreferencesKey("widget_${widgetId}_$suffix"))
                it.remove(booleanPreferencesKey("widget_${widgetId}_$suffix"))
            }
        }
    }

    suspend fun widgetPendingAction(widgetId: Int): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_pending")]?.takeIf { it.isNotBlank() }

    suspend fun setWidgetPendingAction(widgetId: Int, action: String?) {
        editTracked {
            val key = stringPreferencesKey("widget_${widgetId}_pending")
            if (action.isNullOrBlank()) it.remove(key) else it[key] = action
        }
    }

    suspend fun widgetRequireAuth(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_auth")] ?: true

    suspend fun setWidgetRequireAuth(widgetId: Int, value: Boolean) {
        editTracked { it[booleanPreferencesKey("widget_${widgetId}_auth")] = value }
    }

    /** Use the car's set photo as a full-bleed widget background (default off). */
    suspend fun widgetPhotoBackground(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_photobg")] ?: false

    suspend fun setWidgetPhotoBackground(widgetId: Int, value: Boolean) {
        editTracked { it[booleanPreferencesKey("widget_${widgetId}_photobg")] = value }
    }

    /** Widget background transparency level (0 = opaque, 9 = very transparent). */
    suspend fun widgetBackgroundAlpha(widgetId: Int): Int =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_alpha")]
            ?.toIntOrNull() ?: 0

    suspend fun setWidgetBackgroundAlpha(widgetId: Int, value: Int) {
        editTracked { it[stringPreferencesKey("widget_${widgetId}_alpha")] = value.coerceIn(0, 9).toString() }
    }

    /** Show a map/location box on large widgets (default off). */
    suspend fun widgetShowLocation(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_loc")] ?: false

    suspend fun setWidgetShowLocation(widgetId: Int, value: Boolean) {
        editTracked { it[booleanPreferencesKey("widget_${widgetId}_loc")] = value }
    }

    /** Much more rounded corners (pill-like) for small widgets (default false). */
    suspend fun widgetPillShape(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_pill")] ?: false

    suspend fun setWidgetPillShape(widgetId: Int, value: Boolean) {
        editTracked { it[booleanPreferencesKey("widget_${widgetId}_pill")] = value }
    }

    /** Layout preference: "info" (show percent/range) or "controls" (show buttons). */
    suspend fun widgetLayoutMode(widgetId: Int): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_layout")]
            ?: "info"

    suspend fun setWidgetLayoutMode(widgetId: Int, value: String) {
        editTracked { it[stringPreferencesKey("widget_${widgetId}_layout")] = value.takeIf { it in setOf("info", "controls") } ?: "info" }
    }

    /** Ordered "info" mode stats to show, below 3×3 (see WidgetInfoField) -- the
     *  same idea as widgetConfig's action list, but for info mode instead of
     *  controls mode. Key absent = not configured yet, falls back to the
     *  pre-existing fixed set so upgrading doesn't change anyone's widget --
     *  but a key that IS present, even storing "" (every chip deliberately
     *  deselected), has to stay empty. Falling back to defaults there too
     *  (as a naive ifEmpty{} would) made "deselect everything" silently
     *  un-deselect itself the moment the widget next redrew. */
    suspend fun widgetInfoFields(widgetId: Int): List<String> {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_info")]
            ?: return WidgetInfoField.DEFAULTS.map { it.key }
        return raw.split(",").filter { it.isNotBlank() }
    }

    suspend fun setWidgetInfoFields(widgetId: Int, fields: List<String>) {
        editTracked { it[stringPreferencesKey("widget_${widgetId}_info")] = fields.joinToString(",") }
    }

    /** Drive URI for auto-backup; null when not configured. */
    suspend fun syncUri(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_uri")]?.takeIf { it.isNotBlank() }

    suspend fun setSyncUri(uri: String?) {
        editTracked {
            val key = stringPreferencesKey("sync_uri")
            if (uri.isNullOrBlank()) it.remove(key) else it[key] = uri
        }
    }

    /** Timestamp (ms) of the last successful bidirectional sync. */
    suspend fun lastSyncMs(): Long =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_last_ms")]
            ?.toLongOrNull() ?: 0L

    suspend fun setLastSyncMs(ms: Long) {
        editTracked { it[stringPreferencesKey("sync_last_ms")] = ms.toString() }
    }

    /** Persisted so a failure from the background periodic worker (no live
     *  ViewModel to update UiState.syncError) still shows up in Settings the
     *  next time the app is opened, instead of only being visible if a
     *  foreground sync happens to fail while the app is open. */
    suspend fun lastSyncError(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_last_error")]

    suspend fun setLastSyncError(error: String?) {
        editTracked { if (error == null) it.remove(stringPreferencesKey("sync_last_error")) else it[stringPreferencesKey("sync_last_error")] = error }
    }

    /** Wi-Fi only sync (true) or any network (false). */
    suspend fun syncWifiOnly(): Boolean =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_wifi")]
            ?.toBooleanStrictOrNull() ?: true

    suspend fun setSyncWifiOnly(value: Boolean) {
        editTracked { it[stringPreferencesKey("sync_wifi")] = value.toString() }
    }

    /** Outcome of one [performDriveSync] pass. */
    data class DriveSyncOutcome(
        /** False when sync isn't configured, or was skipped (Wi-Fi-only, not on Wi-Fi). */
        val ran: Boolean,
        /** True if a newer remote file was found and imported into this device. */
        val imported: Boolean,
        /** True if this device's settings were successfully uploaded. */
        val uploaded: Boolean,
        /** The timestamp this pass recorded as the last-sync time (unchanged if !ran). */
        val syncedAtMs: Long,
        /** A user-facing reason the pass didn't fully succeed, or null if it did
         *  (or wasn't configured — that's not a failure). */
        val error: String? = null,
    )

    /**
     * One full bidirectional Drive-sync pass: download the file at [syncUri] (if
     * configured), import it when it's newer than our last sync (by the file's
     * real last-modified time, falling back to a timestamp embedded in the file
     * for providers that don't expose one), then upload our current settings with
     * a fresh timestamp.
     *
     * This is the ONE place this logic lives — it used to be duplicated between
     * the phone's auto-sync-on-refresh collector and the watch's on-demand
     * "Sync now" request, which is exactly how a bug (this device's own Drive URI
     * leaking into the portable export) existed in two copies at once.
     */
    suspend fun performDriveSync(): DriveSyncOutcome = driveSyncMutex.withLock {
        // The periodic worker, the auto-sync-on-refresh collector, and a
        // watch-requested sync can all fire within moments of each other with
        // no coordination otherwise -- this mutex makes them run one at a time
        // instead of racing to read/merge/upload the same Drive file.
        val uri = syncUri() ?: return@withLock DriveSyncOutcome(ran = false, imported = false, uploaded = false, syncedAtMs = lastSyncMs())
        if (syncWifiOnly()) {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val wifi = cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            if (!wifi) {
                AppLog.log("⚠ Drive sync: skipped (Wi-Fi only, on cellular)")
                return@withLock DriveSyncOutcome(ran = false, imported = false, uploaded = false, syncedAtMs = lastSyncMs())
            }
        }
        val parsed = android.net.Uri.parse(uri)
        // Check the file's actual last-modified time from Drive.
        val fileModifiedMs = runCatching {
            if (android.provider.DocumentsContract.isDocumentUri(context, parsed)) {
                val cursor = context.contentResolver.query(
                    parsed, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null, null, null,
                )
                cursor?.use {
                    if (it.moveToFirst()) it.getLong(0).takeIf { ts -> ts > 0 }
                    else null
                }
            } else null
        }.getOrNull()
        // Download: read the existing file from Drive.
        var downloadError: String? = null
        // withTimeout, not just runCatching -- a stalled SAF/DocumentsProvider
        // call (Drive app backgrounded, flaky network) previously had no
        // bound at all and could hang this coroutine indefinitely while still
        // holding driveSyncMutex, blocking every other sync path (the worker,
        // the refresh collector, a watch-requested sync) until it resolved.
        // withDriveRetry: one immediate retry so a single transient blip
        // (momentary network hiccup, Drive app briefly waking up) doesn't
        // force waiting for the periodic worker's own backoff or the next
        // unrelated refresh -- this pass is often the ONLY one that runs
        // right after the user enables sync, so it needs to actually land.
        val remoteContent = runCatching {
            withDriveRetry {
                kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                    context.contentResolver.openInputStream(parsed)?.bufferedReader()?.readText()
                }
            }
        }.onFailure {
            downloadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out reading the Drive file" else it.message ?: "Couldn't read the Drive file"
        }.getOrNull()
        val remoteJson = remoteContent?.substringAfter('\n', "")
        val remoteTs = fileModifiedMs ?: (remoteContent?.substringBefore('\n')?.toLongOrNull() ?: 0L)
        var imported = false
        if (remoteTs > lastSyncMs() && remoteJson != null) {
            // Protect anything WE'VE changed locally but haven't uploaded yet —
            // read the dirty set before this pass touches anything, so a merge
            // import can't accidentally protect keys it's about to import itself.
            val protectedKeys = dirtyKeys()
            imported = mergeSettingsJson(remoteJson, protect = protectedKeys)
            if (imported) AppLog.log("Drive sync: imported newer settings")
        }
        val now = System.currentTimeMillis()
        var uploadError: String? = null
        val uploaded: Boolean
        // Never write on a failed read: a download error means we couldn't see
        // the remote file's real contents this pass, so uploading now would
        // truncate-overwrite whatever is actually there with our local state --
        // a last-write-wins clobber of another device's possibly-newer settings.
        // Gate the ENTIRE upload block (dirty snapshot, body build, write/verify,
        // and the lastSyncMs/dirty-clear bookkeeping) on a clean download.
        // First sync is unaffected: a missing/empty Drive file reads with
        // remoteContent==null/empty WITHOUT setting downloadError, so
        // downloadError==null still permits the initial upload.
        if (downloadError != null) {
            uploaded = false
        } else {
            // Snapshot the dirty set that this upload body actually carries, taken
            // right before the body is built. Only these keys may be cleared on
            // success -- a key edited AFTER this point (setters don't take
            // driveSyncMutex, so a local edit can land mid-upload) isn't reflected
            // in `body`, so it must keep its dirty flag or a later remote import
            // could silently overwrite the un-uploaded value.
            val uploadedDirtyKeys = dirtyKeys()
            val body = "$now\n${exportSettingsJson()}"
            uploaded = runCatching {
                withDriveRetry {
                    kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                        context.contentResolver.openOutputStream(parsed, "wt")?.use { it.write(body.toByteArray()) }
                            ?: error("Couldn't open the Drive file for writing")
                        // Verify the write actually landed instead of trusting that
                        // close() completing without throwing means the bytes are really
                        // there -- some document providers can silently truncate or drop
                        // a buffered write under low storage or an interrupted upload,
                        // which previously would have reported success, advanced
                        // lastSyncMs, and cleared the dirty set for data that was never
                        // actually saved.
                        val verify = context.contentResolver.openInputStream(parsed)?.bufferedReader()?.readText()
                        if (verify != body) error("Upload didn't verify — the Drive file doesn't match what was written")
                    }
                }
                AppLog.log("Drive sync: uploaded settings")
                true
            }.onFailure {
                uploadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out writing the Drive file" else it.message ?: "Couldn't write the Drive file"
                AppLog.log("⚠ Drive sync: upload failed: ${it.message}")
            }.getOrElse { false }
            // Only claim "last synced" when the upload actually landed --
            // bumping it on a failure previously made the UI show "Last synced
            // just now" right next to "Sync failed", with no way to tell sync
            // had never succeeded.
            if (uploaded) {
                // Store lastSyncMs in the SAME clock domain as remoteTs (the
                // provider's COLUMN_LAST_MODIFIED), NOT this device's wall clock:
                // re-read the file's last-modified after the verified write so the
                // import guard (remoteTs > lastSyncMs()) is false for our OWN write
                // (no self-reimport next pass) and stays correct across devices
                // regardless of wall-clock skew. Fall back to the embedded `now`
                // header we just wrote when the provider exposes no
                // COLUMN_LAST_MODIFIED.
                val uploadedModifiedMs = runCatching {
                    if (android.provider.DocumentsContract.isDocumentUri(context, parsed)) {
                        val cursor = context.contentResolver.query(
                            parsed, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                            null, null, null,
                        )
                        cursor?.use {
                            if (it.moveToFirst()) it.getLong(0).takeIf { ts -> ts > 0 }
                            else null
                        }
                    } else null
                }.getOrNull()
                setLastSyncMs(uploadedModifiedMs ?: now)
                // Clear ONLY the keys this upload body actually carried, not the
                // whole set -- an edit made after the body snapshot (setters don't
                // hold driveSyncMutex) is still pending and must stay dirty so a
                // later remote import can't overwrite it.
                clearDirtyKeys(uploadedDirtyKeys)
            }
        }
        val error = uploadError ?: downloadError?.takeIf { remoteContent == null }
        // Persisted (not just returned) so a failure from the background
        // periodic worker -- which has no live ViewModel/UiState to update --
        // still shows up in Settings next time the app is opened, instead of
        // silently only ever reaching AppLog.
        setLastSyncError(error)
        return DriveSyncOutcome(
            // Match what was actually persisted above: report the OLD synced
            // time on total failure, not "now", so a caller that copies this
            // straight into UI state (AppViewModel does) can't show "synced
            // just now" next to a sync-failed error.
            ran = true, imported = imported, uploaded = uploaded, syncedAtMs = if (uploaded) now else lastSyncMs(),
            error = error,
        )
    }

    /** Runs [block] once, and if it throws, once more after a short delay --
     *  a single retry absorbs the kind of momentary blip (Drive app still
     *  waking up, a dropped packet) that would otherwise fail an entire sync
     *  pass outright. Real cancellation (the coroutine's own job being
     *  cancelled, NOT our own [DRIVE_IO_TIMEOUT_MS] timeout) is rethrown
     *  immediately instead of being swallowed into a pointless retry. */
    private suspend fun <T> withDriveRetry(block: suspend () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is kotlinx.coroutines.TimeoutCancellationException) {
            kotlinx.coroutines.delay(1000)
            block()
        } else {
            throw e
        }
    } catch (e: Exception) {
        kotlinx.coroutines.delay(1000)
        block()
    }

    // The car's last known address + coordinates, refreshed by the Location action
    // and rendered in the widget's location box.
    suspend fun widgetLocationAddress(widgetId: Int): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_addr")]?.takeIf { it.isNotBlank() }

    suspend fun setWidgetLocationAddress(widgetId: Int, address: String?) {
        editTracked {
            val key = stringPreferencesKey("widget_${widgetId}_addr")
            if (address.isNullOrBlank()) it.remove(key) else it[key] = address
        }
    }

    // --- Dual-column "hot spot" (pebble pinned under the car-info column) -----

    suspend fun hotspot(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("hotspot_$vin")]?.takeIf { it.isNotBlank() }

    suspend fun setHotspot(vin: String, section: String?) {
        editTracked {
            val key = stringPreferencesKey("hotspot_$vin")
            if (section.isNullOrBlank()) it.remove(key) else it[key] = section
        }
    }

    // --- Per-car powertrain override -------------------------------------

    /** Null means "not confirmed by the user yet" — the US Hyundai/Genesis API
     *  only distinguishes EV vs. gas, so the app asks the user to disambiguate
     *  hybrid/PHEV during car setup and stores their answer here; callers fall
     *  back to whatever the API-derived guess was when this is null. */
    suspend fun powertrain(vin: String): Powertrain? =
        context.settingsDataStore.data.first()[stringPreferencesKey("ptrain_$vin")]
            ?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }

    suspend fun setPowertrain(vin: String, value: Powertrain) {
        editTracked { it[stringPreferencesKey("ptrain_$vin")] = value.name }
    }

    // Remaining global appearance setters (theme/font/dynamic-color/palette):
    // each stores its enum's name() (or, for dynamicColor, a "true"/"false"
    // string) under its fixed Keys.* entry; decoding happens once, centrally,
    // in the `appearance` Flow above.
    suspend fun setThemeMode(mode: ThemeMode) {
        editTracked { it[Keys.THEME] = mode.name }
    }

    suspend fun setFontChoice(choice: FontChoice) {
        editTracked { it[Keys.FONT] = choice.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        editTracked { it[Keys.DYNAMIC] = enabled.toString() }
    }

    suspend fun setColorPalette(palette: ColorPalette) {
        editTracked { it[Keys.PALETTE] = palette.name }
    }

    // --- Per-car climate settings + presets ------------------------------

    private val climateJson = Json { ignoreUnknownKeys = true }
    private val presetListSerializer = ListSerializer(ClimatePreset.serializer())

    /** Last-used climate settings for a car, restored when the pebble reopens. */
    suspend fun savedClimate(vin: String): ClimateRequest? {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("climate_$vin")] ?: return null
        return runCatching { climateJson.decodeFromString(ClimateRequest.serializer(), raw) }.getOrNull()
    }

    suspend fun saveClimate(vin: String, req: ClimateRequest) {
        editTracked {
            it[stringPreferencesKey("climate_$vin")] = climateJson.encodeToString(ClimateRequest.serializer(), req)
        }
    }

    /** User-named climate presets for a car. */
    suspend fun climatePresets(vin: String): List<ClimatePreset> {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("climate_presets_$vin")] ?: return emptyList()
        return runCatching { climateJson.decodeFromString(presetListSerializer, raw) }.getOrElse { emptyList() }
    }

    /** Insert-or-replace by id: the whole preset list is re-read, decoded, the
     *  matching entry (by [ClimatePreset.id]) is replaced in place if found or
     *  appended if not, then the entire list is re-encoded and written back as
     *  one JSON string — there's no partial-update of a single preset within
     *  the stored JSON, the whole array is always rewritten. */
    suspend fun saveClimatePreset(vin: String, preset: ClimatePreset) {
        val existing = climatePresets(vin).toMutableList()
        val idx = existing.indexOfFirst { it.id == preset.id }
        if (idx >= 0) existing[idx] = preset else existing.add(preset)
        editTracked {
            it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, existing)
        }
    }

    suspend fun deleteClimatePreset(vin: String, id: String) {
        val updated = climatePresets(vin).filter { it.id != id }
        editTracked {
            it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, updated)
        }
    }

    /** Persist a full, reordered preset list for a car. */
    suspend fun setClimatePresets(vin: String, presets: List<ClimatePreset>) {
        editTracked {
            it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, presets)
        }
    }

    // --- Custom colour palettes ------------------------------------------

    private val paletteJson = Json { ignoreUnknownKeys = true }
    private val paletteListSerializer = ListSerializer(CustomPaletteData.serializer())

    private suspend fun readCustomPalettes(): List<CustomPaletteData> {
        val raw = context.settingsDataStore.data.first()[Keys.CUSTOM_PALETTES] ?: return emptyList()
        return runCatching { paletteJson.decodeFromString(paletteListSerializer, raw) }.getOrElse { emptyList() }
    }

    /** Insert or replace a custom palette by id. */
    suspend fun saveCustomPalette(palette: CustomPaletteData) {
        val updated = readCustomPalettes().filter { it.id != palette.id } + palette
        editTracked {
            it[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
        }
    }

    /** Remove a custom palette; clears the active id if it matches. */
    suspend fun deleteCustomPalette(id: String) {
        val updated = readCustomPalettes().filter { it.id != id }
        editTracked { prefs ->
            prefs[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
            if (prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID] == id) prefs.remove(Keys.ACTIVE_CUSTOM_PALETTE_ID)
        }
    }

    /** Set which custom palette is active (null = use a built-in palette). */
    suspend fun setActiveCustomPaletteId(id: String?) {
        editTracked {
            if (id == null) it.remove(Keys.ACTIVE_CUSTOM_PALETTE_ID)
            else it[Keys.ACTIVE_CUSTOM_PALETTE_ID] = id
        }
    }

    private val carPaletteSerializer = MapSerializer(String.serializer(), String.serializer())

    /** Set or clear a per-car custom palette override (null clears it → use global). */
    suspend fun setCarPaletteId(vin: String, paletteId: String?) {
        val current = context.settingsDataStore.data.first()[Keys.CAR_PALETTE_IDS]?.let { json ->
            runCatching { paletteJson.decodeFromString(carPaletteSerializer, json) }.getOrElse { emptyMap() }
        } ?: emptyMap()
        val updated = if (paletteId == null) current - vin else current + (vin to paletteId)
        editTracked {
            if (updated.isEmpty()) it.remove(Keys.CAR_PALETTE_IDS)
            else it[Keys.CAR_PALETTE_IDS] = paletteJson.encodeToString(carPaletteSerializer, updated)
        }
    }

    /** Clear all per-car palette overrides at once (e.g. when reverting to dynamic color). */
    suspend fun clearAllCarPaletteIds() {
        editTracked { it.remove(Keys.CAR_PALETTE_IDS) }
    }

    /** Serialise all custom palettes to a JSON string for export/share. */
    suspend fun exportPalettesJson(): String =
        context.settingsDataStore.data.first()[Keys.CUSTOM_PALETTES] ?: "[]"

    /**
     * Merge custom palettes parsed from [json] into the saved set (new ids only).
     * Returns an error message on failure, or null on success.
     */
    suspend fun importPalettesJson(json: String): String? {
        val parsed = runCatching { paletteJson.decodeFromString(paletteListSerializer, json) }
            .getOrElse { return "Invalid palette file" }
        val existing = readCustomPalettes()
        val merged = existing + parsed.filter { new -> existing.none { it.id == new.id } }
        editTracked {
            it[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, merged)
        }
        return null
    }

    // --- Full settings backup --------------------------------------------

    private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** The settings-backup format version. The format is a flat key-value bag,
     *  so an older client reading a newer backup is normally fine (unrecognized
     *  keys are simply ignored — ignoreUnknownKeys); bump this only if a future
     *  change stops being purely additive (a renamed/restructured key an older
     *  client would misinterpret rather than just skip), so old clients can
     *  detect and refuse it instead of silently importing something wrong. */
    private val BACKUP_VERSION = 1

    /** Preference keys that describe THIS device's own Drive-sync wiring (a
     *  content:// URI this app instance was granted permission for, local
     *  bookkeeping of when it last synced, its own Wi-Fi-only preference, and
     *  which keys it's changed locally since its last sync) — never portable, so
     *  never included in or restored from a settings backup. A tablet that's
     *  Wi-Fi-only and a phone with unlimited data may reasonably want different
     *  choices here, same as the Drive URI itself. */
    private val DEVICE_LOCAL_KEYS = setOf("sync_uri", "sync_last_ms", "sync_last_error", "sync_wifi", "sync_dirty_keys")

    /**
     * Wraps a settings mutation to record which preference keys it actually
     * changed into the "dirty" set — the keys this device has touched locally
     * since its own last successful Drive sync. [performDriveSync] protects
     * these from being overwritten by an incoming remote file, so a local edit
     * that hasn't been uploaded yet is never silently lost (field-level merge
     * instead of one whole-file last-write-wins).
     *
     * NOT used by [mergeSettingsJson] — accepting a value FROM the remote file
     * must not re-mark that same key as a pending local change, or it would
     * never propagate back out to a third device.
     */
    private suspend fun editTracked(mutate: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { prefs ->
            val before = HashMap(prefs.asMap())
            mutate(prefs)
            val after = prefs.asMap()
            val touched = mutableSetOf<String>()
            after.forEach { (k, v) -> if (before[k] != v) touched += k.name }
            before.keys.forEach { k -> if (k.name !in after.keys.map { it.name }) touched += k.name }
            touched.removeAll(DEVICE_LOCAL_KEYS)
            if (touched.isNotEmpty()) {
                val dirtyKey = stringPreferencesKey("sync_dirty_keys")
                val existing = prefs[dirtyKey]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                prefs[dirtyKey] = (existing + touched).joinToString(",")
            }
        }
    }

    private suspend fun dirtyKeys(): Set<String> =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_dirty_keys")]
            ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    /** Clear [keys] from the dirty set via set-difference, leaving any key
     *  marked dirty after the calling upload's body was snapshotted still
     *  pending. Done inside a single edit{} so a concurrent [editTracked] can't
     *  race between our read and write; if nothing dirty remains the key is
     *  removed entirely. */
    private suspend fun clearDirtyKeys(keys: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            val dirtyKey = stringPreferencesKey("sync_dirty_keys")
            val remaining = (prefs[dirtyKey]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) - keys
            if (remaining.isEmpty()) prefs.remove(dirtyKey) else prefs[dirtyKey] = remaining.joinToString(",")
        }
    }

    /**
     * Export every app preference (theme, colours and custom palettes, weather,
     * notifications, tiles, per-car config…) as one portable JSON backup. Values
     * keep their type (string or boolean) so a re-import restores them exactly.
     * Note: account credentials live in a separate store and are never included.
     *
     * Per-car photos ([encodeSyncPhotos]) are embedded as a separate top-level
     * "photos" object rather than folded into "prefs" like everything else --
     * an `img_$vin` pref pointing at a local file path used to sync as just
     * that path string, which meant nothing on a second device (no such file
     * there), so a synced photo silently never actually appeared anywhere but
     * the device it was set on.
     */
    suspend fun exportSettingsJson(): String {
        val prefs = context.settingsDataStore.data.first()
        val entries = buildJsonObject {
            prefs.asMap().forEach { (key, value) ->
                // sync_uri/sync_last_ms are this DEVICE's own Drive permission grant
                // and sync bookkeeping, not a portable app preference — including
                // them would make every import overwrite the receiving device's
                // working Drive URI with one it has no permission to use, silently
                // breaking its own sync (or corrupting a plain settings-restore that
                // has nothing to do with Drive at all).
                if (key.name in DEVICE_LOCAL_KEYS) return@forEach
                // A local-file img_ path (an absolute "/..." path) is meaningless on
                // any other device, so it never travels in prefs -- only the base64
                // "photos" channel below carries local photos. Remote-URL img_
                // values (not starting with "/") still ride in prefs normally.
                if (key.name.startsWith("img_") && value is String && value.startsWith("/")) return@forEach
                when (value) {
                    is Boolean -> put(key.name, JsonPrimitive(value))
                    is String -> put(key.name, JsonPrimitive(value))
                    else -> put(key.name, JsonPrimitive(value.toString()))
                }
            }
        }
        // Tombstones: keys this device has changed (they're in the dirty set) but
        // that no longer exist in prefs are deletions -- emit them so other devices
        // converge on the removal instead of the deleted key silently coming back
        // from whichever device still has it. DEVICE_LOCAL_KEYS are never portable.
        // A re-added key reappears in prefs and so naturally drops out of _removed
        // on the next export.
        val presentNames = prefs.asMap().keys.map { it.name }.toSet()
        val removed = (dirtyKeys() - presentNames - DEVICE_LOCAL_KEYS)
        val photos = encodeSyncPhotos(prefs)
        val root = buildJsonObject {
            put("_format", JsonPrimitive("bloo-settings"))
            put("_version", JsonPrimitive(BACKUP_VERSION))
            put("prefs", entries)
            if (photos.isNotEmpty()) put("photos", JsonObject(photos))
            if (removed.isNotEmpty()) put("_removed", buildJsonArray { removed.forEach { add(JsonPrimitive(it)) } })
        }
        return backupJson.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Restore settings from a backup produced by [exportSettingsJson], overwriting
     * any matching keys. Returns an error message on failure, or null on success.
     * Uses [editTracked] — a manual restore is a deliberate local change, so if
     * this device also has Drive auto-sync configured, the restored values are
     * the ones the next sync should push out, not silently discard. Embedded
     * photos ([applySyncPhotos]) are written to local storage first (plain
     * suspend file IO, not a DataStore edit), then their resulting `img_$vin`
     * paths are folded into the SAME editTracked mutation as the rest of the
     * prefs, so they're marked dirty for re-upload exactly like everything else.
     */
    suspend fun importSettingsJson(json: String): String? {
        val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }
            .getOrElse { return "Invalid settings file" }
        if (root["_format"]?.jsonPrimitive?.contentOrNull != "bloo-settings") {
            return "Not a Bloo settings backup"
        }
        val version = root["_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) {
            return "This backup was made with a newer version of Bloo — update the app first"
        }
        val prefs = root["prefs"]?.jsonObject ?: return "Settings file has no data"
        val removed = root["_removed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val photoPaths = applySyncPhotos(root["photos"]?.jsonObject)
        editTracked { mut ->
            prefs.forEach { (name, element) ->
                // Never accept this device's own Drive URI/bookkeeping from a backup —
                // exportSettingsJson no longer writes these, but reject them here too
                // in case an older export (or a hand-edited file) still has them.
                if (name in DEVICE_LOCAL_KEYS) return@forEach
                val prim = (element as? JsonPrimitive) ?: return@forEach
                when {
                    // A real JSON string (e.g. "DARK", "true") → keep as a string pref.
                    prim.isString -> mut[stringPreferencesKey(name)] = prim.content
                    // A bare JSON boolean → a boolean pref (notifications, alerts, …).
                    prim.booleanOrNull != null -> mut[booleanPreferencesKey(name)] = prim.booleanOrNull!!
                    // Anything else (a bare number) — every numeric pref here is
                    // stored as a string, so coerce it back to one.
                    else -> mut[stringPreferencesKey(name)] = prim.content
                }
            }
            photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
            // Propagate deletions: a key tombstoned on the source device is removed
            // here too (both key types, since this file mixes string/boolean prefs
            // under the same name) so a deletion converges instead of the key
            // resurrecting from this device's stale copy. DEVICE_LOCAL_KEYS are
            // never touched by a backup.
            removed.forEach { name ->
                if (name in DEVICE_LOCAL_KEYS) return@forEach
                mut.remove(stringPreferencesKey(name))
                mut.remove(booleanPreferencesKey(name))
            }
        }
        return null
    }

    /** Longest edge a synced photo is downscaled to before base64-embedding --
     *  small enough that even several cars' photos keep the whole settings
     *  backup a reasonable size for repeated auto-sync uploads, still sharp
     *  enough for the hero card / cover-screen tile it's actually shown at. */
    private val SYNCED_PHOTO_MAX_DIM = 640

    /** Reads every `img_$vin` pref that points at a local file (a remote URL
     *  needs no embedding -- it already loads the same way on any device) and
     *  returns `{vin: base64 JPEG}` for [exportSettingsJson]'s "photos" field.
     *  Downscales to [SYNCED_PHOTO_MAX_DIM] first; a corrupt/missing file for
     *  one car is skipped rather than failing the whole export. */
    private fun encodeSyncPhotos(prefs: androidx.datastore.preferences.core.Preferences): Map<String, JsonPrimitive> =
        prefs.asMap().keys.mapNotNull { key ->
            if (!key.name.startsWith("img_")) return@mapNotNull null
            val vin = key.name.removePrefix("img_")
            val path = prefs[stringPreferencesKey(key.name)]?.takeIf { it.startsWith("/") } ?: return@mapNotNull null
            val bytes = runCatching { downscaledJpegBytes(path) }.getOrNull() ?: return@mapNotNull null
            vin to JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP))
        }.toMap()

    /** Downscale-decodes [path] to at most [SYNCED_PHOTO_MAX_DIM] on its longest
     *  edge and re-encodes as a JPEG, without ever fully decoding the original
     *  at full resolution (bounds-only pass picks an `inSampleSize` first). */
    private fun downscaledJpegBytes(path: String): ByteArray? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > SYNCED_PHOTO_MAX_DIM * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = SYNCED_PHOTO_MAX_DIM.toFloat() / maxOf(decoded.width, decoded.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        val out = java.io.ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 78, out)
        return out.toByteArray()
    }

    /** Writes any embedded per-car photos from a backup's "photos" object to
     *  local storage (same `filesDir/cars/` directory the crop screen itself
     *  saves to), skipping any vin whose `img_$vin` key is in [protect] --
     *  an automatic Drive merge must not clobber a photo changed locally
     *  since the last successful sync, same reasoning as the plain pref
     *  merge in [mergeSettingsJson]. A fixed per-vin filename (not a fresh
     *  timestamped one) so repeated syncs overwrite in place rather than
     *  accumulating orphaned old photos on disk. Returns the vin -> new
     *  local path map for the caller to fold into whichever edit block
     *  (tracked or not) it's already running, so this lands in the SAME
     *  transaction as the rest of that import/merge instead of a separate one. */
    private fun applySyncPhotos(photos: JsonObject?, protect: Set<String> = emptySet()): Map<String, String> {
        if (photos == null) return emptyMap()
        val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
        return photos.mapNotNull { (vin, element) ->
            if ("img_$vin" in protect) return@mapNotNull null
            val b64 = (element as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            runCatching {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val file = java.io.File(dir, "car_${vin}_synced.jpg")
                file.writeBytes(bytes)
                vin to file.absolutePath
            }.getOrNull()
        }.toMap()
    }

    /**
     * Merge a Drive-downloaded settings file into local prefs for the AUTOMATIC
     * bidirectional sync: every key in [protect] (changed locally since our own
     * last successful sync, and not yet uploaded) keeps its current local value;
     * every other key is taken from remote. Unlike [importSettingsJson] this does
     * NOT go through [editTracked] — accepting a remote value must not re-mark
     * that key as a pending local change, or it would never finish converging.
     * Returns whether anything was actually applied.
     */
    private suspend fun mergeSettingsJson(json: String, protect: Set<String>): Boolean {
        val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
        if (root["_format"]?.jsonPrimitive?.contentOrNull != "bloo-settings") return false
        val version = root["_version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) {
            // A newer device wrote this — rather than misapply a format we don't
            // recognize, skip the import half this round (the upload half still
            // runs normally) and wait for this device to be updated.
            AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping import")
            return false
        }
        val prefs = root["prefs"]?.jsonObject ?: return false
        val removed = root["_removed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val photoPaths = applySyncPhotos(root["photos"]?.jsonObject, protect)
        context.settingsDataStore.edit { mut ->
            // Re-read the dirty set from THIS transaction's live prefs, not just the
            // protect snapshot taken before the pass started: a local edit that
            // landed between that snapshot and this edit block (setters don't hold
            // driveSyncMutex) would otherwise be clobbered by the incoming remote
            // value. Treat those live-dirty keys exactly like protect -- skip
            // writing and skip removing them.
            val liveDirty = mut[stringPreferencesKey("sync_dirty_keys")]
                ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val guarded = protect + liveDirty
            prefs.forEach { (name, element) ->
                if (name in DEVICE_LOCAL_KEYS || name in guarded) return@forEach
                val prim = (element as? JsonPrimitive) ?: return@forEach
                when {
                    prim.isString -> mut[stringPreferencesKey(name)] = prim.content
                    prim.booleanOrNull != null -> mut[booleanPreferencesKey(name)] = prim.booleanOrNull!!
                    else -> mut[stringPreferencesKey(name)] = prim.content
                }
            }
            photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
            // Propagate deletions from the remote file, but never remove a key we're
            // protecting (locally changed since our last sync, or live-dirty within
            // this transaction) or a device-local key -- same convergence reasoning
            // as importSettingsJson, both key types removed since names are shared.
            removed.forEach { name ->
                if (name in DEVICE_LOCAL_KEYS || name in guarded) return@forEach
                mut.remove(stringPreferencesKey(name))
                mut.remove(booleanPreferencesKey(name))
            }
        }
        return true
    }

    // --- Weather ---------------------------------------------------------

    /** Set or clear the weather location. Passing null lat/lon clears it. */
    suspend fun setWeatherLocation(lat: Double?, lon: Double?, label: String?) {
        editTracked {
            if (lat == null || lon == null) {
                it.remove(Keys.WEATHER_LAT)
                it.remove(Keys.WEATHER_LON)
                it.remove(Keys.WEATHER_LABEL)
            } else {
                it[Keys.WEATHER_LAT] = lat.toString()
                it[Keys.WEATHER_LON] = lon.toString()
                if (label.isNullOrBlank()) it.remove(Keys.WEATHER_LABEL) else it[Keys.WEATHER_LABEL] = label
            }
        }
    }

    /** Set the home weather location from this device's own last-known GPS
     *  fix, reverse-geocoded to a place label. Shared by the phone Settings
     *  screen's "My location" action and the watch's equivalent (relayed
     *  through WearPhoneService, since the watch has no weather fetch of its
     *  own). Returns false when no location is available (e.g. permission
     *  never granted on this device) so the caller can report that clearly. */
    suspend fun setWeatherFromDeviceLocation(): Boolean {
        val loc = runCatching {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER,
            ).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        }.getOrNull() ?: return false
        val label = runCatching {
            android.location.Geocoder(context, java.util.Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { a ->
                    listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea).distinct().joinToString(", ")
                }
        }.getOrNull() ?: "My location"
        setWeatherLocation(loc.latitude, loc.longitude, label)
        return true
    }
}
