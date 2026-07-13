package com.bloo.bluelink.data

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
val DEFAULT_SECTIONS = listOf("summary", "controls", "charge", "ai", "climate", "info", "location", "weather", "trips", "diagnostics")

/** Pebbles the user may hide (the others are essential). */
val HIDEABLE_SECTIONS = listOf("charge", "climate", "location", "weather", "trips", "info", "diagnostics", "ai")

/** Number of configurable Quick Settings tiles (room for ~two per car). */
const val TILE_COUNT = 12

/** App appearance preferences, kept separate from the session so sign-out keeps them. */
class SettingsStore(private val context: Context) {

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
        val WEATHER_FAHRENHEIT = stringPreferencesKey("weather_fahrenheit")
        val BIOMETRIC = stringPreferencesKey("biometric_lock")
        val LOCK_TIMING = stringPreferencesKey("lock_timing")
        val LAST_VIN = stringPreferencesKey("last_vehicle_vin")
        val ORDER = stringPreferencesKey("vehicle_order")
        val FLIPPED = stringPreferencesKey("columns_flipped")
        val LINKS_IN_APP = stringPreferencesKey("links_in_app")
        val UI_SCALE = stringPreferencesKey("ui_scale")
        val VIBRANCY = stringPreferencesKey("vibrancy")
        val HAPTICS = stringPreferencesKey("haptics_enabled")
        val AURORA = stringPreferencesKey("aurora_background")
        val AURORA_MOTION = stringPreferencesKey("aurora_motion")
        val AURORA_COLOR_MODE = stringPreferencesKey("aurora_color_mode")
        val AURORA_CUSTOM_COLOR = stringPreferencesKey("aurora_custom_color")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
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
        /** Show all temperatures across the app in Fahrenheit (defaults to true for US locale). */
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
    )

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map { prefs ->
        val palJson = Json { ignoreUnknownKeys = true }
        val palSer = ListSerializer(CustomPaletteData.serializer())
        Appearance(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            fontChoice = prefs[Keys.FONT]?.let { runCatching { FontChoice.valueOf(it) }.getOrNull() }
                ?: FontChoice.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC]?.toBooleanStrictOrNull() ?: true,
            colorPalette = prefs[Keys.PALETTE]?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() }
                ?: ColorPalette.BLUE,
            customPalettes = prefs[Keys.CUSTOM_PALETTES]?.let { json ->
                runCatching { palJson.decodeFromString(palSer, json) }.getOrElse { emptyList() }
            } ?: emptyList(),
            activeCustomPaletteId = prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID],
            carCustomPaletteIds = prefs[Keys.CAR_PALETTE_IDS]?.let { json ->
                runCatching {
                    palJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), json)
                }.getOrElse { emptyMap() }
            } ?: emptyMap(),
            weatherLat = prefs[Keys.WEATHER_LAT]?.toDoubleOrNull(),
            weatherLon = prefs[Keys.WEATHER_LON]?.toDoubleOrNull(),
            weatherLabel = prefs[Keys.WEATHER_LABEL],
            useFahrenheit = prefs[Keys.WEATHER_FAHRENHEIT]?.toBooleanStrictOrNull() ?: true,
            biometricLock = prefs[Keys.BIOMETRIC]?.toBooleanStrictOrNull() ?: false,
            lockTiming = prefs[Keys.LOCK_TIMING]?.let { runCatching { LockTiming.valueOf(it) }.getOrNull() }
                ?: LockTiming.IMMEDIATE,
            columnsFlipped = prefs[Keys.FLIPPED]?.toBooleanStrictOrNull() ?: false,
            linksInApp = prefs[Keys.LINKS_IN_APP]?.toBooleanStrictOrNull() ?: true,
            uiScale = prefs[Keys.UI_SCALE]?.toFloatOrNull() ?: 1f,
            vibrancy = prefs[Keys.VIBRANCY]?.toFloatOrNull() ?: 1f,
            hapticsEnabled = prefs[Keys.HAPTICS]?.toBooleanStrictOrNull() ?: true,
            auroraBackground = prefs[Keys.AURORA]?.toBooleanStrictOrNull() ?: false,
            auroraMotion = prefs[Keys.AURORA_MOTION] ?: "static",
            auroraColorMode = prefs[Keys.AURORA_COLOR_MODE] ?: "complementary",
            auroraCustomColor = prefs[Keys.AURORA_CUSTOM_COLOR],
            unitSystem = prefs[Keys.UNIT_SYSTEM] ?: "imperial",
        )
    }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS] = value.toString() }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC] = enabled.toString() }
    }

    suspend fun setLockTiming(value: LockTiming) {
        context.settingsDataStore.edit { it[Keys.LOCK_TIMING] = value.name }
    }

    suspend fun setColumnsFlipped(flipped: Boolean) {
        context.settingsDataStore.edit { it[Keys.FLIPPED] = flipped.toString() }
    }

    // --- Notifications --------------------------------------------------

    data class NotificationPrefs(
        val service: Boolean = true,
        val doorOpen: Boolean = true,
        val doorOpenMinutes: Int = 5,
        val running: Boolean = true,
        val runningMinutes: Int = 10,
    )

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

    val notifications: Flow<NotificationPrefs> = context.settingsDataStore.data.map { p ->
        NotificationPrefs(
            service = p[booleanPreferencesKey("notify_service")] ?: true,
            doorOpen = p[booleanPreferencesKey("notify_door")] ?: true,
            doorOpenMinutes = p[stringPreferencesKey("notify_door_min")]?.toIntOrNull() ?: 5,
            running = p[booleanPreferencesKey("notify_running")] ?: true,
            runningMinutes = p[stringPreferencesKey("notify_running_min")]?.toIntOrNull() ?: 10,
        )
    }

    suspend fun setNotifyService(v: Boolean) =
        context.settingsDataStore.edit { it[booleanPreferencesKey("notify_service")] = v }.let {}

    suspend fun setNotifyDoor(v: Boolean) =
        context.settingsDataStore.edit { it[booleanPreferencesKey("notify_door")] = v }.let {}

    suspend fun setDoorOpenMinutes(v: Int) =
        context.settingsDataStore.edit { it[stringPreferencesKey("notify_door_min")] = v.toString() }.let {}

    suspend fun setNotifyRunning(v: Boolean) =
        context.settingsDataStore.edit { it[booleanPreferencesKey("notify_running")] = v }.let {}

    suspend fun setRunningMinutes(v: Int) =
        context.settingsDataStore.edit { it[stringPreferencesKey("notify_running_min")] = v.toString() }.let {}

    // Transient alert bookkeeping (per car), used to fire each alert only once.
    suspend fun doorOpenSince(vin: String): Long? =
        context.settingsDataStore.data.first()[stringPreferencesKey("door_since_$vin")]?.toLongOrNull()

    suspend fun setDoorOpenSince(vin: String, value: Long?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("door_since_$vin")
            if (value == null) it.remove(k) else it[k] = value.toString()
        }
    }

    suspend fun engineOnSince(vin: String): Long? =
        context.settingsDataStore.data.first()[stringPreferencesKey("engine_since_$vin")]?.toLongOrNull()

    suspend fun setEngineOnSince(vin: String, value: Long?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("engine_since_$vin")
            if (value == null) it.remove(k) else it[k] = value.toString()
        }
    }

    suspend fun alertFired(key: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("alert_$key")] ?: false

    suspend fun setAlertFired(key: String, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("alert_$key")] = value }
    }

    suspend fun setUiScale(value: Float) {
        context.settingsDataStore.edit { it[Keys.UI_SCALE] = value.toString() }
    }

    suspend fun setVibrancy(value: Float) {
        context.settingsDataStore.edit { it[Keys.VIBRANCY] = value.toString() }
    }

    suspend fun setLinksInApp(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.LINKS_IN_APP] = value.toString() }
    }

    suspend fun setAuroraBackground(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AURORA] = value.toString() }
    }

    suspend fun setAuroraMotion(value: String) {
        context.settingsDataStore.edit { it[Keys.AURORA_MOTION] = value.takeIf { it in setOf("off", "static", "motion") } ?: "static" }
    }

    suspend fun setAuroraColorMode(value: String) {
        context.settingsDataStore.edit { it[Keys.AURORA_COLOR_MODE] = value.takeIf { it in setOf("complementary", "material", "custom") } ?: "complementary" }
    }

    suspend fun setAuroraCustomColor(value: String?) {
        context.settingsDataStore.edit {
            val key = Keys.AURORA_CUSTOM_COLOR
            if (value.isNullOrBlank()) it.remove(key) else it[key] = value
        }
    }

    suspend fun setUnitSystem(value: String) {
        context.settingsDataStore.edit { it[Keys.UNIT_SYSTEM] = value.takeIf { it in setOf("imperial", "metric") } ?: "imperial" }
    }

    // --- Per-car identity + service (the API has no service-history fields) ---

    suspend fun licensePlate(vin: String): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("plate_$vin")] ?: ""

    suspend fun setLicensePlate(vin: String, value: String) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("plate_$vin")
            if (value.isBlank()) it.remove(key) else it[key] = value.trim()
        }
    }

    suspend fun lastServiceMiles(vin: String): Int? =
        context.settingsDataStore.data.first()[stringPreferencesKey("svc_last_$vin")]?.toIntOrNull()

    suspend fun setLastServiceMiles(vin: String, value: Int?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("svc_last_$vin")
            if (value == null) it.remove(key) else it[key] = value.toString()
        }
    }

    suspend fun serviceIntervalMiles(vin: String): Int? =
        context.settingsDataStore.data.first()[stringPreferencesKey("svc_interval_$vin")]?.toIntOrNull()

    suspend fun setServiceIntervalMiles(vin: String, value: Int?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("svc_interval_$vin")
            if (value == null) it.remove(key) else it[key] = value.toString()
        }
    }

    suspend fun lastVehicleVin(): String? =
        context.settingsDataStore.data.first()[Keys.LAST_VIN]

    suspend fun setLastVehicleVin(vin: String) {
        context.settingsDataStore.edit { it[Keys.LAST_VIN] = vin }
    }

    /** User-defined display order of vehicles (by VIN). */
    suspend fun vehicleOrder(): List<String> =
        context.settingsDataStore.data.first()[Keys.ORDER]
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    suspend fun setVehicleOrder(order: List<String>) {
        context.settingsDataStore.edit { it[Keys.ORDER] = order.joinToString("\n") }
    }

    /** Optional user-set photo URL per vehicle (empty = use the default gradient). */
    suspend fun imageUrl(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("img_$vin")]?.takeIf { it.isNotBlank() }

    suspend fun setImageUrl(vin: String, url: String) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("img_$vin")
            if (url.isBlank()) it.remove(key) else it[key] = url.trim()
        }
    }

    // --- Per-car seat capability (the API has no reliable flags) ---------

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
        context.settingsDataStore.edit { it[booleanPreferencesKey("seat_${field}_$vin")] = value }
    }

    // --- First-run onboarding -------------------------------------------

    suspend fun onboardingSeen(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("onboarding_seen")] ?: false

    suspend fun setOnboardingSeen() {
        context.settingsDataStore.edit { it[booleanPreferencesKey("onboarding_seen")] = true }
    }

    /** True once a car has been through the feature-setup wizard. */
    suspend fun isCarConfigured(vin: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("car_configured_$vin")] ?: false

    suspend fun setCarConfigured(vin: String) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("car_configured_$vin")] = true }
    }

    // --- Per-car section order -------------------------------------------

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
        context.settingsDataStore.edit { it[stringPreferencesKey("sections_$vin")] = order.joinToString(",") }
    }

    private fun csv(p: androidx.datastore.preferences.core.Preferences, key: String): Set<String> =
        p[stringPreferencesKey(key)]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun collapsedSections(vin: String): Set<String> =
        csv(context.settingsDataStore.data.first(), "collapsed_$vin")

    suspend fun setSectionCollapsed(vin: String, section: String, collapsed: Boolean) {
        context.settingsDataStore.edit {
            val set = csv(it, "collapsed_$vin").toMutableSet()
            if (collapsed) set.add(section) else set.remove(section)
            it[stringPreferencesKey("collapsed_$vin")] = set.joinToString(",")
        }
    }

    suspend fun hiddenSections(vin: String): Set<String> =
        csv(context.settingsDataStore.data.first(), "hidden_$vin")

    suspend fun setSectionHidden(vin: String, section: String, hidden: Boolean) {
        context.settingsDataStore.edit {
            val set = csv(it, "hidden_$vin").toMutableSet()
            if (hidden) set.add(section) else set.remove(section)
            it[stringPreferencesKey("hidden_$vin")] = set.joinToString(",")
        }
    }

    // --- On-device AI ----------------------------------------------------

    suspend fun aiEnabled(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("ai_enabled")] ?: false

    suspend fun setAiEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("ai_enabled")] = value }
    }

    /** When on, AI summaries run automatically on open/refresh/command (vs only on tap). */
    suspend fun aiAuto(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("ai_auto")] ?: false

    suspend fun setAiAuto(value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("ai_auto")] = value }
    }

    // --- App-icon shortcut selection -------------------------------------

    /** Enabled shortcut ids ("cmd_vin"); null = never customised (show all). */
    suspend fun enabledShortcuts(): Set<String>? {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("enabled_shortcuts")] ?: return null
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setEnabledShortcuts(ids: Set<String>) {
        context.settingsDataStore.edit { it[stringPreferencesKey("enabled_shortcuts")] = ids.joinToString(",") }
    }

    // --- Quick Settings tiles --------------------------------------------

    /** Per-tile assignment: (vin, command) or null if unassigned. */
    suspend fun tileConfig(index: Int): Pair<String, String>? {
        val p = context.settingsDataStore.data.first()
        val vin = p[stringPreferencesKey("tile_${index}_vin")]?.takeIf { it.isNotBlank() } ?: return null
        val cmd = p[stringPreferencesKey("tile_${index}_cmd")]?.takeIf { it.isNotBlank() } ?: return null
        return vin to cmd
    }

    suspend fun setTileConfig(index: Int, vin: String?, cmd: String?) {
        context.settingsDataStore.edit {
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
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("tile_${index}_label")
            if (label.isNullOrBlank()) it.remove(k) else it[k] = label.trim()
        }
    }

    /** What the climate tile runs: "default", "smart", or a preset id. */
    suspend fun tileClimateTarget(index: Int): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_${index}_climate")]
            ?.takeIf { it.isNotBlank() } ?: "default"

    suspend fun setTileClimateTarget(index: Int, target: String?) {
        context.settingsDataStore.edit {
            val k = stringPreferencesKey("tile_${index}_climate")
            if (target.isNullOrBlank()) it.remove(k) else it[k] = target
        }
    }

    /** When true, tiles run the command in the background; else they open the app. */
    suspend fun tileBackground(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("tile_background")] ?: false

    suspend fun setTileBackground(value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("tile_background")] = value }
    }

    /** When true, a tile kicks a throttled status refresh when it becomes visible,
     *  so its lock/climate state stays live (at some battery/rate-limit cost). */
    suspend fun tileLiveRefresh(): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("tile_live_refresh")] ?: false

    suspend fun setTileLiveRefresh(value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("tile_live_refresh")] = value }
    }

    /** Last time a tile-driven refresh ran for [vin] (epoch ms), for throttling. */
    suspend fun tileRefreshedAt(vin: String): Long =
        context.settingsDataStore.data.first()[stringPreferencesKey("tile_refreshed_$vin")]?.toLongOrNull() ?: 0L

    suspend fun setTileRefreshedAt(vin: String, value: Long) {
        context.settingsDataStore.edit { it[stringPreferencesKey("tile_refreshed_$vin")] = value.toString() }
    }

    // --- Home-screen widgets -------------------------------------------------

    /** Per-widget assignment: (pinned vin, ordered action keys) or null. */
    suspend fun widgetConfig(widgetId: Int): Pair<String, List<String>>? {
        val p = context.settingsDataStore.data.first()
        val vin = p[stringPreferencesKey("widget_${widgetId}_vin")]?.takeIf { it.isNotBlank() } ?: return null
        val actions = p[stringPreferencesKey("widget_${widgetId}_actions")]
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return vin to actions
    }

    suspend fun setWidgetConfig(widgetId: Int, vin: String, actions: List<String>) {
        context.settingsDataStore.edit {
            it[stringPreferencesKey("widget_${widgetId}_vin")] = vin
            it[stringPreferencesKey("widget_${widgetId}_actions")] = actions.joinToString(",")
        }
    }

    suspend fun clearWidgetConfig(widgetId: Int) {
        context.settingsDataStore.edit {
            // Remove every per-widget key so a re-used widget id starts clean.
            listOf("vin", "actions", "pending", "auth", "photobg", "loc", "addr", "lat", "lon").forEach { suffix ->
                it.remove(stringPreferencesKey("widget_${widgetId}_$suffix"))
                it.remove(booleanPreferencesKey("widget_${widgetId}_$suffix"))
            }
        }
    }

    suspend fun widgetPendingAction(widgetId: Int): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_pending")]?.takeIf { it.isNotBlank() }

    suspend fun setWidgetPendingAction(widgetId: Int, action: String?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("widget_${widgetId}_pending")
            if (action.isNullOrBlank()) it.remove(key) else it[key] = action
        }
    }

    suspend fun widgetRequireAuth(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_auth")] ?: true

    suspend fun setWidgetRequireAuth(widgetId: Int, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("widget_${widgetId}_auth")] = value }
    }

    /** Use the car's set photo as a full-bleed widget background (default off). */
    suspend fun widgetPhotoBackground(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_photobg")] ?: false

    suspend fun setWidgetPhotoBackground(widgetId: Int, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("widget_${widgetId}_photobg")] = value }
    }

    /** Widget background transparency level (0 = opaque, 9 = very transparent). */
    suspend fun widgetBackgroundAlpha(widgetId: Int): Int =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_alpha")]
            ?.toIntOrNull() ?: 0

    suspend fun setWidgetBackgroundAlpha(widgetId: Int, value: Int) {
        context.settingsDataStore.edit { it[stringPreferencesKey("widget_${widgetId}_alpha")] = value.coerceIn(0, 9).toString() }
    }

    /** Show a map/location box on large widgets (default off). */
    suspend fun widgetShowLocation(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_loc")] ?: false

    suspend fun setWidgetShowLocation(widgetId: Int, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("widget_${widgetId}_loc")] = value }
    }

    /** Much more rounded corners (pill-like) for small widgets (default false). */
    suspend fun widgetPillShape(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey("widget_${widgetId}_pill")] ?: false

    suspend fun setWidgetPillShape(widgetId: Int, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("widget_${widgetId}_pill")] = value }
    }

    /** Layout preference: "info" (show percent/range) or "controls" (show buttons). */
    suspend fun widgetLayoutMode(widgetId: Int): String =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_layout")]
            ?: "info"

    suspend fun setWidgetLayoutMode(widgetId: Int, value: String) {
        context.settingsDataStore.edit { it[stringPreferencesKey("widget_${widgetId}_layout")] = value.takeIf { it in setOf("info", "controls") } ?: "info" }
    }

    /** Drive URI for auto-backup; null when not configured. */
    suspend fun syncUri(): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_uri")]?.takeIf { it.isNotBlank() }

    suspend fun setSyncUri(uri: String?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("sync_uri")
            if (uri.isNullOrBlank()) it.remove(key) else it[key] = uri
        }
    }

    /** Timestamp (ms) of the last successful bidirectional sync. */
    suspend fun lastSyncMs(): Long =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_last_ms")]
            ?.toLongOrNull() ?: 0L

    suspend fun setLastSyncMs(ms: Long) {
        context.settingsDataStore.edit { it[stringPreferencesKey("sync_last_ms")] = ms.toString() }
    }

    /** Wi-Fi only sync (true) or any network (false). */
    suspend fun syncWifiOnly(): Boolean =
        context.settingsDataStore.data.first()[stringPreferencesKey("sync_wifi")]
            ?.toBooleanStrictOrNull() ?: true

    suspend fun setSyncWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[stringPreferencesKey("sync_wifi")] = value.toString() }
    }

    // The car's last known address + coordinates, refreshed by the Location action
    // and rendered in the widget's location box.
    suspend fun widgetLocationAddress(widgetId: Int): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("widget_${widgetId}_addr")]?.takeIf { it.isNotBlank() }

    suspend fun setWidgetLocationAddress(widgetId: Int, address: String?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("widget_${widgetId}_addr")
            if (address.isNullOrBlank()) it.remove(key) else it[key] = address
        }
    }

    suspend fun widgetLocationLatLon(widgetId: Int): Pair<Double, Double>? {
        val data = context.settingsDataStore.data.first()
        val lat = data[stringPreferencesKey("widget_${widgetId}_lat")]?.toDoubleOrNull() ?: return null
        val lon = data[stringPreferencesKey("widget_${widgetId}_lon")]?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    suspend fun setWidgetLocationLatLon(widgetId: Int, lat: Double?, lon: Double?) {
        context.settingsDataStore.edit {
            val latKey = stringPreferencesKey("widget_${widgetId}_lat")
            val lonKey = stringPreferencesKey("widget_${widgetId}_lon")
            if (lat != null) it[latKey] = lat.toString() else it.remove(latKey)
            if (lon != null) it[lonKey] = lon.toString() else it.remove(lonKey)
        }
    }

    // --- Dual-column "hot spot" (pebble pinned under the car-info column) -----

    suspend fun hotspot(vin: String): String? =
        context.settingsDataStore.data.first()[stringPreferencesKey("hotspot_$vin")]?.takeIf { it.isNotBlank() }

    suspend fun setHotspot(vin: String, section: String?) {
        context.settingsDataStore.edit {
            val key = stringPreferencesKey("hotspot_$vin")
            if (section.isNullOrBlank()) it.remove(key) else it[key] = section
        }
    }

    // --- Per-car powertrain override -------------------------------------

    suspend fun powertrain(vin: String): Powertrain? =
        context.settingsDataStore.data.first()[stringPreferencesKey("ptrain_$vin")]
            ?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }

    suspend fun setPowertrain(vin: String, value: Powertrain) {
        context.settingsDataStore.edit { it[stringPreferencesKey("ptrain_$vin")] = value.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setFontChoice(choice: FontChoice) {
        context.settingsDataStore.edit { it[Keys.FONT] = choice.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled.toString() }
    }

    suspend fun setColorPalette(palette: ColorPalette) {
        context.settingsDataStore.edit { it[Keys.PALETTE] = palette.name }
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
        context.settingsDataStore.edit {
            it[stringPreferencesKey("climate_$vin")] = climateJson.encodeToString(ClimateRequest.serializer(), req)
        }
    }

    /** User-named climate presets for a car. */
    suspend fun climatePresets(vin: String): List<ClimatePreset> {
        val raw = context.settingsDataStore.data.first()[stringPreferencesKey("climate_presets_$vin")] ?: return emptyList()
        return runCatching { climateJson.decodeFromString(presetListSerializer, raw) }.getOrElse { emptyList() }
    }

    suspend fun saveClimatePreset(vin: String, preset: ClimatePreset) {
        val existing = climatePresets(vin).toMutableList()
        val idx = existing.indexOfFirst { it.id == preset.id }
        if (idx >= 0) existing[idx] = preset else existing.add(preset)
        context.settingsDataStore.edit {
            it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, existing)
        }
    }

    suspend fun deleteClimatePreset(vin: String, id: String) {
        val updated = climatePresets(vin).filter { it.id != id }
        context.settingsDataStore.edit {
            it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, updated)
        }
    }

    /** Persist a full, reordered preset list for a car. */
    suspend fun setClimatePresets(vin: String, presets: List<ClimatePreset>) {
        context.settingsDataStore.edit {
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
        context.settingsDataStore.edit {
            it[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
        }
    }

    /** Remove a custom palette; clears the active id if it matches. */
    suspend fun deleteCustomPalette(id: String) {
        val updated = readCustomPalettes().filter { it.id != id }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
            if (prefs[Keys.ACTIVE_CUSTOM_PALETTE_ID] == id) prefs.remove(Keys.ACTIVE_CUSTOM_PALETTE_ID)
        }
    }

    /** Set which custom palette is active (null = use a built-in palette). */
    suspend fun setActiveCustomPaletteId(id: String?) {
        context.settingsDataStore.edit {
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
        context.settingsDataStore.edit {
            if (updated.isEmpty()) it.remove(Keys.CAR_PALETTE_IDS)
            else it[Keys.CAR_PALETTE_IDS] = paletteJson.encodeToString(carPaletteSerializer, updated)
        }
    }

    /** Clear all per-car palette overrides at once (e.g. when reverting to dynamic color). */
    suspend fun clearAllCarPaletteIds() {
        context.settingsDataStore.edit { it.remove(Keys.CAR_PALETTE_IDS) }
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
        context.settingsDataStore.edit {
            it[Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, merged)
        }
        return null
    }

    // --- Full settings backup --------------------------------------------

    private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Export every app preference (theme, colours and custom palettes, weather,
     * notifications, tiles, per-car config…) as one portable JSON backup. Values
     * keep their type (string or boolean) so a re-import restores them exactly.
     * Note: account credentials live in a separate store and are never included.
     */
    suspend fun exportSettingsJson(): String {
        val prefs = context.settingsDataStore.data.first()
        val entries = buildJsonObject {
            prefs.asMap().forEach { (key, value) ->
                when (value) {
                    is Boolean -> put(key.name, JsonPrimitive(value))
                    is String -> put(key.name, JsonPrimitive(value))
                    else -> put(key.name, JsonPrimitive(value.toString()))
                }
            }
        }
        val root = buildJsonObject {
            put("_format", JsonPrimitive("bloo-settings"))
            put("_version", JsonPrimitive(1))
            put("prefs", entries)
        }
        return backupJson.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Restore settings from a backup produced by [exportSettingsJson], overwriting
     * any matching keys. Returns an error message on failure, or null on success.
     */
    suspend fun importSettingsJson(json: String): String? {
        val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }
            .getOrElse { return "Invalid settings file" }
        if (root["_format"]?.jsonPrimitive?.contentOrNull != "bloo-settings") {
            return "Not a Bloo settings backup"
        }
        val prefs = root["prefs"]?.jsonObject ?: return "Settings file has no data"
        context.settingsDataStore.edit { mut ->
            prefs.forEach { (name, element) ->
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
        }
        return null
    }

    // --- Weather ---------------------------------------------------------

    /** Set or clear the weather location. Passing null lat/lon clears it. */
    suspend fun setWeatherLocation(lat: Double?, lon: Double?, label: String?) {
        context.settingsDataStore.edit {
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

    suspend fun setUseFahrenheit(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.WEATHER_FAHRENHEIT] = value.toString() }
    }
}
