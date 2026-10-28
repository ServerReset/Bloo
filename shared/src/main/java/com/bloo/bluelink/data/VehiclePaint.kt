package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What an account lists about how a car looks: its paint colour and trim, either of which may be absent. */
internal data class VehiclePaint(val colour: String?, val trim: String?, val model: String? = null, val colourCode: String? = null)

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
    // Hyundai US names no model, only these: the first that is present says which car it is ("IONIQ 5").
    val model = listOf("modelname", "hmamodel", "series", "model", "modelcode").firstNotNullOfOrNull { want -> fields.firstOrNull { it.first == want }?.second }
    val colourCode = colours.firstOrNull { (k, _) -> "code" in k }?.second
    return VehiclePaint(colour, trim, model, colourCode)
}

/** Logs which fields an account car lists (names only) and its colour and trim (not private, and no VIN), so a picture can be matched. */
/**
 * One entry of an account's vehicle list: its car object and its vehicle id, logged for the account's own diagnostics. Null when the entry
 * is not a car or has no vehicle id. Canada and Europe read their lists this way.
 */
internal fun accountCar(entry: JsonElement): Pair<JsonObject, String>? {
    val o = entry.obj() ?: return null
    val id = o["vehicleId"]?.str() ?: return null
    logAccountCar(o)
    return o to id
}

internal fun logAccountCar(car: JsonObject) {
    val paint = paintOf(car)
    AppLog.log("Account vehicle fields (names only): ${car.keys.sorted().joinToString()}")
    AppLog.log("Account vehicle paint: colour=${paint.colour ?: "none"}, code=${paint.colourCode ?: "none"}, trim=${paint.trim ?: "none"}, model=${paint.model ?: "none"}")
}

/** A car's model line: what the account called it, or, when that is only a year (Hyundai US lists no model name), the year and [hint]. */
internal fun modelWithName(model: String, hint: String?): String =
    if (hint.isNullOrBlank() || model.any(Char::isLetter)) model else "$model ${friendlyModel(hint)}".trim()

/** Hyundai's and Kia's internal platform codes, as the names owners know the cars by. */
private val ModelCodes = mapOf("NE" to "IONIQ 5", "CE" to "IONIQ 6", "ME" to "IONIQ 9", "CV" to "EV6", "MV" to "EV9")

/** A model code ("NE1") as the model's name ("IONIQ 5"); anything that is not a known code is returned as it came. */
internal fun friendlyModel(hint: String): String = ModelCodes[hint.trim().uppercase().trimEnd { it.isDigit() }] ?: hint
