package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What an account lists about how a car looks: its paint colour and trim, either of which may be absent. */
internal data class VehiclePaint(val colour: String?, val trim: String?)

/**
 * Reads the paint colour and trim off one account car, whatever the brand calls them: any text field whose key names a colour
 * (`exteriorColor`, `colorName`, `outColor`, ...) or a trim, in the car's own object or one level inside it (Europe nests them
 * under `detailInfo`). A readable name wins over a code; a field that is missing, blank or not text is ignored.
 */
internal fun paintOf(car: JsonObject): VehiclePaint {
    val fields = car.entries.flatMap { (k, v) ->
        if (v is JsonObject) v.entries.map { (nk, nv) -> nk to nv } else listOf(k to v)
    }.mapNotNull { (k, v) -> ((v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })?.let { k.lowercase() to it } }
    val colours = fields.filter { (k, _) -> "colo" in k && "inner" !in k && !k.startsWith("in") && "interior" !in k }
    val colour = (colours.firstOrNull { (k, _) -> "code" !in k } ?: colours.firstOrNull())?.second
    val trim = fields.firstOrNull { (k, _) -> k == "trim" || k.endsWith("trim") || k == "trimname" }?.second
    return VehiclePaint(colour, trim)
}

/** Logs which fields an account car lists (names only) and its colour and trim (not private, and no VIN), so a picture can be matched. */
internal fun logAccountCar(car: JsonObject) {
    val paint = paintOf(car)
    AppLog.log("Account vehicle fields (names only): ${car.keys.sorted().joinToString()}")
    AppLog.log("Account vehicle paint: colour=${paint.colour ?: "none"}, trim=${paint.trim ?: "none"}")
}
