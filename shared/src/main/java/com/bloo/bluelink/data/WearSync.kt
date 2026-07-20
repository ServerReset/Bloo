package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The wire protocol shared by the phone ([:app]) and the watch ([:wear]) over
 * the Wearable Data Layer.
 *
 * Two channels:
 *  - **State** ([PATH_STATE]) — the phone publishes a [WearStatePayload] (the
 *    same compact [VehicleSnapshot] list the widgets/tiles use) as a DataItem;
 *    the watch mirrors it so its tiles render instantly.
 *  - **Commands / sync** ([PATH_COMMAND], [PATH_SYNC_REQUEST]) — the watch sends
 *    a [WearCommand] as a Message; the phone executes it with its already
 *    authenticated repository (token refresh, PIN, etc. all live on the phone).
 *
 * Everything is plain JSON so neither side needs the Play-Services Wearable
 * types to (de)serialize, and the format is identical to what the phone already
 * persists in [SnapshotStore].
 */
object WearSync {

    /** DataItem path: phone → watch snapshot of every car. */
    const val PATH_STATE = "/bloo/state"

    /** DataItem path: phone → watch session tokens, so the watch can talk to the
     *  car backend directly (standalone) when the phone is unreachable. Sent as a
     *  separate, rarely-changing item so it isn't re-published on every refresh. */
    const val PATH_AUTH = "/bloo/auth"

    /** DataItem path: phone → watch appearance + preferences (resolved theme
     *  colours, temperature unit, UI scale), so the watch mirrors the phone's
     *  look and settings and updates live when they change. */
    const val PATH_SETTINGS = "/bloo/settings"

    /** DataItem path: phone → watch saved climate presets, keyed by VIN. */
    const val PATH_PRESETS = "/bloo/presets"

    /** DataItem path: *bidirectional* live climate draft (slider positions, the
     *  active preset) keyed by VIN. Either side writes it when the user changes a
     *  control or applies/toggles a preset, and both mirror the other's. */
    const val PATH_CLIMATE = "/bloo/climate"

    /** DataItem path: phone → watch extras (weather, car photo URLs, AI
     *  summaries) so the watch reaches fuller parity with the phone. */
    const val PATH_EXTRAS = "/bloo/extras"

    /** DataItem path: watch → phone, a car's reordered pebble order. The watch
     *  reorders by pebble group; the phone persists it as that car's section
     *  order (and re-publishes it back in [PATH_SETTINGS]). */
    const val PATH_PEBBLE_ORDER = "/bloo/pebble_order"

    /** Message path: watch → phone, "run this command". */
    const val PATH_COMMAND = "/bloo/command"

    /** Message path: watch → phone, "push me fresh state" (optionally refreshing). */
    const val PATH_SYNC_REQUEST = "/bloo/sync_request"

    /** Message path: phone → watch, "I just executed a command, here's the result". */
    const val PATH_COMMAND_RESULT = "/bloo/command_result"

    /** Message path: phone → watch, "I just attempted the Drive sync you asked
     *  for, here's how it went". */
    const val PATH_SYNC_RESULT = "/bloo/sync_result"

    /** Message path: phone → watch, "I just attempted the AI summary you asked
     *  for, here's how it went" (sent on both success and failure, so the
     *  watch's busy spinner always resolves). */
    const val PATH_AI_RESULT = "/bloo/ai_result"

    /** DataItem path: watch → phone, local display preferences (font/UI scale)
     *  so a change made on the watch syncs back to the phone immediately. */
    const val PATH_LOCAL = "/bloo/local"

    /** DataItem path: watch → phone, "turn AI summaries on/off" -- kept separate
     *  from [PATH_LOCAL] so toggling it can never race with (or get overwritten
     *  by) that path's own uiScale echo. */
    const val PATH_AI_TOGGLE = "/bloo/ai_toggle"

    /** DataItem path: watch → phone, "turn the aurora background on/off" --
     *  its own path for the same reason as [PATH_AI_TOGGLE]. */
    const val PATH_AURORA_TOGGLE = "/bloo/aurora_toggle"

    /** DataMap / message key holding the JSON body. */
    const val KEY_PAYLOAD = "payload"

    /** DataMap key holding a monotonic timestamp so identical state still
     *  publishes as a *changed* DataItem (the Data Layer dedupes byte-identical
     *  items otherwise). */
    const val KEY_TIMESTAMP = "ts"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeState(payload: WearStatePayload): String =
        json.encodeToString(WearStatePayload.serializer(), payload)

    fun decodeState(raw: String?): WearStatePayload {
        if (raw == null) return WearStatePayload()
        // Fast path: whole payload decodes cleanly.
        runCatching { json.decodeFromString(WearStatePayload.serializer(), raw) }.getOrNull()?.let { return it }
        // Recovery: if one vehicle is malformed or version-skewed (e.g. the phone
        // updated to a schema the watch doesn't have yet), decode the list
        // element-by-element so the watch/tiles keep every car that still parses
        // instead of blanking all of them.
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val vehicles = obj["vehicles"]?.jsonArray?.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement(VehicleSnapshot.serializer(), el) }.getOrNull()
            } ?: emptyList()
            WearStatePayload(
                vehicles = vehicles,
                selectedVin = obj["selectedVin"]?.jsonPrimitive?.contentOrNull,
                producedAt = obj["producedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.getOrDefault(WearStatePayload())
    }

    fun encodeAuth(bundle: WearAuthBundle): String =
        json.encodeToString(WearAuthBundle.serializer(), bundle)

    fun decodeAuth(raw: String?): WearAuthBundle =
        raw?.let { runCatching { json.decodeFromString(WearAuthBundle.serializer(), it) }.getOrNull() }
            ?: WearAuthBundle()

    fun encodeSettings(settings: WearSettingsPayload): String =
        json.encodeToString(WearSettingsPayload.serializer(), settings)

    fun decodeSettings(raw: String?): WearSettingsPayload? =
        raw?.let { runCatching { json.decodeFromString(WearSettingsPayload.serializer(), it) }.getOrNull() }

    fun encodePresets(presets: WearPresets): String =
        json.encodeToString(WearPresets.serializer(), presets)

    fun decodePresets(raw: String?): WearPresets =
        raw?.let { runCatching { json.decodeFromString(WearPresets.serializer(), it) }.getOrNull() }
            ?: WearPresets()

    fun encodeClimate(state: WearClimateState): String =
        json.encodeToString(WearClimateState.serializer(), state)

    fun decodeClimate(raw: String?): WearClimateState =
        raw?.let { runCatching { json.decodeFromString(WearClimateState.serializer(), it) }.getOrNull() }
            ?: WearClimateState()

    fun encodeExtras(extras: WearExtras): String =
        json.encodeToString(WearExtras.serializer(), extras)

    fun decodeExtras(raw: String?): WearExtras =
        raw?.let { runCatching { json.decodeFromString(WearExtras.serializer(), it) }.getOrNull() }
            ?: WearExtras()

    fun encodePebbleOrder(order: WearPebbleOrder): String =
        json.encodeToString(WearPebbleOrder.serializer(), order)

    fun decodePebbleOrder(raw: String?): WearPebbleOrder? =
        raw?.let { runCatching { json.decodeFromString(WearPebbleOrder.serializer(), it) }.getOrNull() }

    fun encodeLocal(payload: WearLocalPayload): String =
        json.encodeToString(WearLocalPayload.serializer(), payload)

    fun decodeLocal(raw: String?): WearLocalPayload? =
        raw?.let { runCatching { json.decodeFromString(WearLocalPayload.serializer(), it) }.getOrNull() }

    fun encodeCommand(command: WearCommand): String =
        json.encodeToString(WearCommand.serializer(), command)

    fun decodeCommand(raw: String?): WearCommand? =
        raw?.let { runCatching { json.decodeFromString(WearCommand.serializer(), it) }.getOrNull() }

    fun encodeResult(result: WearCommandResult): String =
        json.encodeToString(WearCommandResult.serializer(), result)

    fun decodeResult(raw: String?): WearCommandResult? =
        raw?.let { runCatching { json.decodeFromString(WearCommandResult.serializer(), it) }.getOrNull() }

    fun encodeSyncResult(result: WearSyncResult): String =
        json.encodeToString(WearSyncResult.serializer(), result)

    fun decodeSyncResult(raw: String?): WearSyncResult? =
        raw?.let { runCatching { json.decodeFromString(WearSyncResult.serializer(), it) }.getOrNull() }

    fun encodeAiResult(result: WearAiResult): String =
        json.encodeToString(WearAiResult.serializer(), result)

    fun decodeAiResult(raw: String?): WearAiResult? =
        raw?.let { runCatching { json.decodeFromString(WearAiResult.serializer(), it) }.getOrNull() }

    fun encodeAiToggle(payload: WearAiTogglePayload): String =
        json.encodeToString(WearAiTogglePayload.serializer(), payload)

    fun decodeAiToggle(raw: String?): WearAiTogglePayload? =
        raw?.let { runCatching { json.decodeFromString(WearAiTogglePayload.serializer(), it) }.getOrNull() }

    fun encodeAuroraToggle(payload: WearAuroraTogglePayload): String =
        json.encodeToString(WearAuroraTogglePayload.serializer(), payload)

    fun decodeAuroraToggle(raw: String?): WearAuroraTogglePayload? =
        raw?.let { runCatching { json.decodeFromString(WearAuroraTogglePayload.serializer(), it) }.getOrNull() }
}

/** The full car list mirrored to the watch, plus which one is selected. */
@Serializable
data class WearStatePayload(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
    /** Server/phone wall-clock when this snapshot was produced (ms). */
    val producedAt: Long = 0L,
)

/** Stable command verbs understood by both sides. */
object WearAction {
    const val TOGGLE_LOCK = "toggle_lock"
    const val LOCK = "lock"
    const val UNLOCK = "unlock"
    const val TOGGLE_CLIMATE = "toggle_climate"
    const val CLIMATE_ON = "climate_on"
    const val CLIMATE_OFF = "climate_off"
    const val TOGGLE_CHARGE = "toggle_charge"
    const val CHARGE_ON = "charge_on"
    const val CHARGE_OFF = "charge_off"

    /** Flash the hazard lights, or flash + sound the horn. Hyundai/Genesis
     *  only (see Vehicle.supportsHornLights) -- Kia's US API has neither. */
    const val FLASH_LIGHTS = "flash_lights"
    const val HORN_AND_LIGHTS = "horn_and_lights"

    /** Apply the AC/DC charge-limit targets in [WearCommand.acLimit]/[WearCommand.dcLimit]. */
    const val SET_CHARGE_LIMITS = "set_charge_limits"

    /** Re-fetch a single car's status (or all, when [WearCommand.vin] is blank). */
    const val REFRESH = "refresh"

    /** Request the phone to generate and push an AI summary for a car. */
    const val AI_SUMMARY = "ai_summary"

    /** Request the phone to import the latest settings from Google Drive and
     *  re-publish them to the watch. */
    const val DRIVE_SYNC = "drive_sync"

    /** Request the phone set its home weather location from its own device
     *  GPS (mirrors the phone Settings screen's "My location" action) — the
     *  watch has no independent weather fetch of its own, it only ever
     *  displays whatever the phone last published. */
    const val WEATHER_DEVICE_LOCATION = "weather_device_location"
}

/** A command the watch wants run for one car. */
@Serializable
data class WearCommand(
    val vin: String,
    val action: String,
    /** Climate settings to use for [WearAction.CLIMATE_ON]/[WearAction.TOGGLE_CLIMATE].
     *  Seats are [SeatLevel.apiValue] ints (0 = off) so the wire format stays flat. */
    val tempF: Int = 72,
    val durationMinutes: Int = 10,
    val defrost: Boolean = false,
    val steeringWheelHeat: Boolean = false,
    val seatFrontLeft: Int = 0,
    val seatFrontRight: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
    /** Targets for [WearAction.SET_CHARGE_LIMITS]. */
    val acLimit: Int = 80,
    val dcLimit: Int = 90,
)

/** The phone's reply after attempting a [WearCommand]. */
@Serializable
data class WearCommandResult(
    val vin: String,
    val action: String,
    val ok: Boolean,
    val message: String? = null,
)

/** The phone's reply after attempting a watch-requested Drive sync. */
@Serializable
data class WearSyncResult(
    val ok: Boolean,
    val message: String? = null,
)

/** The phone's reply after attempting a watch-requested AI summary. */
@Serializable
data class WearAiResult(
    val vin: String,
    val ok: Boolean,
    val message: String? = null,
)

/** One brand's session, mirrored to the watch for standalone operation. Maps
 *  1:1 onto [SessionStore.Session]. */
@Serializable
data class WearSessionDto(
    val brand: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val username: String,
    val pin: String,
    val deviceId: String? = null,
)

/** Every signed-in brand's session, so the watch can authenticate on its own. */
@Serializable
data class WearAuthBundle(
    val sessions: List<WearSessionDto> = emptyList(),
)

/**
 * The phone's *resolved* Material 3 role colours (packed ARGB ints), so the watch
 * mirrors the exact theme without re-running the phone's palette/vibrancy maths.
 */
@Serializable
data class WearColorRoles(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val outline: Int,
    val outlineVariant: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
)

/** Appearance + preferences mirrored to the watch. */
@Serializable
data class WearSettingsPayload(
    val dark: Boolean = true,
    val useFahrenheit: Boolean = true,
    val unitSystem: String = "imperial",
    val uiScale: Float = 1f,
    val colors: WearColorRoles? = null,
    /** Per-VIN resolved colours for cars with a custom palette override. */
    val carColors: Map<String, WearColorRoles> = emptyMap(),
    /** Per-VIN pebble (detail-section) order, so the watch lays its tiles out in
     *  the same order as each car on the phone. */
    val pebbleOrders: Map<String, List<String>> = emptyMap(),
    /** Per-VIN pebble keys the user hid on the phone, so the watch drops the
     *  matching tiles instead of still showing something the phone hides. */
    val hiddenSections: Map<String, Set<String>> = emptyMap(),
    /** Whether on-device AI summaries are turned on, mirrored so the watch can
     *  show the same toggle state and hide the AI tile when it's off. */
    val aiEnabled: Boolean = false,
    /** Whether the aurora background is on. One shared flag, same as
     *  [aiEnabled] above -- flipping it on either device (see
     *  [WearAuroraTogglePayload] for the watch's own toggle) changes both,
     *  by design, so the two stay in lockstep rather than silently diverging. */
    val auroraEnabled: Boolean = false,
    /** Aurora colour source: "complementary", "material", or "custom" -- same
     *  three modes as the phone's aurora background, mirrored so the watch's
     *  own simplified gradient (see WearAuroraBackground) picks matching
     *  colours instead of always looking like "material" mode. */
    val auroraColorMode: String = "complementary",
    /** Custom colour hex for aurora (only used when [auroraColorMode] is "custom"). */
    val auroraCustomColor: String? = null,
    /** Whether haptic feedback is on app-wide. Mirrored so a user who disabled
     *  it on the phone (e.g. finds the buzzing annoying) doesn't keep getting
     *  it on every watch tap/slider-tick/PIN-digit -- see WatchApp's root
     *  LocalHapticFeedback override. */
    val hapticsEnabled: Boolean = true,
    /** "simple" or "advanced" -- mirrors the phone's Settings view mode so the
     *  watch can hide the same power-user options the phone does, one shared
     *  choice instead of the watch showing everything unconditionally while
     *  the phone hides it. One-way (phone -> watch): the watch has no UI of
     *  its own to change this, unlike aiEnabled/auroraEnabled above. */
    val settingsMode: String = "simple",
    /** Mirrors the phone's AppViewModel.UiState.updateAvailable -- a newer CI
     *  build than what's installed, if one was found and hasn't been
     *  dismissed. One-way (phone -> watch): the watch can't download/install
     *  its own update, so it just surfaces the fact and points back to the
     *  phone. Null when there's nothing to show. */
    val updateAvailable: WearUpdateInfo? = null,
)

/** The small slice of the phone's UpdateInfo the watch actually needs to
 *  show its own "Update available" card -- see [WearSettingsPayload.updateAvailable]. */
@Serializable
data class WearUpdateInfo(
    val runNumber: Int,
    val displayTitle: String? = null,
    /** The GitHub Release page -- opened on the phone via RemoteActivityHelper
     *  when the watch's card is tapped, since the watch itself has no APK
     *  install flow. */
    val htmlUrl: String,
)

/** Watch-local display preferences synced back to the phone (watch → phone). */
@Serializable
data class WearLocalPayload(
    val uiScale: Float = 1f,
    val unitSystem: String? = null,
    /** Whether the watch's own PIN lock is on, mirrored to the phone purely so
     *  it shows up in the phone's Drive/manual settings backup (a record of
     *  the setting, for portability) -- the PIN code itself never leaves the
     *  watch, and the phone never pushes this back down to reconfigure the
     *  watch (see WearViewModel/WearPhoneService for why: the phone's synced
     *  copy can be briefly stale relative to a change the watch just made,
     *  and silently clobbering a security-lock setting from a stale echo is
     *  worse than a one-time manual re-enable after restoring a backup onto
     *  a new watch). */
    val watchPinLockEnabled: Boolean = false,
    /** "off"/"immediate"/"1min"/"5min"/"10min" -- same meaning as the phone's
     *  own LockTiming, mirrored for the same backup-record reason above. */
    val watchPinLockTiming: String = "immediate",
)

/** Watch → phone: "turn AI summaries on/off". */
@Serializable
data class WearAiTogglePayload(
    val enabled: Boolean = false,
)

/** Watch → phone: "turn the aurora background on/off", and optionally set its
 *  colour mode from the watch too. [colorMode] is null when this push is only
 *  changing [enabled] -- the phone leaves its current colour mode alone in
 *  that case, so toggling the background off and back on from the watch
 *  never resets an unrelated setting. */
@Serializable
data class WearAuroraTogglePayload(
    val enabled: Boolean = false,
    val colorMode: String? = null,
)

/** A single car's reordered pebble order, sent watch → phone. */
@Serializable
data class WearPebbleOrder(
    val vin: String,
    val order: List<String> = emptyList(),
)

/** Saved climate presets per VIN, mirrored to the watch. */
@Serializable
data class WearPresets(
    val byVin: Map<String, List<ClimatePreset>> = emptyMap(),
)

/** The live climate draft for one car, shared both ways. Seat values are
 *  [SeatLevel.apiValue] so the format is platform-neutral (the phone also has
 *  cooling levels; the watch collapses cooling to off). */
@Serializable
data class ClimateSync(
    val activePresetId: String? = null,
    val tempF: Int = 72,
    val durationMinutes: Int = 10,
    val defrost: Boolean = false,
    val steering: Boolean = false,
    val seatFrontLeft: Int = 0,
    val seatFrontRight: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
)

/** Per-VIN live climate drafts mirrored between phone and watch. */
@Serializable
data class WearClimateState(
    val byVin: Map<String, ClimateSync> = emptyMap(),
)

/** Compact current-conditions snapshot mirrored to the watch (Celsius like the
 *  phone's Weather; the watch converts using the synced unit). */
@Serializable
data class WearWeather(
    val tempC: Double,
    val feelsLikeC: Double,
    val highC: Double? = null,
    val lowC: Double? = null,
    val windKph: Double = 0.0,
    val humidity: Int? = null,
    val isDay: Boolean = true,
    val code: Int = -1,
)

/** Richer per-car content mirrored to the watch for phone parity. */
@Serializable
data class WearExtras(
    val homeWeather: WearWeather? = null,
    val carWeather: Map<String, WearWeather> = emptyMap(),
    val images: Map<String, String> = emptyMap(),
    val ai: Map<String, String> = emptyMap(),
)
