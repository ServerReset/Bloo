package com.bloo.bluelink.data

import android.content.Context

/**
 * The phone's way into a vehicle command. The phone's own names and climate targets (a saved preset, "smart", a temperature, "default") are
 * resolved here, before the account lock, and the command then goes through [CarCommandRunner]: the one command path the watch and the
 * widgets share. This only worded the result the way the phone's toasts say it.
 */
object VehicleCommandRunner {
    /**
     * Marks a climate target that carries an explicit temperature, e.g. "temp:64". See [resolveClimate].
     */
    const val TEMP_PREFIX = "temp:"

    /** Appended to a [TEMP_PREFIX] target to also run the defroster. */
    const val DEFROST_SUFFIX = ":defrost"

    /**
     * Outcome of a single command: whether it succeeded, plus a short human-readable status/error
     * message suitable for a toast or log line.
     */
    data class Result(val ok: Boolean, val message: String)

    /** Executes one phone command end to end and returns the user-facing [Result]. */
    suspend fun run(ctx: Context, vin: String, cmd: String, climateTarget: String): Result {
        val action = actionFor(cmd) ?: return Result(true, "Done")
        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
            ?: return Result(false, "Car not found")
        val command = runCatching { commandFor(ctx, action, snap, climateTarget) }
            .getOrElse { return Result(false, it.message ?: "Command failed") }
        val outcome = CarCommandRunner.execute(ctx, command)
        if (!outcome.ok) return Result(false, outcome.message ?: "Command failed")
        return Result(true, announce(action, snap, command))
    }

    /** The shared command a phone command name stands for, or null for a name the phone does not send. */
    private fun actionFor(cmd: String): String? = when (cmd) {
        "doors" -> CarAction.TOGGLE_LOCK
        CarCommandIds.LOCK -> CarAction.LOCK
        CarCommandIds.UNLOCK -> CarAction.UNLOCK
        "charge" -> CarAction.TOGGLE_CHARGE
        CarCommandIds.CHARGE_ON -> CarAction.CHARGE_ON
        CarCommandIds.CHARGE_OFF -> CarAction.CHARGE_OFF
        "climate" -> CarAction.TOGGLE_CLIMATE
        CarCommandIds.CLIMATE_ON -> CarAction.CLIMATE_ON
        CarCommandIds.CLIMATE_OFF -> CarAction.CLIMATE_OFF
        CarCommandIds.LIGHTS -> CarAction.FLASH_LIGHTS
        CarCommandIds.HORN -> CarAction.HORN_AND_LIGHTS
        "charge_limit" -> CarAction.SET_CHARGE_LIMITS
        else -> null
    }

    /**
     * The command the shared runner takes. Everything the phone owns is worked out here, before the lock: the climate settings a target
     * names, the weather a smart target needs, and the charge-limit check. A climate toggle that is going to stop the climate needs none of it.
     */
    private suspend fun commandFor(ctx: Context, action: String, snap: VehicleSnapshot, target: String): CarCommand {
        val base = CarCommand(vin = snap.vin, action = action)
        val v = snap.toVehicle()
        return when (action) {
            CarAction.SET_CHARGE_LIMITS -> {
                // Canada can't read its charge targets, so it must not write one either: setChargeTargets would POST the unverified
                // evc/setsoc "level" field.
                if (!v.brand.supportsChargeLimits) error("Charge limit isn't available on ${v.name}")
                val pct = target.toIntOrNull()?.coerceIn(CHARGE_LIMIT_RANGE) ?: error("Bad charge limit")
                base.copy(acLimit = pct, dcLimit = pct)
            }
            CarAction.CLIMATE_ON -> base.withClimate(resolveClimate(ctx, snap, target))
            CarAction.TOGGLE_CLIMATE ->
                if (snap.climateOn == true) base else base.withClimate(resolveClimate(ctx, snap, target))
            else -> base
        }
    }

    /**
     * The climate settings a phone target names. Refuses a start while driving first, as the car does, so no weather is fetched for a start
     * that cannot happen.
     */
    private suspend fun resolveClimate(ctx: Context, snap: VehicleSnapshot, target: String): ClimateRequest {
        if (snap.isDriving) error("Can't start climate while driving")
        val v = snap.toVehicle()
        return when {
            target == "smart" -> smartRequest(ctx, snap)
            target.startsWith(TEMP_PREFIX) -> {
                // "temp:64" or "temp:82:defrost". Suffix rather than a second prefix so the two can be asked for together, which is what
                // "defrost the windscreen" actually wants: heat AND defrost.
                val body = target.removePrefix(TEMP_PREFIX)
                val defrost = body.endsWith(DEFROST_SUFFIX)
                val f = body.removeSuffix(DEFROST_SUFFIX).toIntOrNull() ?: error("Bad temperature")
                ClimateRequest(
                    tempF = f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last),
                    defrost = defrost,
                    durationMinutes = DEFAULT_CLIMATE_DURATION_MIN,
                )
            }
            target != "default" -> {
                val preset = SettingsStore(ctx).climatePresets(v.vin).firstOrNull { it.id == target } ?: error("Preset unavailable")
                preset.request
            }
            else -> ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)
        }
    }

    /** The smart target: the weather where the car is parked, through the same request the watch's smart climate builds. */
    private suspend fun smartRequest(ctx: Context, snap: VehicleSnapshot): ClimateRequest {
        val lat = snap.lat ?: error("No location for smart climate")
        val lon = snap.lon ?: error("No location for smart climate")
        val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
        return smartClimateRequest(w, ClimateChoices.list(ctx, snap.vin))
    }

    /** The success wording the phone's toasts have always used, from the state the command started in. */
    private fun announce(action: String, snap: VehicleSnapshot, command: CarCommand): String {
        val name = snap.toVehicle().name
        return when (action) {
            CarAction.TOGGLE_LOCK -> if (snap.locked == true) "Unlocking $name" else "Locking $name"
            CarAction.LOCK -> "Locking $name"
            CarAction.UNLOCK -> "Unlocking $name"
            CarAction.TOGGLE_CHARGE -> if (snap.charging == true) "Stopping charge" else "Starting charge"
            CarAction.CHARGE_ON -> "Starting charge"
            CarAction.CHARGE_OFF -> "Stopping charge"
            CarAction.TOGGLE_CLIMATE -> if (snap.climateOn == true) "Stopping climate" else "Starting climate"
            CarAction.CLIMATE_ON -> "Starting climate"
            CarAction.CLIMATE_OFF -> "Stopping climate"
            CarAction.FLASH_LIGHTS -> "Flashing lights on $name"
            CarAction.HORN_AND_LIGHTS -> "Sounding horn on $name"
            CarAction.SET_CHARGE_LIMITS -> "Charge limit set to ${command.acLimit}% on $name"
            else -> "Done"
        }
    }
}
