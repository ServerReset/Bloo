package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    /** DataItem path: watch → phone, local display preferences (font/UI scale)
     *  so a change made on the watch syncs back to the phone immediately. */
    const val PATH_LOCAL = "/bloo/local"

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

    fun decodeState(raw: String?): WearStatePayload =
        raw?.let { runCatching { json.decodeFromString(WearStatePayload.serializer(), it) }.getOrNull() }
            ?: WearStatePayload()

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

    /** Re-fetch a single car's status (or all, when [WearCommand.vin] is blank). */
    const val REFRESH = "refresh"

    /** Request the phone to generate and push an AI summary for a car. */
    const val AI_SUMMARY = "ai_summary"
}

/** A command the watch wants run for one car. */
@Serializable
data class WearCommand(
    val vin: String,
    val action: String,
    /** Climate setpoint to use for [WearAction.CLIMATE_ON]/[WearAction.TOGGLE_CLIMATE]. */
    val tempF: Int = 72,
    val durationMinutes: Int = 10,
    val defrost: Boolean = false,
)

/** The phone's reply after attempting a [WearCommand]. */
@Serializable
data class WearCommandResult(
    val vin: String,
    val action: String,
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
    val uiScale: Float = 1f,
    val colors: WearColorRoles? = null,
    /** Per-VIN resolved colours for cars with a custom palette override. */
    val carColors: Map<String, WearColorRoles> = emptyMap(),
    /** Per-VIN pebble (detail-section) order, so the watch lays its tiles out in
     *  the same order as each car on the phone. */
    val pebbleOrders: Map<String, List<String>> = emptyMap(),
)

/** Watch-local display preferences synced back to the phone (watch → phone). */
@Serializable
data class WearLocalPayload(
    val uiScale: Float = 1f,
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
