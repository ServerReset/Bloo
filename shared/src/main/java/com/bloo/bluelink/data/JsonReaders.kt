package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/*
 * The JSON readers every vehicle API shares. Hyundai/Genesis (EU, Canada), Kia and the others each
 * carried their own private copy of these; they read the same shapes the same way, so there is one.
 * Every one returns null for a missing or wrongly typed value instead of throwing, because the
 * services leave fields out or change their type from one model year to the next.
 */

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

/** A String, treating the literal JSON string "null" like an absent value (some services send it that way). */
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

/** An Int that also accepts a decimal number, truncating it (the EU service mixes the two). */
internal fun JsonElement?.intLoose(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

internal fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull

internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/** A Boolean that tolerates mixed encodings: true/false or 0/1. */
internal fun JsonElement?.flag(): Boolean? =
    (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { v -> v != 0 } }

/** Descends through nested objects and arrays by key (a numeric key indexes an array). */
internal fun JsonElement?.path(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = when (cur) {
            is JsonObject -> cur[k]
            is JsonArray -> k.toIntOrNull()?.let { cur.getOrNull(it) }
            else -> null
        }
        if (cur == null) return null
    }
    return cur
}

/** A distance the service reports in km, as miles: the app stores miles everywhere and converts to
 *  km only at display time (see FormatUtils.formatDistance). Divides by [KM_PER_MI] so the two
 *  directions are exact inverses. */
internal fun Double.kmToMi(): Double = this / KM_PER_MI
