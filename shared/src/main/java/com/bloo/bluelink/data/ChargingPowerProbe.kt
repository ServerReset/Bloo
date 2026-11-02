package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The car's live charging power (kW) is not in the parsed status yet: the field's name is not in this repo's samples, so it cannot be
 * mapped with any confidence. Once per app session, while a car reports it is charging, this logs the names of its EV status fields that
 * look like power or charging, so the real name can be read from the log and mapped.
 */
private val loggedOnce = AtomicBoolean(false)

internal fun logChargingPowerFieldsOnce(source: String, ev: JsonElement?) {
    if (!loggedOnce.compareAndSet(false, true)) return
    val obj = ev as? JsonObject ?: return
    val candidates = obj.keys.filter { key -> listOf("power", "kw", "charg", "rate").any { key.contains(it, ignoreCase = true) } }
    AppLog.log("$source EV fields while charging. Power candidates: ${candidates.joinToString()}. All: ${obj.keys.joinToString()}")
}
