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

    /** Message path: watch → phone, "run this command". */
    const val PATH_COMMAND = "/bloo/command"

    /** Message path: watch → phone, "push me fresh state" (optionally refreshing). */
    const val PATH_SYNC_REQUEST = "/bloo/sync_request"

    /** Message path: phone → watch, "I just executed a command, here's the result". */
    const val PATH_COMMAND_RESULT = "/bloo/command_result"

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
