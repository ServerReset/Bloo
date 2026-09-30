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
import com.bloo.bluelink.autolock.AutoLockConfig
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
import kotlinx.serialization.DeserializationStrategy
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
    private object Keys {
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

    suspend fun setBiometricLock(enabled: Boolean) {
        editTracked { it[Keys.BIOMETRIC] = enabled.toString() }
    }

    /** Stores the enum's name() as a string; read back with LockTiming.valueOf(),
     *  falling back to LockTiming.IMMEDIATE if the stored name no longer matches
     *  an enum constant (e.g. after a rename). */
    suspend fun setLockTiming(value: LockTiming) {
        editTracked { it[Keys.LOCK_TIMING] = value.name }
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
        /** Notification when the car's engine is started. */
        val carStarted: Boolean = true,
        /** Notification when charging is complete. */
        val chargeComplete: Boolean = true,
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
            carStarted = p[booleanPreferencesKey("notify_start")] ?: true,
            chargeComplete = p[booleanPreferencesKey("notify_charge_complete")] ?: true,
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

    suspend fun setNotifyCarStarted(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_start")] = v }.let {}

    suspend fun setNotifyChargeComplete(v: Boolean) =
        editTracked { it[booleanPreferencesKey("notify_charge_complete")] = v }.let {}

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

    /** Track the previous engine state per VIN to detect "started" transitions. */
    suspend fun engineStartNotificationSent(vin: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("notif_start_sent_$vin")] ?: false

    suspend fun setEngineStartNotificationSent(vin: String, value: Boolean) {
        editTracked { it[booleanPreferencesKey("notif_start_sent_$vin")] = value }
    }

    /** Track the previous charging state per VIN to detect "complete" transitions. */
    suspend fun chargeCompleteNotificationSent(vin: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("notif_complete_sent_$vin")] ?: false

    suspend fun setChargeCompleteNotificationSent(vin: String, value: Boolean) {
        editTracked { it[booleanPreferencesKey("notif_complete_sent_$vin")] = value }
    }

    suspend fun setUiScale(value: Float) {
        editTracked { it[Keys.UI_SCALE] = value.toString() }
    }

    suspend fun setVibrancy(value: Float) {
        editTracked { it[Keys.VIBRANCY] = value.toString() }
    }

    suspend fun setAuroraBackground(value: Boolean) {
        editTracked { it[Keys.AURORA] = value.toString() }
    }

    // The setters below validate the incoming string against the fixed set of
    // legal values and silently fall back to the default if it's anything else
    // (e.g. a stale string from a future app version we don't recognize),
    // rather than storing garbage that the appearance Flow above would then
    // have to re-validate on every read.
    suspend fun setAuroraMotion(value: String) {
        editTracked { it[Keys.AURORA_MOTION] = value.takeIf { it in setOf("off", "static", "motion") } ?: "static" }
    }

    suspend fun setUnitSystem(value: String) {
        editTracked { it[Keys.UNIT_SYSTEM] = value.takeIf { it in setOf("imperial", "metric") } ?: "imperial" }
    }

    /** Settings view mode: "simple" or "advanced". */
    suspend fun settingsMode(): String = settingsMode(context.settingsDataStore.data.first())
    fun settingsMode(p: Preferences): String = p[Keys.SETTINGS_MODE] ?: "simple"

    /** The chosen distance/temperature unit system ("imperial" default, or "metric"), for
     *  non-Compose callers that need it as a one-shot read rather than the [appearance] flow --
     *  e.g. CarAlerts building a notification string off the main thread. Mirrors [settingsMode]. */
    suspend fun unitSystem(): String =
        context.settingsDataStore.data.first()[Keys.UNIT_SYSTEM] ?: "imperial"

    suspend fun setSettingsMode(value: String) {
        editTracked { it[Keys.SETTINGS_MODE] = value }
    }

    /** Per-VIN default climate preset ID for the one-tap Start button. */
    suspend fun defaultClimatePreset(vin: String): String? = defaultClimatePreset(vin, context.settingsDataStore.data.first())

    fun defaultClimatePreset(vin: String, p: Preferences): String? =
        p[stringPreferencesKey(Keys.DEFAULT_CLIMATE_PRESET_PREFIX + vin)]?.takeIf { it.isNotBlank() }

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

    suspend fun lastVehicleVin(): String? = lastVehicleVin(context.settingsDataStore.data.first())

    fun lastVehicleVin(p: Preferences): String? = p[Keys.LAST_VIN]

    suspend fun setLastVehicleVin(vin: String) {
        editTracked { it[Keys.LAST_VIN] = vin }
    }

    /** User-defined display order of vehicles (by VIN). */
    suspend fun vehicleOrder(): List<String> = vehicleOrder(context.settingsDataStore.data.first())

    fun vehicleOrder(p: Preferences): List<String> =
        p[Keys.ORDER]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

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
    /**
     * Force the settings DataStore's first load now.
     *
     * Its very first read is the expensive one -- opening the file, parsing the preferences
     * protobuf -- and on the cold-start path the auto-login's own `appearance.first()` was
     * paying it, measured at 452ms on the API 34 emulator, directly between the credential
     * load and the garage load. DataStore caches the parsed data in memory after that first
     * read, so loading it on the startup warm-up thread makes the real read ~free.
     */
    suspend fun warmUp() {
        runCatching { context.settingsDataStore.data.first() }
    }

    suspend fun snapshot(): Preferences {
        com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() begin")
        val prefs = context.settingsDataStore.data.first()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() done")
        return prefs
    }

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
        isCarConfigured(vin, context.settingsDataStore.data.first())

    fun isCarConfigured(vin: String, p: Preferences): Boolean =
        p[booleanPreferencesKey("car_configured_$vin")] ?: false

    suspend fun setCarConfigured(vin: String) {
        editTracked { it[booleanPreferencesKey("car_configured_$vin")] = true }
    }

    // --- AutoLock (per car) ------------------------------------------------
    //
    // Ported from the i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock): locks a
    // car automatically when the phone disconnects from its paired Bluetooth device (the
    // head unit), confirmed by Activity Recognition (driving -> walking), after a
    // cancellable grace period, and only when the car's own status says it's safe to
    // (unlocked, engine off, doors/windows closed). See app/.../autolock/ for the state
    // machine, policy and detection plumbing.

    suspend fun autoLockConfig(vin: String): AutoLockConfig =
        autoLockConfig(vin, context.settingsDataStore.data.first())

    /**
     * [Preferences]-taking overload, same reason as [seatConfig]/[isCarConfigured]: the
     * Bluetooth receiver checks EVERY registered VIN on every single connect/disconnect
     * event, and each of those used to be its own full `.data.first()` DataStore round trip
     * -- N reads for N configured cars, on every Bluetooth event this phone ever sees, not
     * just the car's own. One snapshot, taken once by the caller (see
     * [autoLockConfiguredVins]'s own overload), serves all of them.
     */
    fun autoLockConfig(vin: String, p: Preferences): AutoLockConfig {
        fun b(key: String, default: Boolean) = p[booleanPreferencesKey(key)] ?: default
        fun s(key: String) = p[stringPreferencesKey(key)]
        fun i(key: String, default: Int) = s(key)?.toIntOrNull() ?: default
        return AutoLockConfig(
            enabled = b("autolock_enabled_$vin", false),
            deviceAddress = s("autolock_device_addr_$vin"),
            deviceName = s("autolock_device_name_$vin"),
            graceSeconds = i("autolock_grace_$vin", 30),
            dryRun = b("autolock_dry_run_$vin", true),
        )
    }

    /** Every DataStore key one car's [AutoLockConfig] occupies -- named once so
     *  [setAutoLockConfig] (which writes them) and [clearAllAutoLockConfigs] (which removes
     *  them on sign-out) can't drift out of sync with each other the way two hand-written key
     *  lists eventually would.
     *
     *  Includes several keys ("autolock_use_activity_$vin", "autolock_dont_lock_if_open_$vin",
     *  "autolock_use_bt_$vin", "autolock_use_geofence_$vin", "autolock_geofence_radius_$vin")
     *  that [autoLockConfig] no longer reads and [setAutoLockConfig] no longer writes -- those
     *  behaviors are now either hardcoded always-on or removed entirely (geofence). Left in
     *  this list purely so a sign-out still clears any value an older build wrote for them,
     *  rather than leaving orphaned keys behind. */
    private fun autoLockKeys(vin: String) = listOf(
        booleanPreferencesKey("autolock_enabled_$vin"),
        stringPreferencesKey("autolock_device_addr_$vin"),
        stringPreferencesKey("autolock_device_name_$vin"),
        booleanPreferencesKey("autolock_use_bt_$vin"),
        stringPreferencesKey("autolock_grace_$vin"),
        booleanPreferencesKey("autolock_use_activity_$vin"),
        booleanPreferencesKey("autolock_use_geofence_$vin"),
        stringPreferencesKey("autolock_geofence_radius_$vin"),
        booleanPreferencesKey("autolock_dont_lock_if_open_$vin"),
        booleanPreferencesKey("autolock_dry_run_$vin"),
    )

    suspend fun setAutoLockConfig(vin: String, config: AutoLockConfig) {
        editTracked {
            it[booleanPreferencesKey("autolock_enabled_$vin")] = config.enabled
            val addrKey = stringPreferencesKey("autolock_device_addr_$vin")
            if (config.deviceAddress == null) it.remove(addrKey) else it[addrKey] = config.deviceAddress
            val nameKey = stringPreferencesKey("autolock_device_name_$vin")
            if (config.deviceName == null) it.remove(nameKey) else it[nameKey] = config.deviceName
            it[stringPreferencesKey("autolock_grace_$vin")] = config.graceSeconds.toString()
            it[booleanPreferencesKey("autolock_dry_run_$vin")] = config.dryRun
        }
        // Maintain the registry of "cars with AutoLock configured" so the Bluetooth
        // receiver -- which starts from a raw device MAC or a VIN, not a UI selection -- can
        // enumerate every car to check instead of needing one BroadcastReceiver registration
        // per car.
        editTracked {
            val key = stringPreferencesKey("autolock_vins")
            val current = (it[key] ?: "").split(',').filter { s -> s.isNotBlank() }.toMutableSet()
            if (config.enabled) current += vin else current -= vin
            it[key] = current.joinToString(",")
        }
    }

    /** Every VIN that has ever had AutoLock enabled -- see [setAutoLockConfig]'s registry
     *  note. Used by [com.bloo.bluelink.autolock.AutoLockBluetoothReceiver] to find which
     *  car(s), if any, a disconnected Bluetooth device belongs to. */
    suspend fun autoLockConfiguredVins(): List<String> =
        autoLockConfiguredVins(context.settingsDataStore.data.first())

    fun autoLockConfiguredVins(p: Preferences): List<String> =
        (p[stringPreferencesKey("autolock_vins")] ?: "").split(',').filter { it.isNotBlank() }

    /** Forgets AutoLock entirely for every currently-registered car -- called on a full
     *  sign-out (see [com.bloo.bluelink.ui.AppViewModel.logout]) alongside the other
     *  account-derived stores it already wipes there (statusCache, snapshotStore). Without
     *  this, a signed-out car's VIN stayed in the registry forever: every Bluetooth
     *  connect/disconnect this phone ever saw kept checking it, futilely, against a vehicle
     *  [com.bloo.bluelink.autolock.AutoLockController] can never find again. */
    suspend fun clearAllAutoLockConfigs() {
        val vins = autoLockConfiguredVins()
        if (vins.isEmpty()) return
        editTracked {
            it.remove(stringPreferencesKey("autolock_vins"))
            // MutablePreferences.remove is generic on the key's own value type
            // (fun <T> remove(key: Preferences.Key<T>): T) and autoLockKeys' mixed
            // Boolean/String keys collapse to Preferences.Key<*> in the list -- Kotlin can't
            // infer T from a star projection, so the type parameter is pinned to Any here
            // instead (an unchecked but safe cast: remove() only ever reads the key's
            // identity, never the value type, to find and drop the entry).
            @Suppress("UNCHECKED_CAST")
            vins.forEach { vin -> autoLockKeys(vin).forEach { key -> it.remove(key as Preferences.Key<Any>) } }
        }
    }

    /** One DataStore read for every registered car's full [AutoLockConfig] -- what the
     *  Bluetooth receiver actually wants (a device MAC or a VIN comes in, every configured
     *  car needs checking against it), instead of the registry list plus one [autoLockConfig]
     *  round trip per car. */
    suspend fun allAutoLockConfigs(): Map<String, AutoLockConfig> {
        val p = context.settingsDataStore.data.first()
        return autoLockConfiguredVins(p).associateWith { autoLockConfig(it, p) }
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

    // --- On-device AI ----------------------------------------------------

    suspend fun aiEnabled(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("ai_enabled")] ?: false

    suspend fun setAiEnabled(value: Boolean) {
        editTracked { it[booleanPreferencesKey("ai_enabled")] = value }
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

    /** Drive URI for auto-backup; null when not configured. */
    suspend fun syncUri(): String? = syncUri(context.settingsDataStore.data.first())
    fun syncUri(p: Preferences): String? =
        p[stringPreferencesKey("sync_uri")]?.takeIf { it.isNotBlank() }

    /** A short, stable biometric of the ACTUAL Drive file this device is synced
     *  to, derived from the persisted document URI's unique id. Shown in Settings
     *  so two devices can eyeball whether they're on the SAME file: if the two
     *  biometrics differ, they picked different files (Google Drive allows two
     *  files with the same name), which is the #1 reason settings/devices don't
     *  converge. Null when sync isn't set up. */
    suspend fun syncFileFingerprint(): String? = syncFileFingerprint(context.settingsDataStore.data.first())
    fun syncFileFingerprint(p: Preferences): String? {
        if (syncUri(p) == null) return null
        // The CONTENT-based file id (stored inside the Drive file as `_fileId`,
        // cached here device-local). Every device on the same file reads the same
        // value → the same short code. This replaces the old URI hash, which was
        // WRONG: a SAF content:// URI is assigned PER DEVICE by the OS, so the same
        // Drive file had different URIs (and different hashes) on two phones — the
        // exact "I picked the same file but the codes differ" bug. Null until the
        // first successful sync has read/minted the id.
        val id = p[stringPreferencesKey("sync_file_id")] ?: return null
        // Short, human-comparable tag (first 6 hex of a SHA-256 of the full id) so
        // we never surface the raw UUID but two devices still match at a glance.
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return hash.take(3).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** The full content-based file id this device currently has cached, or null.
     *  Used by [performMainToMainSync] to decide whether to preserve the remote file's
     *  id or mint a new one. */
    internal suspend fun syncFileId(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_file_id")]?.takeIf { it.isNotBlank() }

    internal suspend fun setSyncFileId(id: String) {
        context.settingsDataStore.edit { it[stringPreferencesKey("sync_file_id")] = id }
    }

    suspend fun setSyncUri(uri: String?) {
        editTracked {
            val key = stringPreferencesKey("sync_uri")
            if (uri.isNullOrBlank()) it.remove(key) else it[key] = uri
        }
    }

    /** Timestamp (ms) of the last successful bidirectional sync. */
    suspend fun lastSyncMs(): Long = lastSyncMs(context.settingsDataStore.data.first())
    fun lastSyncMs(p: Preferences): Long =
        p[stringPreferencesKey("sync_last_ms")]?.toLongOrNull() ?: 0L

    suspend fun setLastSyncMs(ms: Long) {
        editTracked { it[stringPreferencesKey("sync_last_ms")] = ms.toString() }
    }

    /** Persisted so a failure from the background periodic worker (no live
     *  ViewModel to update UiState.syncError) still shows up in Settings the
     *  next time the app is opened, instead of only being visible if a
     *  foreground sync happens to fail while the app is open. */
    suspend fun lastSyncError(): String? = lastSyncError(context.settingsDataStore.data.first())
    fun lastSyncError(p: Preferences): String? = p[stringPreferencesKey("sync_last_error")]

    suspend fun setLastSyncError(error: String?) {
        editTracked { if (error == null) it.remove(stringPreferencesKey("sync_last_error")) else it[stringPreferencesKey("sync_last_error")] = error }
    }

    /** Wi-Fi only sync (true) or any network (false). */
    suspend fun syncWifiOnly(): Boolean = syncWifiOnly(context.settingsDataStore.data.first())
    fun syncWifiOnly(p: Preferences): Boolean =
        p[stringPreferencesKey("sync_wifi")]?.toBooleanStrictOrNull() ?: true

    suspend fun setSyncWifiOnly(value: Boolean) {
        editTracked { it[stringPreferencesKey("sync_wifi")] = value.toString() }
    }

    /**
     * When a paired WATCH should ask for the app PIN. Device-local (in DEVICE_LOCAL_KEYS): it
     * describes this phone's own paired watch, so it must not travel in the portable backup.
     * Stored via the RAW DataStore (not editTracked) for the same reason the device-registry
     * keys are -- touching it should not pollute the sync dirty set.
     */
    suspend fun watchLockTiming(): com.bloo.bluelink.data.WatchLockTiming =
        com.bloo.bluelink.data.WatchLockTiming.fromWire(
            context.settingsDataStore.data.first()[stringPreferencesKey("watch_lock_timing")],
        )

    suspend fun setWatchLockTiming(value: com.bloo.bluelink.data.WatchLockTiming) {
        context.settingsDataStore.edit { it[stringPreferencesKey("watch_lock_timing")] = value.wireKey }
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

    /** Reads the id straight off an already-taken snapshot, without the
     *  mint-if-missing side effect above -- null on a snapshot from before the id
     *  was first minted. Callers on the cold-start path that just want "whatever
     *  is there right now" (e.g. seeding UiState) should prefer this over the
     *  suspend overload; it costs no DataStore round trip against a snapshot they
     *  already hold. */
    fun syncDeviceId(p: Preferences): String? = p[stringPreferencesKey("sync_device_id")]?.takeIf { it.isNotBlank() }

    /** Friendly name shown in the "your devices" list. Defaults to the hardware
     *  model until the user renames it. */
    suspend fun syncDeviceName(): String = syncDeviceName(context.settingsDataStore.data.first())
    fun syncDeviceName(p: Preferences): String =
        p[stringPreferencesKey("sync_device_name")]?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "This device"

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
    suspend fun syncedDevices(): List<SyncMerge.SyncDevice> = syncedDevices(context.settingsDataStore.data.first())
    fun syncedDevices(p: Preferences): List<SyncMerge.SyncDevice> {
        val raw = p[stringPreferencesKey("sync_devices_cache")] ?: return emptyList()
        return runCatching { devicesJson.decodeFromString(deviceListSerializer, raw) }.getOrElse { emptyList() }
    }

    internal suspend fun setSyncedDevicesCache(devices: List<SyncMerge.SyncDevice>) {
        context.settingsDataStore.edit {
            it[stringPreferencesKey("sync_devices_cache")] = devicesJson.encodeToString(deviceListSerializer, devices)
        }
    }

    /** The primary device id (source of truth), cached device-local for display and
     *  written into the file on the next upload. Null = no primary chosen. */
    suspend fun syncPrimaryDeviceId(): String? = syncPrimaryDeviceId(context.settingsDataStore.data.first())
    fun syncPrimaryDeviceId(p: Preferences): String? =
        p[stringPreferencesKey("sync_primary_cache")]?.takeIf { it.isNotBlank() }

    internal suspend fun setSyncPrimaryCache(id: String?) {
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
     *  intent is what stopped the primary from ever changing -- see [performMainToMainSync]. */
    internal suspend fun syncPrimaryPending(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_primary_pending")]?.takeIf { it.isNotBlank() }

    internal suspend fun setSyncPrimaryPending(id: String?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_primary_pending")
            if (id.isNullOrBlank()) it.remove(k) else it[k] = id
        }
    }

    /** Designate the primary device (source of truth). Persists locally; the value
     *  is written into the Drive file on the next [performMainToMainSync] upload.
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

    /** Device ids queued for removal from the registry, not yet uploaded -- the same
     *  "pending write intent" shape [syncPrimaryPending] uses, so "kick this device"
     *  survives an app restart before the next sync pass gets to enact it. Unlike
     *  [setPrimaryDevice] this is a SET, not a single value: several devices could be
     *  kicked before the next sync runs. */
    suspend fun syncPendingRemovedDeviceIds(): Set<String> {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("sync_pending_removed_device_ids")]
        return raw?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    internal suspend fun setSyncPendingRemovedDeviceIds(ids: Set<String>) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("sync_pending_removed_device_ids")
            if (ids.isEmpty()) it.remove(k) else it[k] = ids.joinToString(",")
        }
    }

    /**
     * "Kick" a device out of the synced-devices list. NOT a permanent ban: this is
     * exactly the same pruning [SyncMerge.mergeDevices] already does automatically
     * for a device that hasn't been seen in 90 days, just requested NOW instead of
     * waited-for -- a device that syncs again after being kicked simply reappears,
     * the same way a stale one would if it ever came back online. That is a
     * deliberate safety property, not a limitation: kicking a device you don't
     * recognise can never permanently lock out one that is still genuinely in use.
     *
     * Recorded twice, same shape as [setPrimaryDevice]: as a pending write intent
     * (consumed by the next successful upload, which is what actually keeps it out
     * of the registry the OTHER devices see) and stripped from the display cache
     * immediately, so Settings reflects the tap right away rather than after a
     * round trip to Drive and back.
     */
    suspend fun removeSyncedDevice(id: String) {
        if (id.isBlank() || id == syncDeviceId()) return
        setSyncPendingRemovedDeviceIds(syncPendingRemovedDeviceIds() + id)
        setSyncedDevicesCache(syncedDevices().filterNot { it.id == id })
    }

    /** This device's own registry entry, freshly stamped. [appVersion] is best-effort. */
    internal suspend fun selfSyncDevice(nowMs: Long): SyncMerge.SyncDevice {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
        return SyncMerge.SyncDevice(
            id = syncDeviceId(),
            name = syncDeviceName(),
            model = Build.MODEL ?: "",
            appVersion = appVersion,
            lastSeenMs = nowMs,
            // A Wear OS companion registers through the SAME Drive sync as a phone, so it
            // lands in this registry like any peer -- but it must never read as a peer
            // PRIMARY candidate. Settings uses this to show it as a dependent companion
            // under its phone instead. FEATURE_WATCH is how a watch announces itself.
            kind = if (context.packageManager.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_WATCH,
                )
            ) SyncMerge.KIND_WATCH else SyncMerge.KIND_PHONE,
        )
    }

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

    /** The single pebble pinned to the hotspot's secondary slot for [vin], or null if none
     *  selected. The primary slot ("controls") is hardcoded and never persisted here. */
    suspend fun hotspots(vin: String): String? = hotspots(vin, context.settingsDataStore.data.first())

    fun hotspots(vin: String, p: Preferences): String? {
        return p[stringPreferencesKey("hotspots_$vin")]?.takeIf { it.isNotBlank() }
    }

    suspend fun setHotspots(vin: String, section: String?) {
        editTracked {
            val key = stringPreferencesKey("hotspots_$vin")
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

    /** Sets or clears (blank/null) the user's own Open Charge Map API key -- see
     *  [Appearance.chargerApiKey]'s own doc. */
    suspend fun setChargerApiKey(key: String?) {
        editTracked {
            if (key.isNullOrBlank()) it.remove(Keys.CHARGER_API_KEY) else it[Keys.CHARGER_API_KEY] = key.trim()
        }
    }

    // --- Weather ---------------------------------------------------------

    /** Set or clear the weather location. Passing null lat/lon clears it. Always
     *  resets [Appearance.weatherFollowsDevice] to false -- every caller of this
     *  EXCEPT [setWeatherFromDeviceLocation] is setting an explicit, static
     *  location (a typed place, or clearing it entirely), and that one turns the
     *  flag back on itself, right after calling this. */
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
            it.remove(Keys.WEATHER_FOLLOWS_DEVICE)
        }
    }

    /** Set the home weather location from this device's own last-known GPS
     *  fix, reverse-geocoded to a place label -- the phone Settings screen's
     *  "My location" action. Returns false when no location is available
     *  (e.g. permission never granted on this device) so the caller can
     *  report that clearly.
     *
     *  [preloaded], when given, is used AS-IS instead of this function doing
     *  its own LocationManager fetch -- specifically so AppViewModel's periodic
     *  device-location refresh (fused location, the same fix that becomes
     *  [com.bloo.bluelink.ui.UiState.deviceLocation] and the dot drawn on the
     *  car map) and the weather-follows-device location are the SAME reading,
     *  not two independently-fetched ones that can legitimately disagree by
     *  city blocks. Reported directly: the map's device dot, the home weather
     *  card and "how far is the car from me" could each show a different spot
     *  for "here". A caller with no pre-fetched location (the Settings screen's
     *  manual "My location" button) still gets the original LocationManager-based
     *  fetch below. */
    suspend fun setWeatherFromDeviceLocation(preloaded: android.location.Location? = null): Boolean {
        val loc = preloaded ?: run {
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
            runCatching {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER,
                ).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            }.getOrNull() ?: return false
        }
        // @Suppress("DEPRECATION"): the sync Geocoder is Java-deprecated in favour of
        // the API-33+ listener overload, but the sync form is the only one that
        // exists on every supported API level (minSdk 26) without a second,
        // listener-shaped implementation. The whole read is runCatching-wrapped.
        @Suppress("DEPRECATION")
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
        // Re-set AFTER setWeatherLocation, which unconditionally clears this flag
        // (see its own doc) -- this is the one call site that's allowed to turn it
        // back on, marking the location as "following the device" so a later
        // refresh (WeatherController.refreshDeviceLocationForWeather) knows to
        // re-run this same fetch instead of leaving it frozen at this one fix.
        editTracked { it[Keys.WEATHER_FOLLOWS_DEVICE] = "true" }
        return true
    }
}
