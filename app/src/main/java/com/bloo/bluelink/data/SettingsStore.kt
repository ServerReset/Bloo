package com.bloo.bluelink.data

import androidx.compose.runtime.Immutable
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

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

/**
 * User-confirmed head-unit generation for a Hyundai/Genesis US vehicle --
 * the same GEN5W/ccNC split [com.bloo.bluelink.data.isGen5W] already infers
 * from the API's own `generation` field, made overridable the same way
 * [Powertrain] is: only Hyundai/Genesis US cars ever report a real
 * generation number (Kia US, every Canada brand, and Europe all resolve
 * [com.bloo.bluelink.data.isGen5W] to a fixed answer regardless of this
 * choice -- see that property's own doc), so this only has anything to
 * confirm for that same population.
 */
enum class VehiclePlatform { GEN5W, CCNC }

/** When the biometric app-lock re-engages after the app leaves the foreground. */
enum class LockTiming(val label: String) {
    OFF("Off"),
    IMMEDIATE("Immediate"),
    AFTER_1_MIN("1 min"),
    AFTER_5_MIN("5 min"),
    AFTER_10_MIN("10 min"),
}

/**
 * The wire-key form of a [LockTiming], matching the string vocabulary the watch stores and
 * [shouldRelockAfter] switches on. NOT the enum's persistence format -- LockTiming persists via
 * `.name` -- purely the bridge into the shared re-lock predicate so the phone and watch share
 * one copy of the timing thresholds. Exhaustive with no `else` on purpose: adding a LockTiming
 * value must fail to compile here until its key is chosen.
 */
val LockTiming.wireKey: String
    get() = when (this) {
        LockTiming.OFF -> "off"
        LockTiming.IMMEDIATE -> "immediate"
        LockTiming.AFTER_1_MIN -> "1min"
        LockTiming.AFTER_5_MIN -> "5min"
        LockTiming.AFTER_10_MIN -> "10min"
    }

/** Reorderable detail sections (pebbles), in their default order. */
// "climate" ahead of "ai": pre-heating/cooling the car before walking out to
// it is the single most common "glance and go" action this app exists for,
// while AI summary is a passive, network-dependent read -- the old order put
// a "Summarize" button ahead of every actual control on both phone and watch
// (the watch's tile order mirrors this list) for anyone who hasn't
// customized their section order.
val DEFAULT_SECTIONS = listOf("summary", "update", "controls", "charge", "climate", "ai", "info", "location", "weather", "trips", "diagnostics")

/**
 * Collapse key for the hero card's photo, so it rides the same per-car
 * collapsed-sections set every pebble uses -- persisted, per car, and carried by
 * Drive sync for free, instead of a parallel preference that would behave subtly
 * differently from every other collapse in the app.
 *
 * Deliberately NOT in [DEFAULT_SECTIONS]: it is not a reorderable pebble, and
 * sectionOrder() filters saved lists against that list, so this key can never leak
 * into the pebble-order UI. Nothing filters the COLLAPSED set, which is what lets it
 * round-trip.
 */
const val HERO_PHOTO_SECTION = "hero"

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
        val COVER_SETTINGS_HINT = stringPreferencesKey("cover_settings_hint_dismissed")
        val SHOW_SEARCH = stringPreferencesKey("show_search")
        val SEAMLESS_INSTALL_SHIZUKU = stringPreferencesKey("seamless_install_shizuku")
        val SETTINGS_AS_PAGE = stringPreferencesKey("settings_as_page")
        // Fractions (0f..1f) of the cover screen's own drag range, not raw dp -- the
        // physical cover display doesn't change size between sessions, but a fraction
        // still degrades gracefully if it ever did, where a raw dp coordinate could
        // clamp to a corner it wasn't actually dropped near. Kept OUT of the Appearance
        // bundle deliberately: that flow is collected by most of the app's UI (theme,
        // colors, ...), so writing to it on every drag delta -- which this needs to
        // survive a killed process, not just a rotation -- would recompose far more
        // than a floating circle's own position ever should.
        val SEARCH_BUBBLE_X = stringPreferencesKey("search_bubble_x")
        val SEARCH_BUBBLE_Y = stringPreferencesKey("search_bubble_y")
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

    /** @Immutable for the same reason as UiState: this is threaded through
     *  every screen and into CarThemeOverride on every car page, and while
     *  Compose infers it unstable (it holds Maps and Lists) nothing taking it
     *  can ever skip. All fields are vals and every collection in one is built
     *  fresh by the store, never edited in place. */
    @Immutable
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
        /** Show the search bubble on the car screen and the flip cover. On by
         *  default: search answers questions about the car and runs commands,
         *  so the screen showing the car is where it earns its place. Settings
         *  always has it regardless -- that is how you find a setting. */
        val showSearch: Boolean = true,
        /** Whether the flip-cover "open your phone for settings" hint has been
         *  dismissed once. Pure UI dust; roaming it to other devices is
         *  harmless (they may just never see the hint, which is fine). */
        val coverSettingsHintDismissed: Boolean = false,
        /** When on, this device installs downloaded updates silently via Shizuku
         *  (local ADB) instead of the tap-through system installer. Off by default;
         *  device-local capability (Shizuku may not be present on other devices), so
         *  it never roams via Drive sync (see SyncMerge.DEVICE_LOCAL_KEYS). */
        val seamlessInstallShizuku: Boolean = false,
        /** When on, Settings is reached by swiping past your last car in the garage's
         *  own pager instead of the floating gear button -- one continuous pager with
         *  Settings as its own extra page, rather than a separate screen you navigate
         *  to. Off by default (the floating button), matching every existing install's
         *  current behaviour; this only changes anything for someone who opts in. */
        val settingsAsPage: Boolean = false,
        /** Watch's own PIN lock enabled/timing -- a backup record only, mirrored
         *  from the watch. See [SettingsStore.Keys.WATCH_PIN_ENABLED]'s comment. */
        val watchPinLockEnabled: Boolean = false,
        val watchPinLockTiming: String = "immediate",
        /** Opt-in "liquid glass" appearance. Off by default = current look; when
         *  on, floating chrome and cards use real backdrop refraction (API 31+)
         *  or an enhanced-frosted fallback below that. */
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
                decodeJsonOr(paletteJson, paletteListSerializer, json, emptyList())
            } ?: emptyList(),
            activeCustomPaletteId = prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID],
            carCustomPaletteIds = prefs[Keys.CAR_PALETTE_IDS]?.let { json ->
                decodeJsonOr(paletteJson, MapSerializer(String.serializer(), String.serializer()), json, emptyMap())
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
            // Shared rule -- see FormatUtils.useFahrenheit for why this stopped being
            // written out here and on the watch separately.
            useFahrenheit = useFahrenheit(prefs[Keys.UNIT_SYSTEM]),
            watchPinLockEnabled = prefs[Keys.WATCH_PIN_ENABLED]?.toBooleanStrictOrNull() ?: false,
            watchPinLockTiming = prefs[Keys.WATCH_PIN_TIMING] ?: "immediate",
            pebbleOutline = prefs[Keys.PEBBLE_OUTLINE]?.toBooleanStrictOrNull() ?: false,
            coverSettingsHintDismissed = prefs[Keys.COVER_SETTINGS_HINT]?.toBooleanStrictOrNull() ?: false,
            showSearch = prefs[Keys.SHOW_SEARCH]?.toBooleanStrictOrNull() ?: true,
            seamlessInstallShizuku = prefs[Keys.SEAMLESS_INSTALL_SHIZUKU]?.toBooleanStrictOrNull() ?: false,
            settingsAsPage = prefs[Keys.SETTINGS_AS_PAGE]?.toBooleanStrictOrNull() ?: false,
        )
    }
        // Off the main thread. This is a ~40-field decode including two JSON parses (custom
        // palettes and per-car palette ids), and DataStore only guarantees the FILE read is off
        // main -- a map{} transform runs in the collector's context. Three separate collectors
        // subscribe to this on cold start, so it ran three times on the main thread while the
        // first frame was trying to draw.
        .flowOn(Dispatchers.Default)

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

    suspend fun setCoverSettingsHintDismissed(value: Boolean) {
        editTracked { it[Keys.COVER_SETTINGS_HINT] = value.toString() }
    }

    suspend fun setPebbleOutline(value: Boolean) {
        editTracked { it[Keys.PEBBLE_OUTLINE] = value.toString() }
    }

    suspend fun setShowSearch(value: Boolean) {
        editTracked { it[Keys.SHOW_SEARCH] = value.toString() }
    }

    suspend fun setSeamlessInstallShizuku(value: Boolean) {
        editTracked { it[Keys.SEAMLESS_INSTALL_SHIZUKU] = value.toString() }
    }

    suspend fun setSettingsAsPage(value: Boolean) {
        editTracked { it[Keys.SETTINGS_AS_PAGE] = value.toString() }
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
     *  gates the persistent foreground-service notification; [doorOpen],
     *  [running] and [unlocked] gate the "door left open" / "engine left
     *  running" / "left unlocked" alerts, each firing once the condition has
     *  held continuously for its paired *Minutes threshold (see
     *  [doorOpenSince]/[engineOnSince]/[unlockedSince] below, which track how
     *  long the condition has been true per car). */
    /** @Immutable -- same terms as [Appearance]. */
    @Immutable
    data class NotificationPrefs(
        val service: Boolean = true,
        val doorOpen: Boolean = true,
        val doorOpenMinutes: Int = 5,
        val running: Boolean = true,
        val runningMinutes: Int = 10,
        val unlocked: Boolean = true,
        val unlockedMinutes: Int = 10,
        /** The Live Update charging notification -- see
         *  [com.bloo.bluelink.data.LiveCharge]'s class doc for what that
         *  means precisely and how to verify it's actually working. */
        val charging: Boolean = true,
    )

    /** The single [NotificationPrefs] decode, shared by both the one-shot
     *  [notificationPrefs] read and the reactive [notifications] Flow so the two
     *  can't drift apart (they previously inlined the identical block twice). */
    private fun decodeNotificationPrefs(p: Preferences): NotificationPrefs =
        NotificationPrefs(
            service = p[booleanPreferencesKey("notify_service")] ?: true,
            doorOpen = p[booleanPreferencesKey("notify_door")] ?: true,
            doorOpenMinutes = p[stringPreferencesKey("notify_door_min")]?.toIntOrNull() ?: 5,
            running = p[booleanPreferencesKey("notify_running")] ?: true,
            runningMinutes = p[stringPreferencesKey("notify_running_min")]?.toIntOrNull() ?: 10,
            unlocked = p[booleanPreferencesKey("notify_unlocked")] ?: true,
            unlockedMinutes = p[stringPreferencesKey("notify_unlocked_min")]?.toIntOrNull() ?: 10,
            charging = p[booleanPreferencesKey("notify_charging")] ?: true,
        )

    /** One-shot read of [NotificationPrefs] (vs. the [notifications] Flow below,
     *  which stays subscribed) — used where a caller just needs the current
     *  values once, e.g. deciding whether to schedule a check at all. */
    suspend fun notificationPrefs(): NotificationPrefs =
        decodeNotificationPrefs(context.settingsDataStore.data.first())

    /** Reactive equivalent of [notificationPrefs] for UI that needs to update
     *  live when the user changes a toggle in Settings while the screen is open. */
    val notifications: Flow<NotificationPrefs> = context.settingsDataStore.data.map { p ->
        decodeNotificationPrefs(p)
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

    suspend fun setNotifyUnlocked(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_unlocked")] = v }.let {}

    suspend fun setUnlockedMinutes(v: Int) =
        editTracked { it[stringPreferencesKey("notify_unlocked_min")] = v.toString() }.let {}

    suspend fun setNotifyCharging(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_charging")] = v }.let {}

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

    /**
     * Where the user last parked the cover screen's floating search bubble, as
     * fractions (0f..1f) of its own drag range -- null until it has been dragged
     * at least once. A plain one-shot suspend read, not a collected Flow: the
     * bubble's own composable seeds itself from this once (see SearchLayer),
     * it does not need to react live to a value only that same composable ever
     * writes.
     */
    suspend fun searchBubblePosition(): Pair<Float, Float>? {
        val prefs = context.settingsDataStore.data.first()
        val x = prefs[Keys.SEARCH_BUBBLE_X]?.toFloatOrNull() ?: return null
        val y = prefs[Keys.SEARCH_BUBBLE_Y]?.toFloatOrNull() ?: return null
        return x to y
    }

    /** Persists the bubble's resting fractions -- called once per drag gesture
     *  (on release), not per frame, from SearchLayer's onDragEnd. */
    suspend fun setSearchBubblePosition(xFrac: Float, yFrac: Float) {
        editTracked {
            it[Keys.SEARCH_BUBBLE_X] = xFrac.toString()
            it[Keys.SEARCH_BUBBLE_Y] = yFrac.toString()
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

    suspend fun unlockedSince(vin: String): Long? =
        context.settingsDataStore.data.first()[stringPreferencesKey("unlocked_since_$vin")]?.toLongOrNull()

    suspend fun setUnlockedSince(vin: String, value: Long?) {
        editTracked {
            val k = stringPreferencesKey("unlocked_since_$vin")
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

    /**
     * Whether the user swiped away [vin]'s live charging bar during the CURRENT charging
     * session. Google's Live Updates guidance is explicit that a dismissed Live Update
     * must not be reposted, and this notification is otherwise re-posted every five
     * minutes for as long as the charge lasts.
     *
     * Persisted rather than kept in memory because the poller is a WorkManager job: the
     * process is routinely killed between ticks, so an in-memory flag would be forgotten
     * and the bar would come straight back — which is the behaviour this exists to stop.
     *
     * Device-local (see SyncMerge's prefix list): dismissing a notification on a phone
     * says nothing about what a tablet should show. Cleared when charging ends, so the
     * next session starts fresh rather than being permanently suppressed by one swipe.
     */
    suspend fun liveChargeDismissed(vin: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("live_dismissed_$vin")] ?: false

    suspend fun setLiveChargeDismissed(vin: String, value: Boolean) {
        editTracked { it[booleanPreferencesKey("live_dismissed_$vin")] = value }
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

    /** The chosen distance/temperature unit system ("imperial" default, or "metric"), for
     *  non-Compose callers that need it as a one-shot read rather than the [appearance] flow --
     *  e.g. CarAlerts building a notification string off the main thread. Mirrors [settingsMode]. */
    suspend fun unitSystem(): String =
        context.settingsDataStore.data.first()[Keys.UNIT_SYSTEM] ?: "imperial"

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

    suspend fun licensePlate(vin: String): String = licensePlate(vin, context.settingsDataStore.data.first())

    fun licensePlate(vin: String, p: Preferences): String =
        p[stringPreferencesKey("plate_$vin")] ?: ""

    suspend fun setLicensePlate(vin: String, value: String) {
        editTracked {
            val key = stringPreferencesKey("plate_$vin")
            if (value.isBlank()) it.remove(key) else it[key] = value.trim()
        }
    }

    suspend fun lastServiceMiles(vin: String): Int? = lastServiceMiles(vin, context.settingsDataStore.data.first())

    fun lastServiceMiles(vin: String, p: Preferences): Int? =
        p[stringPreferencesKey("svc_last_$vin")]?.toIntOrNull()

    suspend fun setLastServiceMiles(vin: String, value: Int?) {
        editTracked {
            val key = stringPreferencesKey("svc_last_$vin")
            if (value == null) it.remove(key) else it[key] = value.toString()
        }
    }

    suspend fun serviceIntervalMiles(vin: String): Int? = serviceIntervalMiles(vin, context.settingsDataStore.data.first())

    fun serviceIntervalMiles(vin: String, p: Preferences): Int? =
        p[stringPreferencesKey("svc_interval_$vin")]?.toIntOrNull()

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
    /**
     * ONE Preferences snapshot, for a caller about to read many keys at once.
     *
     * Every getter here inlines its own `data.first()`, which after the first read is served
     * from memory but is still a collect-and-cancel round trip on the DataStore actor, and
     * they are sequential suspends. `loadGarageInner` made twelve of them PER CAR -- 36 on a
     * three-car account, on the cold-start critical path, every one returning the identical
     * object -- and `refreshLocalCarConfig` did it again on every settings import.
     *
     * Pair this with the `Preferences`-taking overloads: read once, pass it down. Those
     * overloads exist so the KEY and the DEFAULT stay written exactly once, in the getter --
     * a caller that reached for the raw key itself would be the drift this store exists to
     * prevent.
     */
    suspend fun snapshot(): Preferences = context.settingsDataStore.data.first()

    suspend fun imageUrl(vin: String): String? = imageUrl(vin, context.settingsDataStore.data.first())

    fun imageUrl(vin: String, p: Preferences): String? =
        p[stringPreferencesKey("img_$vin")]?.takeIf { it.isNotBlank() }

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
    suspend fun seatConfig(vin: String): SeatConfig =
        seatConfig(vin, context.settingsDataStore.data.first())

    /**
     * Reads THIRTEEN keys plus an older grouped-flag format, which is exactly why it takes a
     * Preferences: thirteen reads for one car became thirteen DataStore round trips, and
     * loadGarage does this per car.
     */
    fun seatConfig(vin: String, p: Preferences): SeatConfig {
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

    suspend fun onboardingSeen(): Boolean = onboardingSeen(context.settingsDataStore.data.first())

    fun onboardingSeen(p: Preferences): Boolean = p[booleanPreferencesKey("onboarding_seen")] ?: false

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
    suspend fun sectionOrder(vin: String): List<String> =
        sectionOrder(vin, context.settingsDataStore.data.first())

    fun sectionOrder(vin: String, p: Preferences): List<String> {
        val saved = p[stringPreferencesKey("sections_$vin")]
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

    suspend fun collapsedSections(vin: String): Set<String> = collapsedSections(vin, context.settingsDataStore.data.first())

    fun collapsedSections(vin: String, p: Preferences): Set<String> =
        csv(p, "collapsed_$vin")

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

    suspend fun hiddenSections(vin: String): Set<String> = hiddenSections(vin, context.settingsDataStore.data.first())

    fun hiddenSections(vin: String, p: Preferences): Set<String> =
        csv(p, "hidden_$vin")

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
    suspend fun enabledShortcuts(): Set<String>? = enabledShortcuts(context.settingsDataStore.data.first())

    fun enabledShortcuts(p: Preferences): Set<String>? {
        val raw = p[stringPreferencesKey("enabled_shortcuts")] ?: return null
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
    suspend fun tileConfig(index: Int): Pair<String, String>? =
        tileConfig(index, context.settingsDataStore.data.first())

    fun tileConfig(index: Int, p: Preferences): Pair<String, String>? {
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
    suspend fun tileLabel(index: Int): String? = tileLabel(index, context.settingsDataStore.data.first())

    fun tileLabel(index: Int, p: Preferences): String? =
        p[stringPreferencesKey("tile_${index}_label")]?.takeIf { it.isNotBlank() }

    suspend fun setTileLabel(index: Int, label: String?) {
        editTracked {
            val k = stringPreferencesKey("tile_${index}_label")
            if (label.isNullOrBlank()) it.remove(k) else it[k] = label.trim()
        }
    }

    /** What the climate tile runs: "default", "smart", or a preset id. */
    suspend fun tileClimateTarget(index: Int): String = tileClimateTarget(index, context.settingsDataStore.data.first())

    fun tileClimateTarget(index: Int, p: Preferences): String =
        p[stringPreferencesKey("tile_${index}_climate")]
            ?.takeIf { it.isNotBlank() } ?: "default"

    suspend fun setTileClimateTarget(index: Int, target: String?) {
        editTracked {
            val k = stringPreferencesKey("tile_${index}_climate")
            if (target.isNullOrBlank()) it.remove(k) else it[k] = target
        }
    }

    /** When true, tiles run the command in the background; else they open the app. */
    suspend fun tileBackground(): Boolean = tileBackground(context.settingsDataStore.data.first())

    fun tileBackground(p: Preferences): Boolean =
        p[booleanPreferencesKey("tile_background")] ?: false

    suspend fun setTileBackground(value: Boolean) {
        editTracked { it[booleanPreferencesKey("tile_background")] = value }
    }

    /** When true, a tile kicks a throttled status refresh when it becomes visible,
     *  so its lock/climate state stays live (at some battery/rate-limit cost). */
    suspend fun tileLiveRefresh(): Boolean = tileLiveRefresh(context.settingsDataStore.data.first())

    fun tileLiveRefresh(p: Preferences): Boolean =
        p[booleanPreferencesKey("tile_live_refresh")] ?: false

    suspend fun setTileLiveRefresh(value: Boolean) {
        editTracked { it[booleanPreferencesKey("tile_live_refresh")] = value }
    }

    /** Last time a tile-driven refresh ran for [vin] (epoch ms), for throttling. */
    suspend fun tileRefreshedAt(vin: String): Long =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_refreshed_$vin")]?.toLongOrNull() ?: 0L

    suspend fun setTileRefreshedAt(vin: String, value: Long) {
        editTracked { it[stringPreferencesKey("tile_refreshed_$vin")] = value.toString() }
    }

    // --- Home-screen widgets (removed) ---------------------------------------
    //
    // The whole "widget_<id>_*" section is gone: WIDGET_KEY_SUFFIXES plus nineteen
    // suspend accessors (widgetConfig/setWidgetConfig/clearWidgetConfig,
    // widgetPendingAction, widgetRequireAuth, widgetPhotoBackground,
    // widgetBackgroundAlpha, widgetShowLocation, widgetPillShape, widgetLayoutMode,
    // widgetLocationAddress, and their setters). Every one had zero call sites in
    // any module and any file type -- checked individually, not in aggregate.
    //
    // This was supersession, not rot. Widget config moved to
    // widget/WidgetConfigStore.kt, which keeps one JSON blob per widget under
    // widget_cfg_$widgetId in a SEPARATE DataStore file (bloo_widget_config),
    // deliberately so a widget's layout doesn't roam to other devices via Drive
    // backup. Its Stored class carries the successors, renamed on the way:
    // photoBackground, pillShape, backgroundOpacity (was alpha), showMap (was
    // showLocation), priority (was layoutMode), plus infoFields/actions/vin.
    // CarWidget, WidgetConfigActivity and CarWidgetReceiver all use that store
    // exclusively.
    //
    // Three had no successor at all -- widgetRequireAuth (per-widget biometric
    // gating), widgetPendingAction, and widgetLocationAddress. Those are features
    // that were dropped rather than migrated, so this deletes the last trace of
    // their persistence layer. Recorded here because that is the one part of this
    // removal a reader might otherwise mistake for an accident.
    //
    // Not addressed here: an upgrading user's DataStore still holds their old
    // widget_<id>_* keys, and export/import/sync enumerate it generically via
    // prefs.asMap(), so those keys keep being round-tripped as inert data.
    // clearWidgetConfig was the only thing that could ever have purged them and it
    // was itself dead, so nothing is newly stranded by this commit -- it just makes
    // the situation legible. A one-time key sweep is a data-migration question,
    // separate from deleting unreachable code.

    /** Drive URI for auto-backup; null when not configured. */
    suspend fun syncUri(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_uri")]?.takeIf { it.isNotBlank() }

    /** A short, stable fingerprint of the ACTUAL Drive file this device is synced
     *  to, derived from the persisted document URI's unique id. Shown in Settings
     *  so two devices can eyeball whether they're on the SAME file: if the two
     *  fingerprints differ, they picked different files (Google Drive allows two
     *  files with the same name), which is the #1 reason settings/devices don't
     *  converge. Null when sync isn't set up. */
    suspend fun syncFileFingerprint(): String? {
        if (syncUri() == null) return null
        // The CONTENT-based file id (stored inside the Drive file as `_fileId`,
        // cached here device-local). Every device on the same file reads the same
        // value → the same short code. This replaces the old URI hash, which was
        // WRONG: a SAF content:// URI is assigned PER DEVICE by the OS, so the same
        // Drive file had different URIs (and different hashes) on two phones — the
        // exact "I picked the same file but the codes differ" bug. Null until the
        // first successful sync has read/minted the id.
        val id = context.settingsDataStore.data.first()[stringPreferencesKey("sync_file_id")] ?: return null
        // Short, human-comparable tag (first 6 hex of a SHA-256 of the full id) so
        // we never surface the raw UUID but two devices still match at a glance.
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return hash.take(3).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** The full content-based file id this device currently has cached, or null.
     *  Used by [performDriveSync] to decide whether to preserve the remote file's
     *  id or mint a new one. */
    private suspend fun syncFileId(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_file_id")]?.takeIf { it.isNotBlank() }

    private suspend fun setSyncFileId(id: String) {
        context.settingsDataStore.edit { it[stringPreferencesKey("sync_file_id")] = id }
    }

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

    // --- Device sync identity + registry (all device-local: see SyncMerge.DEVICE_LOCAL_KEYS) ---
    //
    // These describe THIS install's participation in the shared Drive file. They
    // never travel in the portable backup (they're in DEVICE_LOCAL_KEYS, so
    // editTracked never marks them dirty and buildExport never emits them). They're
    // written with the RAW DataStore (context.settingsDataStore.edit), NOT
    // editTracked, precisely so touching them can't pollute the dirty set or trip a
    // content-hash change.

    private val devicesJson = Json { ignoreUnknownKeys = true }
    private val deviceListSerializer = ListSerializer(SyncMerge.SyncDevice.serializer())

    /** A stable per-install id for this device in the sync registry, created once
     *  (lazily) and persisted. Not derived from any hardware id (privacy + it must
     *  survive a factory-reset-style reinstall as a NEW device, which a random UUID
     *  gives us for free). */
    suspend fun syncDeviceId(): String {
        val existing = context.settingsDataStore.data.first()[stringPreferencesKey("sync_device_id")]
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.settingsDataStore.edit { it[stringPreferencesKey("sync_device_id")] = fresh }
        return fresh
    }

    /** Friendly name shown in the "your devices" list. Defaults to the hardware
     *  model until the user renames it. */
    suspend fun syncDeviceName(): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_device_name")]?.takeIf { it.isNotBlank() }
            ?: Build.MODEL ?: "This device"

    suspend fun setSyncDeviceName(name: String) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_device_name")
            if (name.isBlank()) it.remove(k) else it[k] = name.trim()
        }
    }

    /** Hash of the portable content this device last saw or wrote (the change gate). */
    suspend fun syncLastHash(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_last_hash")]?.takeIf { it.isNotBlank() }

    suspend fun setSyncLastHash(hash: String?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_last_hash")
            if (hash.isNullOrBlank()) it.remove(k) else it[k] = hash
        }
    }

    /** Whether this device has ever completed a sync of the CURRENT file. False →
     *  the next pass full-adopts (join-adopt). Reset to false on a file switch. */
    suspend fun syncSyncedEver(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("sync_synced_ever")] ?: false

    suspend fun setSyncSyncedEver(value: Boolean) {
        context.settingsDataStore.edit {
            if (value) it[booleanPreferencesKey("sync_synced_ever")] = true
            else it.remove(booleanPreferencesKey("sync_synced_ever"))
        }
    }

    /** One-shot flag: the next sync pass force-adopts the file (used by
     *  "Pull from primary now"). Cleared by the pass that consumes it. */
    suspend fun syncPullPrimary(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("sync_pull_primary")] ?: false

    suspend fun setSyncPullPrimary(value: Boolean) {
        context.settingsDataStore.edit {
            if (value) it[booleanPreferencesKey("sync_pull_primary")] = true
            else it.remove(booleanPreferencesKey("sync_pull_primary"))
        }
    }

    /** Cached copy of the last-merged `devices` registry, for offline display in
     *  Settings (the file may not be reachable when Settings opens). */
    suspend fun syncedDevices(): List<SyncMerge.SyncDevice> {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("sync_devices_cache")] ?: return emptyList()
        return runCatching { devicesJson.decodeFromString(deviceListSerializer, raw) }.getOrElse { emptyList() }
    }

    private suspend fun setSyncedDevicesCache(devices: List<SyncMerge.SyncDevice>) {
        context.settingsDataStore.edit {
            it[stringPreferencesKey("sync_devices_cache")] = devicesJson.encodeToString(deviceListSerializer, devices)
        }
    }

    /** The primary device id (source of truth), cached device-local for display and
     *  written into the file on the next upload. Null = no primary chosen. */
    suspend fun syncPrimaryDeviceId(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_primary_cache")]?.takeIf { it.isNotBlank() }

    private suspend fun setSyncPrimaryCache(id: String?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_primary_cache")
            if (id.isNullOrBlank()) it.remove(k) else it[k] = id
        }
    }

    /** A primary designation made on THIS device that hasn't been uploaded yet.
     *
     *  Separate from [syncPrimaryDeviceId] because that pref carries two different
     *  meanings which must not be conflated: "what the file says" (cached for offline
     *  Settings display) and "what I want the file to say". Reading the cache as a write
     *  intent is what stopped the primary from ever changing -- see [performDriveSync]. */
    private suspend fun syncPrimaryPending(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_primary_pending")]?.takeIf { it.isNotBlank() }

    private suspend fun setSyncPrimaryPending(id: String?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_primary_pending")
            if (id.isNullOrBlank()) it.remove(k) else it[k] = id
        }
    }

    /** Designate the primary device (source of truth). Persists locally; the value
     *  is written into the Drive file on the next [performDriveSync] upload.
     *
     *  Records the choice TWICE, deliberately: as a pending write intent (consumed by the
     *  next successful upload) and in the display cache (so Settings reflects the tap
     *  immediately rather than after a round trip). */
    suspend fun setPrimaryDevice(id: String) {
        setSyncPrimaryPending(id)
        setSyncPrimaryCache(id)
    }

    /** Arm a one-shot force-adopt from the file (the "Pull from primary now" lever). */
    suspend fun requestPullFromPrimary() {
        setSyncPullPrimary(true)
    }

    /** Reset all per-file sync gate state — MUST be called when the sync target URI
     *  changes, or stale hash/synced-ever/lastSync/dirty from the OLD file would
     *  block adoption of and convergence with the NEW file. */
    suspend fun resetSyncStateForNewFile() {
        context.settingsDataStore.edit {
            it.remove(stringPreferencesKey("sync_last_hash"))
            it.remove(booleanPreferencesKey("sync_synced_ever"))
            it.remove(stringPreferencesKey("sync_last_ms"))
            it.remove(stringPreferencesKey("sync_dirty_keys"))
            it.remove(booleanPreferencesKey("sync_pull_primary"))
            it.remove(stringPreferencesKey("sync_devices_cache"))
            it.remove(stringPreferencesKey("sync_primary_cache"))
            // The pending designation too: it named a primary for the OLD file's device
            // registry, and re-asserting it against a different file's registry is exactly
            // the stale-state bug this function exists to prevent.
            it.remove(stringPreferencesKey("sync_primary_pending"))
            // Drop the cached file id too — the new file has its own (or will mint
            // one). Keeping the old id would show a stale/mismatched File ID.
            it.remove(stringPreferencesKey("sync_file_id"))
        }
    }

    /** This device's own registry entry, freshly stamped. [appVersion] is best-effort. */
    private suspend fun selfSyncDevice(nowMs: Long): SyncMerge.SyncDevice {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        return SyncMerge.SyncDevice(
            id = syncDeviceId(),
            name = syncDeviceName(),
            model = Build.MODEL ?: "",
            appVersion = appVersion,
            lastSeenMs = nowMs,
        )
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
        /** The merged device registry after this pass (for the ViewModel/Settings). */
        val devices: List<SyncMerge.SyncDevice> = emptyList(),
        /** The primary device id recorded in the file, or null if none. */
        val primaryDeviceId: String? = null,
        /** This device's own sync id (so the UI can mark "this device" / hide "make
         *  primary" on self without a second read). */
        val selfDeviceId: String? = null,
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
    /**
     * The last-modified time a Storage Access Framework document reports, in epoch millis, or
     * null when the URI is not a document URI, the provider returns nothing, or the query throws.
     *
     * performDriveSync reads this in two places -- the download gate and the upload's
     * self-write guard -- to compare in the PROVIDER's clock domain rather than the device's,
     * which is what keeps the sync skew-safe and free of self-reimport. The two reads were
     * byte-for-byte identical; this is that query, once. Never throws (runCatching), because a
     * flaky Drive provider must degrade to "unknown time", not crash a sync pass.
     */
    private fun providerLastModifiedMs(parsed: android.net.Uri): Long? = runCatching {
        if (android.provider.DocumentsContract.isDocumentUri(context, parsed)) {
            context.contentResolver.query(
                parsed, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null,
            )?.use { if (it.moveToFirst()) it.getLong(0).takeIf { ts -> ts > 0 } else null }
        } else null
    }.getOrNull()

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
        // Installed-base migration seed: `sync_synced_ever` is a brand-new key, so
        // it's false on every device that ALREADY synced this file under the old
        // (mtime-only) scheme. Without this, that device's first post-update pass
        // would see !syncedEver and full-adopt its OWN file, discarding any
        // not-yet-uploaded local edits. A device that has ever recorded a lastSyncMs
        // for this file is NOT a fresh joiner — mark it synced so it takes the normal
        // protected-merge path, not join-adopt.
        if (!syncSyncedEver() && lastSyncMs() > 0L) setSyncSyncedEver(true)
        // Check the file's actual last-modified time from Drive.
        val fileModifiedMs = providerLastModifiedMs(parsed)
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
                    // .use{} closes the InputStream (and its ParcelFileDescriptor);
                    // readText() alone does NOT close, leaking an FD to the Drive
                    // SAF provider on every sync pass. Still evaluates to String?.
                    context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                }
            }
        }.onFailure {
            downloadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out reading the Drive file" else it.message ?: "Couldn't read the Drive file"
        }.getOrNull()
        val remoteJson = remoteContent?.substringAfter('\n', "")?.takeIf { it.isNotBlank() }
        val remoteTs = fileModifiedMs ?: (remoteContent?.substringBefore('\n')?.toLongOrNull() ?: 0L)
        // The Drive-only metadata (content hash, primary, device registry). Absent
        // fields → null/empty (an old-client file, or the header/marker case).
        val remoteMeta = remoteJson?.let { SyncMerge.parseMeta(it) }
        val remoteHash = remoteMeta?.hash
        val remoteHasContent = remoteJson != null && SyncMerge.parseBackup(remoteJson) != null
        // Resolve the file's content-based id: the remote file's own id wins (so
        // every device converges on it), else what we've cached, else mint a fresh
        // one (this device is the first to stamp the file). Cache it so the File ID
        // shown in Settings is stable + identical across devices on this file.
        // Read the cached file id ONCE (was read twice back-to-back with nothing
        // writing between — each read is a full DataStore snapshot collect).
        val cachedFileId = syncFileId()
        val resolvedFileId = remoteMeta?.fileId ?: cachedFileId ?: java.util.UUID.randomUUID().toString()
        if (resolvedFileId != cachedFileId) setSyncFileId(resolvedFileId)

        // Adopt-mode + import-gate decision.
        val pullPrimary = syncPullPrimary()
        val syncedEver = syncSyncedEver()
        // Change gate: prefer the content HASH (skew-immune, and it self-detects a
        // no-op so two devices don't ping-pong re-imports); fall back to the file's
        // modified-time only when the file predates the hash (an un-updated client
        // last wrote it and dropped our additive keys).
        val gatePassed = if (remoteHash != null) remoteHash != syncLastHash() else remoteTs > lastSyncMs()
        // A device that has never synced THIS file, or an explicit "pull from
        // primary", FULLY adopts the file as source of truth (this is the fix for
        // "my other phone won't pull the primary's settings" — the old protected
        // merge kept a joining device's huge dirty set and adopted almost nothing).
        // Every other pass is a normal field-level protected merge.
        val fullAdopt = pullPrimary || !syncedEver
        val shouldImport = remoteHasContent && (pullPrimary || !syncedEver || gatePassed)
        var imported = false
        if (shouldImport && remoteJson != null) {
            imported = if (fullAdopt) {
                adoptSettingsJson(remoteJson)
            } else {
                // Protect anything WE'VE changed locally but haven't uploaded yet —
                // read the dirty set before this pass touches anything, so a merge
                // import can't accidentally protect keys it's about to import itself.
                mergeSettingsJson(remoteJson, protect = dirtyKeys())
            }
            if (imported) {
                AppLog.log(if (fullAdopt) "Drive sync: adopted settings from file" else "Drive sync: imported newer settings")
                // Record the content we just took, so this pass's own state matches
                // the file and the next pass's gate is a no-op (no self-reimport).
                if (remoteHash != null) setSyncLastHash(remoteHash)
                setSyncSyncedEver(true)
            }
        }
        // The pull-from-primary lever is one-shot: consume it once there was a REAL
        // chance to adopt, whether or not anything actually needed adopting -- so a
        // later normal merge doesn't keep re-adopting. That is NOT the same as
        // consuming it unconditionally: if this pass's download failed
        // (downloadError != null, remoteJson stayed null), there was never a real
        // chance -- shouldImport was false only because we couldn't read the file,
        // not because there was nothing to pull. Clearing the flag anyway silently
        // drops the user's explicit "pull from primary" request: the very next
        // sync (the periodic worker, hours later, or a plain "Sync now") would run
        // an ordinary protected merge instead, with nothing telling the user their
        // request never actually happened. `!syncedEver` right above doesn't have
        // this problem -- it's derived from persisted state, not a flag this
        // function clears itself, so a failed pass naturally retries it next time;
        // this one-shot flag needs the same self-healing property.
        if (pullPrimary && downloadError == null) setSyncPullPrimary(false)

        val now = System.currentTimeMillis()
        var uploadError: String? = null
        val uploaded: Boolean
        // Best-available registry/primary for the outcome even if the upload half
        // doesn't run (failed download) — the UI still updates from what we read.
        var outcomeDevices: List<SyncMerge.SyncDevice> = remoteMeta?.devices ?: emptyList()
        // Precedence, and the order matters: an un-uploaded designation made HERE wins, then
        // whatever the file says, and only then this device's cached copy.
        //
        // It used to read `syncPrimaryDeviceId() ?: remoteMeta?.primaryDeviceId` -- the local
        // CACHE ahead of the file. But that cache is also where every pass stores the value it
        // just wrote (see setSyncPrimaryCache below), so "the primary I once saw" was
        // indistinguishable from "the primary I am asking for", and each device re-asserted
        // its own copy forever. The primary could therefore never be MOVED: designate the
        // tablet on the tablet, it uploads primary=tablet, then the phone's next pass reads
        // that, ignores it in favour of its own cached primary=phone, and writes it back. Both
        // devices sit there each believing it is primary, and the user's choice silently
        // reverts. A pending intent is one-shot, so the file converges after one pass.
        val pendingPrimary = syncPrimaryPending()
        val primaryToWrite: String? = pendingPrimary ?: remoteMeta?.primaryDeviceId ?: syncPrimaryDeviceId()
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
            // Snapshot the portable content ONCE (post-import): its SHA-256 is both
            // the change gate written into the file AND, being computed over the
            // exact prefs/photos we upload, guarantees the file's `_hash` matches
            // its own content. Encoding the photos once here (not twice) keeps this
            // cheap despite the base64 work.
            val prefsSnapshot = context.settingsDataStore.data.first()
            val prefsMap: Map<String, Any> = prefsSnapshot.asMap().entries.associate { it.key.name to it.value }
            val photos = encodeSyncPhotos(prefsSnapshot).mapValues { it.value.content }
            // ONE set, used by both the hash and the body -- see portableContentHash's param
            // doc for why passing it to only one of them corrupts the change gate.
            val carriedTombstones = remoteJson?.let { SyncMerge.parseRemoved(it) } ?: emptySet()
            val localHash = SyncMerge.portableContentHash(
                prefsMap, uploadedDirtyKeys, photos, priorRemoved = carriedTombstones,
            )
            val self = selfSyncDevice(now)
            outcomeDevices = SyncMerge.mergeDevices(remoteMeta?.devices ?: emptyList(), self, now)
            val driveBody = SyncMerge.buildExportForDrive(
                prefs = prefsMap,
                dirtyKeys = uploadedDirtyKeys,
                photos = photos,
                hash = localHash,
                primaryDeviceId = primaryToWrite,
                selfDevice = self,
                knownDevices = remoteMeta?.devices ?: emptyList(),
                nowMs = now,
                fileId = resolvedFileId,
                // Carry the remote file's OWN tombstones forward. Without this a `_removed`
                // entry lived for exactly one upload: it is derived from the dirty set, and
                // clearDirtyKeys empties that on success, so the next push -- any unrelated edit,
                // ~2s later -- rebuilt the body without it. A peer that had not synced inside
                // that single window still held the deleted key, re-uploaded it, and the deletion
                // was undone on the device that made it.
                //
                // Read straight from remoteJson rather than through parseBackup, because this
                // runs on the UPLOAD half, which happens even when the import half was skipped
                // (nothing newer, or an unreadable prefs block) -- exactly the passes that still
                // have to keep republishing the tombstone.
                priorRemoved = carriedTombstones,
            )
            val body = "$now\n$driveBody"
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
                        val verify = context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
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
                // Keep the wall-clock lastSyncMs advancing IN PARALLEL with the hash
                // gate: it's the fallback gate for a file an un-updated client
                // overwrote (dropping `_hash`), so it must stay current or the
                // fallback breaks exactly when it's needed. Re-read the file's
                // last-modified so the fallback compares in the provider's clock
                // domain (no self-reimport, skew-safe), same as before.
                val uploadedModifiedMs = providerLastModifiedMs(parsed)
                setLastSyncMs(uploadedModifiedMs ?: now)
                // Clear ONLY the keys this upload body actually carried, not the
                // whole set -- an edit made after the body snapshot (setters don't
                // hold driveSyncMutex) is still pending and must stay dirty so a
                // later remote import can't overwrite it.
                clearDirtyKeys(uploadedDirtyKeys)
                // The content-hash self-write guard: next pass reads this exact hash
                // back and the gate is a no-op (mirrors the lastSyncMs self-guard).
                setSyncLastHash(localHash)
                setSyncSyncedEver(true)
                // Cache the registry + primary for offline Settings display.
                setSyncedDevicesCache(outcomeDevices)
                setSyncPrimaryCache(primaryToWrite)
                // Consume the one-shot designation -- but ONLY now, inside the successful-
                // upload branch. Clearing it any earlier (on read, or on a failed upload)
                // would drop the user's choice on the floor without it ever reaching the
                // file; from the next pass on, the file's own value governs.
                if (pendingPrimary != null) setSyncPrimaryPending(null)
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
            devices = outcomeDevices,
            primaryDeviceId = primaryToWrite,
            selfDeviceId = syncDeviceId(),
        )
    }

    /** Result of [testSyncRoundTrip]: [ok] plus a human-readable [message]
     *  describing exactly which step passed or failed, for a Settings "Test
     *  sync" diagnostic the user can run on a real device. */
    data class SyncTestResult(val ok: Boolean, val message: String)

    /**
     * A non-destructive end-to-end self-test of the Drive round-trip, for the
     * Settings "Test sync" button. Exercises the EXACT provider path
     * [performDriveSync] relies on — persisted permission, read, truncate-write,
     * write-verify, read-back — against the user's real configured file, but
     * writes the file's own current bytes back VERBATIM so nothing the user has
     * is changed. (A brand-new/empty file is written with a harmless one-line
     * marker that the very next real sync overwrites.)
     *
     * This is the honest answer to "does Drive sync actually work on THIS device
     * with THIS provider," which can't be proven by reading code alone: it
     * catches a lost/He-revoked permission grant, a provider that rejects the
     * "wt" truncate mode, or one that silently drops a write — the real-world
     * failure modes. It never touches the settings DataStore, never advances
     * lastSyncMs, and never clears the dirty set, so it's side-effect-free
     * beyond re-writing identical bytes.
     */
    suspend fun testSyncRoundTrip(): SyncTestResult {
        val uri = syncUri() ?: return SyncTestResult(false, "Drive sync isn't set up yet.")
        val parsed = android.net.Uri.parse(uri)
        // 1. Confirm we still hold a persisted read+write grant for this file.
        val granted = runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
            }
        }.getOrDefault(false)
        if (!granted) {
            return SyncTestResult(false, "Lost access to the Drive file — set up sync again.")
        }
        // Serialize with real syncs so the read-then-write-back can't interleave
        // with a concurrent performDriveSync writing different content.
        return driveSyncMutex.withLock {
            // 2. Read current bytes (an empty/new file reads as "" or null).
            val current = runCatching {
                withDriveRetry {
                    kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                        context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                    }
                }
            }.getOrElse { e ->
                val why = if (e is kotlinx.coroutines.TimeoutCancellationException) "timed out reading" else (e.message ?: "couldn't read")
                return@withLock SyncTestResult(false, "Couldn't read the Drive file ($why).")
            }
            // Write the SAME bytes back so user content is unchanged; only a
            // genuinely empty file gets a throwaway marker (overwritten by the
            // next real sync's upload).
            val payload = current?.takeIf { it.isNotEmpty() } ?: "bloo-sync-test"
            // 3. Truncate-write + 4. verify, exactly as performDriveSync does.
            val verified = runCatching {
                withDriveRetry {
                    kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                        context.contentResolver.openOutputStream(parsed, "wt")?.use { it.write(payload.toByteArray()) }
                            ?: error("couldn't open for writing")
                        val readBack = context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                        readBack == payload
                    }
                }
            }.getOrElse { e ->
                val why = if (e is kotlinx.coroutines.TimeoutCancellationException) "timed out writing" else (e.message ?: "write failed")
                return@withLock SyncTestResult(false, "Couldn't write the Drive file ($why).")
            }
            if (verified) {
                SyncTestResult(true, "Drive sync is working — read, wrote and verified the file successfully.")
            } else {
                SyncTestResult(false, "The write didn't verify — the provider may be dropping or truncating writes.")
            }
        }
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

    // widgetLocationAddress/setWidgetLocationAddress removed here -- the last two of
    // the dead widget accessors, sitting apart from the rest of the block. See the
    // tombstone at the old "Home-screen widgets" section for why all of them went.

    // --- Dual-column "hot spot" (pebble pinned under the car-info column) -----

    suspend fun hotspot(vin: String): String? = hotspot(vin, context.settingsDataStore.data.first())

    fun hotspot(vin: String, p: Preferences): String? =
        p[stringPreferencesKey("hotspot_$vin")]?.takeIf { it.isNotBlank() }

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
    suspend fun powertrain(vin: String): Powertrain? = powertrain(vin, context.settingsDataStore.data.first())

    fun powertrain(vin: String, p: Preferences): Powertrain? =
        p[stringPreferencesKey("ptrain_$vin")]
            ?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }

    suspend fun setPowertrain(vin: String, value: Powertrain) {
        editTracked { it[stringPreferencesKey("ptrain_$vin")] = value.name }
    }

    // --- Per-car head-unit generation override ----------------------------
    //
    // Same shape as the powertrain override right above -- null means "not
    // confirmed by the user yet", callers fall back to the API-derived guess
    // ([com.bloo.bluelink.data.isGen5W]) when this is null. See VehiclePlatform's
    // own doc for which vehicles this has anything real to confirm.

    suspend fun platform(vin: String): VehiclePlatform? = platform(vin, context.settingsDataStore.data.first())

    fun platform(vin: String, p: Preferences): VehiclePlatform? =
        p[stringPreferencesKey("platform_$vin")]
            ?.let { runCatching { VehiclePlatform.valueOf(it) }.getOrNull() }

    suspend fun setPlatform(vin: String, value: VehiclePlatform) {
        editTracked { it[stringPreferencesKey("platform_$vin")] = value.name }
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

    /** Shared decode-or-default for the repeated
     *  `runCatching { json.decodeFromString(serializer, raw) }.getOrElse { default }`
     *  pattern used to read JSON-encoded prefs — a corrupt/foreign/renamed stored
     *  value falls back to [default] rather than throwing. The [json] instance is
     *  passed in (climateJson vs paletteJson, both ignoreUnknownKeys=true but kept
     *  explicit per section) rather than hardcoded here. */
    private fun <T> decodeJsonOr(json: Json, serializer: DeserializationStrategy<T>, raw: String, default: T): T =
        runCatching { json.decodeFromString(serializer, raw) }.getOrElse { default }

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
    suspend fun climatePresets(vin: String): List<ClimatePreset> =
        climatePresets(vin, context.settingsDataStore.data.first())

    fun climatePresets(vin: String, p: Preferences): List<ClimatePreset> {
        val raw = p[stringPreferencesKey("climate_presets_$vin")] ?: return emptyList()
        return decodeJsonOr(climateJson, presetListSerializer, raw, emptyList())
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
        return decodeJsonOr(paletteJson, paletteListSerializer, raw, emptyList())
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
            decodeJsonOr(paletteJson, carPaletteSerializer, json, emptyMap())
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

    // exportPalettesJson() / importPalettesJson() were deleted here: no callers. Custom palettes
    // travel through the full settings backup and Drive sync like every other pref (they live
    // under Keys.CUSTOM_PALETTES, which the backup already carries), so a standalone
    // palette-only import/export was never wired to any UI. The helpers they used
    // (readCustomPalettes, paletteJson, paletteListSerializer) remain live for the per-palette
    // save/delete paths above.

    // --- Full settings backup --------------------------------------------

    private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** The settings-backup format version. The format is a flat key-value bag,
     *  so an older client reading a newer backup is normally fine (unrecognized
     *  keys are simply ignored — ignoreUnknownKeys); bump this only if a future
     *  change stops being purely additive (a renamed/restructured key an older
     *  client would misinterpret rather than just skip), so old clients can
     *  detect and refuse it instead of silently importing something wrong.
     *  Single source of truth lives in [SyncMerge] (the pure, testable core);
     *  this alias keeps the many in-class references reading by simple name. */
    private val BACKUP_VERSION = SyncMerge.BACKUP_VERSION

    /** Preference keys that describe THIS device's own Drive-sync wiring (a
     *  content:// URI this app instance was granted permission for, local
     *  bookkeeping of when it last synced, its own Wi-Fi-only preference, and
     *  which keys it's changed locally since its last sync) — never portable, so
     *  never included in or restored from a settings backup. A tablet that's
     *  Wi-Fi-only and a phone with unlimited data may reasonably want different
     *  choices here, same as the Drive URI itself. Defined in [SyncMerge] so the
     *  pure export/merge core and this Context-bound store can't drift apart. */
    private val DEVICE_LOCAL_KEYS = SyncMerge.DEVICE_LOCAL_KEYS

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
            // Build the after-key-name set ONCE (was rebuilt via after.keys.map{}
            // inside this loop → O(n²) + n list allocations on every settings write,
            // which now fires the auto-push path). Value-identical.
            val afterNames = HashSet<String>(after.size)
            after.keys.forEach { afterNames += it.name }
            before.keys.forEach { k -> if (k.name !in afterNames) touched += k.name }
            // Strip BOTH the exact device-local keys AND the per-VIN device-local
            // prefixes (see SyncMerge.DEVICE_LOCAL_PREFIXES for the list -- naming them
            // here just meant this comment fell behind it), matching what
            // the export/hash already exclude via isDeviceLocal — otherwise those
            // transient runtime stamps land in the dirty set and every alert/tile tick
            // fires a redundant full Drive round-trip (the hash is unchanged, so no data
            // corrupts, but it's needless background I/O the prefix design meant to stop).
            touched.removeAll { com.bloo.bluelink.data.SyncMerge.isDeviceLocal(it) }
            if (touched.isNotEmpty()) {
                val dirtyKey = stringPreferencesKey("sync_dirty_keys")
                val existing = prefs.dirtyKeySet()
                prefs[dirtyKey] = (existing + touched).joinToString(",")
            }
        }
    }

    /** Decode the CSV-encoded "sync_dirty_keys" pref into a Set, dropping blanks
     *  (an unset/empty value → empty set). Shared by every site that reads the
     *  dirty set — the tracked-edit writer, [dirtyKeys], [clearDirtyKeys], and the
     *  live-dirty re-read in [mergeSettingsJson] — so they can't split it
     *  inconsistently. */
    private fun Preferences.dirtyKeySet(): Set<String> =
        this[stringPreferencesKey("sync_dirty_keys")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private suspend fun dirtyKeys(): Set<String> =
        context.settingsDataStore.data.first().dirtyKeySet()

    /** Reactive view of the dirty set — emits whenever a tracked setting changes
     *  (every [editTracked] that touches a portable key appends to it). The
     *  ViewModel observes this to auto-push to Drive shortly after ANY change
     *  (setting toggle, pebble/section reorder, per-car config…), so sync feels
     *  automatic instead of only firing on a refresh or the periodic worker.
     *  Emits the empty set once the last upload clears it.
     *
     *  Deduplicated HERE, on the key set *and a fingerprint of those keys' current values*,
     *  rather than by a plain `distinctUntilChanged()` at the collector. That is the whole
     *  point: the dirty set is a lossy projection of "something changed", so editing the
     *  same key twice leaves it byte-identical. A collector deduplicating on the set alone
     *  therefore saw no second change -- which is fine while the first push is still pending
     *  (it uploads current values anyway), but not when that push FAILED: the set stayed
     *  `{k}`, the re-edit of `k` produced `{k}` again, no emission, and the change sat
     *  unsynced until a data refresh or the 2-hour worker. The value fingerprint restores
     *  the promise the first sentence above makes.
     *
     *  Fingerprinted by hash, not by retaining the values: `distinctUntilChanged` holds its
     *  last value for comparison, and dirty values include multi-kilobyte JSON blobs
     *  (climate presets, custom palettes). A hash collision would suppress one emission --
     *  a delayed sync, backstopped by the periodic worker -- not a wrong one. */
    val dirtyKeysFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { prefs ->
            val keys = prefs.dirtyKeySet()
            // One name -> value map, not a scan per dirty key. Tombstoned keys are absent
            // from prefs and fingerprint as null, so a delete and a later restore of the
            // same key are correctly distinct.
            val byName = prefs.asMap().entries.associate { it.key.name to it.value }
            // NUL separator below, and written as the ESCAPE rather than the character. A pref
            // value can hold any printable text (names, JSON blobs, file paths), so a space or
            // comma separator would let two different key/value sets fingerprint alike; NUL
            // cannot occur in one. Kotlin also accepts the raw byte, which is the trap: it
            // compiles, and then grep classifies this whole file as binary and prints no
            // matching lines at all. tools/check-control-chars.py now fails on it.
            keys to keys.sorted().joinToString("\u0000") { "$it=${byName[it]}" }.hashCode()
        }
        .distinctUntilChanged()
        .map { it.first }

    /** Clear [keys] from the dirty set via set-difference, leaving any key
     *  marked dirty after the calling upload's body was snapshotted still
     *  pending. Done inside a single edit{} so a concurrent [editTracked] can't
     *  race between our read and write; if nothing dirty remains the key is
     *  removed entirely. */
    private suspend fun clearDirtyKeys(keys: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            val dirtyKey = stringPreferencesKey("sync_dirty_keys")
            val remaining = prefs.dirtyKeySet() - keys
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
        // Everything that decides WHICH keys/tombstones travel and how each value
        // is encoded lives in the pure, unit-tested [SyncMerge.buildExport]: the
        // DEVICE_LOCAL_KEYS skip, the local-file img_ path skip, the boolean/
        // string/coerced-toString typing, and the `_removed` tombstone set
        // (dirty keys no longer present, minus device-local). This method only
        // does the two Android-bound things buildExport can't: snapshot the typed
        // Preferences into a plain map, and base64-encode local car photos (which
        // needs android.graphics.Bitmap — see [encodeSyncPhotos]).
        val prefsMap: Map<String, Any> = prefs.asMap().entries.associate { it.key.name to it.value }
        val photos = encodeSyncPhotos(prefs).mapValues { it.value.content }
        // Read the dirty set from the snapshot we already hold (was a second
        // .data.first() via dirtyKeys()) — same value, one fewer collect.
        return SyncMerge.buildExport(prefsMap, prefs.dirtyKeySet(), photos)
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
        // `as? JsonPrimitive` / `as? JsonObject`, never `?.jsonPrimitive` / `?.jsonObject`.
        // The kotlinx accessors THROW IllegalArgumentException when the element is not of
        // that kind, and these twelve guards (three functions × four keys) exist precisely
        // to vet a HAND-EDITABLE, version-skewed file — the one place where `_format`
        // plausibly arrives as an object, or `prefs` as an array. Throwing out of a function
        // documented to *return an error message* (and out of two documented to return
        // false) turned "this is not a Bloo backup" into a crash. [SyncMerge.parseBackup]
        // already vets the identical keys with safe casts and promises "never throws on a
        // hand-edited or version-skewed file"; these disagreed with it.
        if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") {
            return "Not a Bloo settings backup"
        }
        val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) {
            return "This backup was made with a newer version of Bloo — update the app first"
        }
        if ((root["prefs"] as? JsonObject) == null) return "Settings file has no data"
        // Which keys to put (by type) and which to tombstone is decided by the
        // pure, unit-tested [SyncMerge.parseBackup]: real JSON strings and bare
        // numbers become string prefs, bare booleans become boolean prefs, and
        // `_removed` becomes the remove set — all with DEVICE_LOCAL_KEYS excluded.
        // The four guards above already validated format/version/prefs, so this is
        // non-null; the ?: keeps the same "no data" message defensively.
        val plan = SyncMerge.parseBackup(json) ?: return "Settings file has no data"
        val photoPaths = applySyncPhotos(root["photos"] as? JsonObject)
        editTracked { mut ->
            plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
            plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
            photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
            // Propagate deletions: a key tombstoned on the source device is removed
            // here too (both key types, since this file mixes string/boolean prefs
            // under the same name) so a deletion converges instead of the key
            // resurrecting from this device's stale copy. DEVICE_LOCAL_KEYS are
            // already excluded by parseBackup.
            plan.removes.forEach { name ->
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

    /** How recently a file in `cars/` must have been written to be spared by
     *  [pruneOrphanPhotos]. Only guards the crop screen's write-file-then-write-pref
     *  window, which is sub-millisecond; ten minutes is absurdly generous on purpose,
     *  because the cost of waiting is one stale file until the next launch and the cost
     *  of being wrong is deleting the photo the user just chose. */
    private val MIN_ORPHAN_AGE_MS = 10 * 60 * 1000L

    /** Reads every `img_$vin` pref that points at a local file (a remote URL
     *  needs no embedding -- it already loads the same way on any device) and
     *  returns `{vin: base64 JPEG}` for [exportSettingsJson]'s "photos" field.
     *  Downscales to [SYNCED_PHOTO_MAX_DIM] first; a corrupt/missing file for
     *  one car is skipped rather than failing the whole export.
     *
     *  Memoized on the file's identity (see [syncPhotoCache]) because this is not the
     *  once-per-manual-export call it looks like: it runs on every Drive sync pass, and
     *  auto-push fires one of those ~2s after ANY tracked pref edit. Dragging pebbles or
     *  nudging a slider therefore paid a full decode + rescale + JPEG re-compress +
     *  base64 for every car, to produce bytes identical to last time. */
    private fun encodeSyncPhotos(prefs: androidx.datastore.preferences.core.Preferences): Map<String, JsonPrimitive> =
        prefs.asMap().keys.mapNotNull { key ->
            if (!key.name.startsWith("img_")) return@mapNotNull null
            val vin = key.name.removePrefix("img_")
            val path = prefs[stringPreferencesKey(key.name)]?.takeIf { it.startsWith("/") } ?: return@mapNotNull null
            // Path plus mtime plus length. The crop screen and applySyncPhotos both write
            // each car to a FIXED per-vin filename (deliberately, so repeated syncs
            // overwrite in place instead of accumulating orphans), so the path alone cannot
            // tell a new photo from the old one -- mtime and length are what move.
            val file = java.io.File(path)
            val stamp = "$path:${file.lastModified()}:${file.length()}"
            syncPhotoCache[vin]?.let { (cachedStamp, cachedB64) ->
                if (cachedStamp == stamp) return@mapNotNull vin to JsonPrimitive(cachedB64)
            }
            val bytes = runCatching { downscaledJpegBytes(path) }.getOrNull() ?: return@mapNotNull null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            // Bounded by garage size in normal use (one entry per vin, a changed photo
            // replaces its own entry). The clear() is a backstop for a pathological garage,
            // and costs only a re-encode.
            if (syncPhotoCache.size >= MAX_CACHED_SYNC_PHOTOS) syncPhotoCache.clear()
            syncPhotoCache[vin] = stamp to b64
            vin to JsonPrimitive(b64)
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
        try {
            val scale = SYNCED_PHOTO_MAX_DIM.toFloat() / maxOf(decoded.width, decoded.height)
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
            } else decoded
            val out = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 78, out)
            return out.toByteArray()
        } finally {
            decoded.recycle()
        }
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
     * Delete car photos on disk that no preference points at any more.
     *
     * The crop screen writes `cars/car_<vin>_<millis>.<ext>` -- a FRESH timestamped name
     * every time -- and only overwrites the `img_$vin` pref. So every re-crop left the
     * previous file behind forever. Ten passes at the crop slider on one car is ten
     * full-resolution images, none of them reachable. (applySyncPhotos writes a fixed
     * `car_<vin>_synced.jpg` instead, which is why it does not leak; its path is stored in
     * the same pref, so it is correctly seen as referenced here.)
     *
     * Safe by construction, and deliberately NOT the per-VIN pref garbage collection the
     * same leak invites. `img_$vin` is the ONLY preference that holds a local photo path
     * (`photo_$vin` looks like a second one but is a Wear DataMap asset key, not a pref),
     * so a file absent from that set cannot be displayed by anything -- there is no code
     * path that could reach it. Crucially this makes the decision independent of the
     * VEHICLE LIST: purging prefs for "cars that disappeared" would risk destroying a
     * user's plate, service history and presets whenever one brand's fetch failed and its
     * cars merely looked absent. This asks a question that cannot be wrong instead.
     *
     * [MIN_ORPHAN_AGE_MS] guards the one race: a file written by the crop screen
     * microseconds before its pref write lands. Nothing else in the app writes here.
     */
    suspend fun pruneOrphanPhotos(): Int = withContext(Dispatchers.IO) {
        val dir = java.io.File(context.filesDir, "cars")
        if (!dir.isDirectory) return@withContext 0
        val referenced = context.settingsDataStore.data.first().asMap()
            .filterKeys { it.name.startsWith("img_") }
            .values.filterIsInstance<String>()
            .filter { it.startsWith("/") }
            .toSet()
        val cutoff = System.currentTimeMillis() - MIN_ORPHAN_AGE_MS
        var freed = 0
        dir.listFiles()?.forEach { f ->
            if (!f.isFile || f.absolutePath in referenced || f.lastModified() > cutoff) return@forEach
            if (runCatching { f.delete() }.getOrDefault(false)) freed++
        }
        if (freed > 0) AppLog.log("Cleaned up $freed orphaned car photo(s)")
        freed
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
        // Validate format/version up front for the merge-specific behaviour a bad
        // remote file needs: return false (don't apply anything, but let the upload
        // half of the pass proceed), and log the newer-format case. parseBackup
        // below applies the same guards, but doing them here keeps the AppLog line
        // and the distinct "skip import, keep syncing" semantics intact.
        val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
        if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return false
        val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) {
            // A newer device wrote this — rather than misapply a format we don't
            // recognize, skip the import half this round (the upload half still
            // runs normally) and wait for this device to be updated.
            AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping import")
            return false
        }
        if ((root["prefs"] as? JsonObject) == null) return false
        // Photos are guarded by the pre-pass `protect` snapshot (an img_$vin changed locally
        // since the last sync keeps its local file), PLUS every photo this device chose itself.
        //
        // `protect` alone was not enough, and the gap destroyed originals. Once a device has
        // successfully uploaded its own photo, clearDirtyKeys drops img_$vin from the dirty set
        // -- so on the very next pass the key is no longer protected. A peer that imported the
        // photo re-uploads it, this device's hash gate opens, and applySyncPhotos then writes the
        // peer's TRANSPORT copy over the originating device's own pref.
        //
        // The transport copy is lossy by design: encodeSyncPhotos re-encodes to a 640px JPEG at
        // quality 78. CropScreen goes out of its way to save an alpha source as a 1080px PNG
        // ("Preserve transparency ... so the background stays see-through"), and JPEG cannot
        // carry alpha at all. So the device that chose a transparent PNG ended up displaying a
        // flattened, black-backgrounded, twice-compressed 640px JPEG of it.
        //
        // And it was unrecoverable rather than merely wrong, because of pruneOrphanPhotos, which
        // I added earlier on this branch: once img_$vin points at car_$vin_synced.jpg, the
        // original crop is referenced by nothing and the sweep deletes it. Before that sweep
        // existed the original at least survived on disk. A leak-fix turned a degradation into
        // data loss -- worth remembering as a class of mistake, not just this instance.
        //
        // Scoped so a genuine peer update still lands: only photos whose path is NOT the synced
        // filename are protected. A device whose photo already came from sync keeps accepting
        // newer synced photos; a device that chose its own original never has it overwritten by
        // a re-encode of itself. adoptSettingsJson already protected ALL local img keys and says
        // why -- this is the same reasoning, one import path later.
        val ownPhotoKeys = context.settingsDataStore.data.first().asMap()
            .filterKeys { it.name.startsWith("img_") }
            .filterValues { v ->
                (v as? String)?.let { it.startsWith("/") && !it.endsWith("_synced.jpg") } == true
            }
            .keys.map { it.name }.toSet()
        val photoPaths = applySyncPhotos(root["photos"] as? JsonObject, protect + ownPhotoKeys)
        context.settingsDataStore.edit { mut ->
            // Re-read the dirty set from THIS transaction's live prefs, not just the
            // protect snapshot taken before the pass started: a local edit that
            // landed between that snapshot and this edit block (setters don't hold
            // driveSyncMutex) would otherwise be clobbered by the incoming remote
            // value. Treat those live-dirty keys exactly like protect -- skip
            // writing and skip removing them. [SyncMerge.mergePlan] applies the
            // guarded drop (and the DEVICE_LOCAL_KEYS exclusion, and value typing)
            // purely on the prefs/tombstones; photos are handled above.
            val liveDirty = mut.dirtyKeySet()
            val guarded = protect + liveDirty
            // parseBackup already succeeded on the guards above, so mergePlan is
            // non-null here; ?: return@edit is a defensive no-op.
            val plan = SyncMerge.mergePlan(json, guarded) ?: return@edit
            plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
            plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
            // photoPaths was decided from the PRE-PASS protect+ownPhotoKeys snapshot,
            // taken before this edit block opened -- the same gap `liveDirty` above
            // exists to close for prefs. setImageUrl (the crop screen's write path)
            // goes through editTracked and holds no mutex, so it can land between
            // that snapshot and here; without this filter its fresh img_$vin would
            // be silently overwritten by the older remote photo, AND -- since that
            // overwrite bypasses editTracked -- the key wouldn't even be marked
            // dirty afterward, so the next sync push wouldn't re-upload the correct
            // local photo either. Same guard the prefs above already get.
            photoPaths.forEach { (vin, path) ->
                if ("img_$vin" !in guarded) mut[stringPreferencesKey("img_$vin")] = path
            }
            // Propagate deletions from the remote file, but never remove a key we're
            // protecting (locally changed since our last sync, or live-dirty within
            // this transaction) or a device-local key -- mergePlan already dropped
            // both from removes; both key types removed since names are shared.
            plan.removes.forEach { name ->
                mut.remove(stringPreferencesKey(name))
                mut.remove(booleanPreferencesKey(name))
            }
        }
        return true
    }

    /**
     * Full-adopt the file as the source of truth: apply EVERY portable key
     * unguarded (ignoring even the local dirty set) and clear the dirty set, so a
     * device joining an existing sync — or an explicit "pull from primary" — takes
     * the file's settings wholesale instead of protecting its own pre-join values.
     * This is the fix for the reported bug: the old code only ever ran the
     * protected [mergeSettingsJson], and a previously-used joining device's dirty
     * set covered ~every key, so it adopted almost nothing.
     *
     * Distinct from [mergeSettingsJson] on two points: (1) it does NOT re-add
     * live-dirty to a guarded set (there is no guarding — the file wins); (2) photos
     * are still PROTECTED (`protect = all local img_ keys`) so a join doesn't
     * silently replace the user's own car photos with the primary's, while every
     * other pref fully adopts. Clears the dirty set at the end so the adopted values
     * aren't immediately re-uploaded as "local changes".
     */
    private suspend fun adoptSettingsJson(json: String): Boolean {
        val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
        if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return false
        val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) {
            AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping adopt")
            return false
        }
        if ((root["prefs"] as? JsonObject) == null) return false
        // Protect the user's own local car photos across a join-adopt (only these).
        val localImgKeys = context.settingsDataStore.data.first().asMap().keys
            .map { it.name }.filter { it.startsWith("img_") }.toSet()
        val photoPaths = applySyncPhotos(root["photos"] as? JsonObject, protect = localImgKeys)
        // Unguarded plan: the file wins for every portable pref/tombstone.
        val plan = SyncMerge.parseBackup(json) ?: return false
        context.settingsDataStore.edit { mut ->
            plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
            plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
            photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
            plan.removes.forEach { name ->
                mut.remove(stringPreferencesKey(name))
                mut.remove(booleanPreferencesKey(name))
            }
            // Clear the dirty set in the SAME transaction: the adopted values are
            // the file's, not pending local changes, so they must not be re-uploaded
            // as edits (and must not protect themselves on the next merge).
            mut.remove(stringPreferencesKey("sync_dirty_keys"))
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
        // GetLastKnownLocation requires an active location grant; fail fast and
        // explicitly instead of relying on the SecurityException throw inside
        // the runCatching below to do the same thing. Keep the runCatching
        // anyway -- TIME is revoked mid-call by the user sometimes, and a
        // missed weather label must never crash a settings click.
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
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
                    listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea).distinct()
                        .joinToString(", ")
                        // .ifBlank, because the `?: "My location"` below only catches a NULL
                        // geocode. An Address whose locality, subAdminArea AND adminArea are
                        // all null -- offshore, or a sparse country -- makes joinToString
                        // return "", which is non-null, so it sailed past the fallback and
                        // setWeatherLocation stored a label of no label at all. The
                        // forward-geocode path in AppViewModel already guards this way.
                        .ifBlank { "My location" }
                }
        }.getOrNull() ?: "My location"
        setWeatherLocation(loc.latitude, loc.longitude, label)
        return true
    }
}

/**
 * `vin -> (file stamp, base64 JPEG)` memo for `SettingsStore.encodeSyncPhotos`.
 *
 * Top-level rather than a field, because [SettingsStore] is CONSTRUCTED AD HOC at a dozen
 * call sites (`SettingsStore(context).appearance.first()` and friends) -- an instance field
 * would be a fresh empty map on most of those calls and would cache nothing. This is a pure
 * memo of a deterministic function of a file's bytes, so process scope is the correct scope,
 * and there is nothing to invalidate on sign-out or car removal: the stamp does that.
 *
 * ConcurrentHashMap because sync runs off the main thread and two passes can overlap. A torn
 * read here would at worst re-encode; a ConcurrentModificationException would fail a sync.
 */
private val syncPhotoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

/** Entry cap for [syncPhotoCache]. A 640px quality-78 JPEG base64s to roughly 60-110 KB, so
 *  this bounds the memo near a megabyte for a garage far larger than any real one. */
private const val MAX_CACHED_SYNC_PHOTOS = 12
