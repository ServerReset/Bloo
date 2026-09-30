package com.bloo.bluelink.data

import androidx.compose.runtime.Immutable
import android.content.Context
import android.os.Build
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import androidx.core.graphics.scale

// A corruption handler so a settings file damaged by an interrupted write / power
// loss resets to empty prefs instead of rethrowing IOException out of every read
// (which crashed the app on launch, since `appearance` is collected eagerly).
internal val Context.settingsDataStore by preferencesDataStore(
    name = "bloo_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

// Process-wide serialization for performMainToMainSync(): the periodic worker and the
// auto-sync-on-refresh collector can both fire at nearly the same moment, and
// SettingsStore is instantiated fresh at each call
// site (not a singleton) — a per-instance lock wouldn't serialize anything, so
// this lives at module scope instead, same pattern as BlueLinkGate.statusMutex.
internal val mainToMainSyncMutex = Mutex()

// A stalled SAF/DocumentsProvider call previously had no bound and could hold
// mainToMainSyncMutex indefinitely; each Drive I/O step in performMainToMainSync() is
// capped at this long instead.
internal const val DRIVE_IO_TIMEOUT_MS = 20_000L

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
 * The ONE powertrain resolution rule, used by every surface in the app that
 * needs to know a vehicle's powertrain -- an explicit user override (stored
 * per-VIN; [SettingsStore.powertrain]/[SettingsStore.setPowertrain] persist
 * it, [UiState.powertrains] holds it in memory for the running app) always
 * wins; otherwise infer from the API's own [Vehicle.isEv] flag, which only
 * ever distinguishes EV from everything else.
 *
 * Before this, [UiState.powertrainOf] (in-memory, UI-facing) and the
 * background alert path ([CarAlerts.evaluate], no UiState to read) each
 * re-derived this same override-or-infer rule independently, and neither one
 * could see a hybrid/PHEV override the other correctly honoured -- exactly
 * the kind of drift a single shared rule is for. Both now call this.
 */
fun resolvePowertrain(v: Vehicle, override: Powertrain?): Powertrain =
    override ?: if (v.isEv) Powertrain.EV else Powertrain.GAS

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
    /** Never re-lock after launch. */
    OFF("Off"),

    /** Re-lock only when the screen actually turns off while the app is away -- a brief
     *  backgrounding (a system prompt, a quick app switch) leaves it unlocked. */
    SCREEN_OFF("Screen off"),

    /** Re-lock the moment the app is backgrounded, however briefly. */
    IMMEDIATE("Immediate"),
}

/**
 * The wire-key form of a [LockTiming], matching the string vocabulary [shouldRelockAfter]
 * switches on. NOT the enum's persistence format -- LockTiming persists via `.name` -- purely
 * the bridge into the shared re-lock predicate. Exhaustive with no `else` on purpose: adding a
 * LockTiming value must fail to compile here until its key is chosen.
 */
val LockTiming.wireKey: String
    get() = when (this) {
        LockTiming.OFF -> "off"
        LockTiming.SCREEN_OFF -> "screen_off"
        LockTiming.IMMEDIATE -> "immediate"
    }

/** Reorderable detail sections (pebbles), in their default order. */
// "climate" ahead of "ai": pre-heating/cooling the car before walking out to
// it is the single most common "glance and go" action this app exists for,
// while AI summary is a passive, network-dependent read -- the old order put
// a "Summarize" button ahead of every actual control for anyone who hasn't
// customized their section order.
val DEFAULT_SECTIONS = listOf("summary", "update", "controls", "charge", "climate", "ai", "info", "location", "trips", "diagnostics")

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
 * Google Drive sync (see [performMainToMainSync]) can tell which values are "dirty"
 * (changed locally but not yet uploaded).
 *
 * Because DataStore only stores primitives, anything structured (climate presets,
 * custom palettes, the full settings backup itself) is
 * JSON-encoded with kotlinx.serialization into a single string value under one
 * key, then decoded back out on read. Anything keyed per-car interpolates the
 * vehicle's VIN directly into the preference key name (e.g. "plate_$vin",
 * "climate_$vin") rather than using a nested/structured key space, since
 * Preferences DataStore only supports a flat namespace.
 */
class SettingsStore(internal val context: Context) {

    /** All strongly-typed, non-interpolated preference keys used directly by
     *  name below. Per-car keys are instead built ad hoc with string
     *  interpolation (see e.g. [seatConfig]) since Preferences DataStore has
     *  no notion of a keyed sub-namespace — this object only holds the ones
     *  that are the same for the whole app. */
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
        val HAPTICS = stringPreferencesKey("haptics_enabled")
        val PEBBLE_OUTLINE = stringPreferencesKey("pebble_outline")
        val COVER_SETTINGS_HINT = stringPreferencesKey("cover_settings_hint_dismissed")
        val SHOW_SEARCH = stringPreferencesKey("show_search")
        val SEAMLESS_INSTALL_SHIZUKU = stringPreferencesKey("seamless_install_shizuku")
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
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val LAST_VIN = stringPreferencesKey("last_vehicle_vin")
        val ORDER = stringPreferencesKey("vehicle_order")
        val SETTINGS_MODE = stringPreferencesKey("settings_mode")
        val CHARGER_API_KEY = stringPreferencesKey("charger_api_key")
        /** Per-VIN default climate preset ID for the one-tap Start button in advanced mode. */
        const val DEFAULT_CLIMATE_PRESET_PREFIX = "default_climate_preset_"
    }

    /** @Immutable for the same reason as UiState: this is threaded through
     *  every screen, and while Compose infers it unstable (it holds Maps and
     *  Lists) nothing taking it can ever skip. All fields are vals and every
     *  collection in one is built fresh by the store, never edited in place. */
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
        /** User-set weather location (latitude/longitude/place label), or null if unset. */
        val weatherLat: Double? = null,
        val weatherLon: Double? = null,
        val weatherLabel: String? = null,
        /** True when the weather location was last set via [setWeatherFromDeviceLocation]
         *  (the "use device location" mode) rather than a typed place. Lets a refresh
         *  re-sync it to wherever the device is NOW instead of it staying a one-time
         *  snapshot from whenever that mode was turned on -- reported directly as the
         *  location "not updating in settings when the app is refreshed if it's set to
         *  location mode". False whenever a place is set explicitly, or the location is
         *  cleared -- see [setWeatherLocation]. */
        val weatherFollowsDevice: Boolean = false,
        /** True for imperial (°F), false for metric (°C). Derived from [unitSystem]. */
        val useFahrenheit: Boolean = true,
        val biometricLock: Boolean = false,
        /** When the biometric lock re-engages after leaving the foreground. */
        val lockTiming: LockTiming = LockTiming.IMMEDIATE,
        /** In the wide expanded view, put pebbles on the left, controls right. */
        val columnsFlipped: Boolean = false,
        /** Text/UI scale multiplier (0.85–1.3). */
        val uiScale: Float = 1f,
        /** Colour vibrancy multiplier (0.5–1.6, 1 = default). */
        val vibrancy: Float = 1f,
        /** Show an aurora gradient as the app background instead of solid surface. */
        val auroraBackground: Boolean = false,
        /** Aurora motion mode: "off", "static", "motion". */
        val auroraMotion: String = "static",
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
        /** Opt-in "liquid glass" appearance. Off by default = current look; when
         *  on, floating chrome and cards use real backdrop refraction (API 31+)
         *  or an enhanced-frosted fallback below that. */
        /** User's own free Open Charge Map API key (openchargemap.org), or null if
         *  never set. OCM requires one per-caller now -- see ChargerApi's own doc --
         *  and this app has no business shipping one embedded for every install to
         *  share (a single key's rate limit split across every Bloo user would starve
         *  fast). Entered inline from the expanded map's charger filter bar the first
         *  time a search fails for lack of one. */
        val chargerApiKey: String? = null,
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
            // "AMOLED"/"SYSTEM_AMOLED" are legacy values remapped to the nearest modern mode, so
            // an install that had AMOLED selected keeps a dark theme instead of reverting to System.
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
            // Clamp on read: a corrupt/hand-edited/foreign backup with e.g.
            // ui_scale="10" would otherwise scale the whole UI 10x and lock the
            // user out of Settings, so a bad stored value can never take effect.
            uiScale = (prefs[Keys.UI_SCALE]?.toFloatOrNull() ?: 1f).coerceIn(0.85f, 1.3f),
            vibrancy = (prefs[Keys.VIBRANCY]?.toFloatOrNull() ?: 1f).coerceIn(0.5f, 1.6f),
            hapticsEnabled = prefs[Keys.HAPTICS]?.toBooleanStrictOrNull() ?: true,
            auroraBackground = prefs[Keys.AURORA]?.toBooleanStrictOrNull() ?: false,
            auroraMotion = prefs[Keys.AURORA_MOTION] ?: "static",
            unitSystem = prefs[Keys.UNIT_SYSTEM] ?: "imperial",
            // Shared rule -- see FormatUtils.useFahrenheit for why this stopped being
            // written out separately per surface.
            useFahrenheit = useFahrenheit(prefs[Keys.UNIT_SYSTEM]),
            pebbleOutline = prefs[Keys.PEBBLE_OUTLINE]?.toBooleanStrictOrNull() ?: false,
            coverSettingsHintDismissed = prefs[Keys.COVER_SETTINGS_HINT]?.toBooleanStrictOrNull() ?: false,
            showSearch = prefs[Keys.SHOW_SEARCH]?.toBooleanStrictOrNull() ?: true,
            seamlessInstallShizuku = prefs[Keys.SEAMLESS_INSTALL_SHIZUKU]?.toBooleanStrictOrNull() ?: false,
            chargerApiKey = prefs[Keys.CHARGER_API_KEY],
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
        /** Which look the charging notification takes. False (the default) is the full-width
         *  bar layout; true hands it to the system's own Live Update style, which is the ONLY
         *  way to get the Android 16 status-bar chip -- a custom layout cannot be promoted. */
        val liveChargeSystemStyle: Boolean = false,
        /** Notification when the car's engine is started. */
        val carStarted: Boolean = true,
        /** Notification when charging is complete. */
        val chargeComplete: Boolean = true,
    )

    /** Reactive equivalent of [notificationPrefs] for UI that needs to update
     *  live when the user changes a toggle in Settings while the screen is open. */
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

    /** Result of [testSyncRoundTrip]: [ok] plus a human-readable [message]
     *  describing exactly which step passed or failed, for a Settings "Test
     *  sync" diagnostic the user can run on a real device. */
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

    /** The settings-backup format version. The format is a flat key-value bag,
     *  so an older client reading a newer backup is normally fine (unrecognized
     *  keys are simply ignored — ignoreUnknownKeys); bump this only if a future
     *  change stops being purely additive (a renamed/restructured key an older
     *  client would misinterpret rather than just skip), so old clients can
     *  detect and refuse it instead of silently importing something wrong.
     *  Single source of truth lives in [SyncMerge] (the pure, testable core);
     *  this alias keeps the many in-class references reading by simple name. */
    internal val BACKUP_VERSION = SyncMerge.BACKUP_VERSION

    /** Preference keys that describe THIS device's own Drive-sync wiring (a
     *  content:// URI this app instance was granted permission for, local
     *  bookkeeping of when it last synced, its own Wi-Fi-only preference, and
     *  which keys it's changed locally since its last sync) — never portable, so
     *  never included in or restored from a settings backup. A tablet that's
     *  Wi-Fi-only and a phone with unlimited data may reasonably want different
     *  choices here, same as the Drive URI itself. Defined in [SyncMerge] so the
     *  pure export/merge core and this Context-bound store can't drift apart. */
    internal val DEVICE_LOCAL_KEYS = SyncMerge.DEVICE_LOCAL_KEYS

    /**
     * Wraps a settings mutation to record which preference keys it actually
     * changed into the "dirty" set — the keys this device has touched locally
     * since its own last successful Drive sync. [performMainToMainSync] protects
     * these from being overwritten by an incoming remote file, so a local edit
     * that hasn't been uploaded yet is never silently lost (field-level merge
     * instead of one whole-file last-write-wins).
     *
     * NOT used by [mergeSettingsJson] — accepting a value FROM the remote file
     * must not re-mark that same key as a pending local change, or it would
     * never propagate back out to a third device.
     */
    internal suspend fun editTracked(mutate: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
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
    internal fun Preferences.dirtyKeySet(): Set<String> =
        this[stringPreferencesKey("sync_dirty_keys")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    internal suspend fun dirtyKeys(): Set<String> =
        context.settingsDataStore.data.first().dirtyKeySet()

    /** Reactive view of the dirty set — emits whenever a tracked setting changes
     *  (every [editTracked] that touches a portable key appends to it). The
     *  ViewModel observes this to auto-push to Drive shortly after ANY change
     *  (setting toggle, pebble/section reorder, per-car config…), so sync feels
     *  automatic instead of only firing on a refresh or the periodic worker.
     *  Emits the empty set once the last upload clears it.
     *
     *  Deduplicated HERE, on the key set *and a biometric of those keys' current values*,
     *  rather than by a plain `distinctUntilChanged()` at the collector. That is the whole
     *  point: the dirty set is a lossy projection of "something changed", so editing the
     *  same key twice leaves it byte-identical. A collector deduplicating on the set alone
     *  therefore saw no second change -- which is fine while the first push is still pending
     *  (it uploads current values anyway), but not when that push FAILED: the set stayed
     *  `{k}`, the re-edit of `k` produced `{k}` again, no emission, and the change sat
     *  unsynced until a data refresh or the 2-hour worker. The value biometric restores
     *  the promise the first sentence above makes.
     *
     *  Biometriced by hash, not by retaining the values: `distinctUntilChanged` holds its
     *  last value for comparison, and dirty values include multi-kilobyte JSON blobs
     *  (climate presets, custom palettes). A hash collision would suppress one emission --
     *  a delayed sync, backstopped by the periodic worker -- not a wrong one. */
    val dirtyKeysFlow: Flow<Set<String>> = context.settingsDataStore.data
        .map { prefs ->
            val keys = prefs.dirtyKeySet()
            // One name -> value map, not a scan per dirty key. Tombstoned keys are absent
            // from prefs and biometric as null, so a delete and a later restore of the
            // same key are correctly distinct.
            val byName = prefs.asMap().entries.associate { it.key.name to it.value }
            // NUL separator below, and written as the ESCAPE rather than the character. A pref
            // value can hold any printable text (names, JSON blobs, file paths), so a space or
            // comma separator would let two different key/value sets biometric alike; NUL
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
    internal suspend fun clearDirtyKeys(keys: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            val dirtyKey = stringPreferencesKey("sync_dirty_keys")
            val remaining = prefs.dirtyKeySet() - keys
            if (remaining.isEmpty()) prefs.remove(dirtyKey) else prefs[dirtyKey] = remaining.joinToString(",")
        }
    }

    /** How recently a file in `cars/` must have been written to be spared by
     *  [pruneOrphanPhotos]. Only guards the crop screen's write-file-then-write-pref
     *  window, which is sub-millisecond; ten minutes is absurdly generous on purpose,
     *  because the cost of waiting is one stale file until the next launch and the cost
     *  of being wrong is deleting the photo the user just chose. */
    internal val MIN_ORPHAN_AGE_MS = 10 * 60 * 1000L

    // --- Chargers ----------------------------------------------------------

    // --- Weather ---------------------------------------------------------

}
