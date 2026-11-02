package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the car reports about charging. The charging power (kW) is not in the parsed status yet, because its field name is not in this
 * repo's samples. So, once per app session and only while the car reports it is charging, this logs every value in the car's EV status
 * (nested fields included, as name=value), so the field that carries the kW can be read from the log and mapped.
 */
private val loggedOnce = AtomicBoolean(false)

/** Every name=value pair under [element], nested objects included, up to [limit] pairs. */
internal fun chargingDump(element: JsonElement?, limit: Int = 120): List<String> {
    val out = ArrayList<String>()
    fun walk(prefix: String, e: JsonElement?) {
        if (out.size >= limit) return
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> walk(if (prefix.isEmpty()) k else "$prefix.$k", v) }
            is kotlinx.serialization.json.JsonArray -> e.forEachIndexed { i, v -> walk("$prefix[$i]", v) }
            is JsonPrimitive -> out += "$prefix=${e.content}"
            null -> {}
        }
    }
    walk("", element)
    return out
}

internal fun logChargingPowerFieldsOnce(source: String, ev: JsonElement?) {
    if (!loggedOnce.compareAndSet(false, true)) return
    if (ev !is JsonObject) return
    val pairs = chargingDump(ev).joinToString(", ").take(3000)
    AppLog.log("$source charging data (EV status, once per session): $pairs")
}
