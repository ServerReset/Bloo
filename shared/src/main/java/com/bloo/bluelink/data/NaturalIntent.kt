package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the on-device model made of a free-form request: one [intent] from the fixed list, the clock [time] it names as "HH:MM" (or null),
 * and the [car] it names (or null). The model only classifies. The app then runs the intent through the same path a typed command takes,
 * so an unknown or garbled answer can do nothing rather than the wrong thing.
 */
@Serializable
data class NaturalIntent(val intent: String = "none", val time: String? = null, val car: String? = null)

/** The intents the model may answer with. Anything else is treated as [NaturalIntent] "none". */
val NATURAL_INTENTS = setOf("climate_on", "climate_off", "lock", "unlock", "departure", "none")

/**
 * The prompt for the on-device model: the request, the time now, and the car names it may refer to. It asks for JSON only, with the
 * time already in 24-hour form, so the app never has to guess an am or pm the model left out.
 */
fun naturalIntentPrompt(query: String, nowHhmm: String, carNames: List<String>): String = """
You classify a request about a car into JSON. Reply with the JSON object only.
Intents: climate_on (start the climate), climate_off (stop the climate), lock, unlock, departure (have the car ready for a time, which sets the pre-run), none (anything else).
Reply shape: {"intent": "<one intent>", "time": "HH:MM" in 24-hour time or null, "car": "<one of the car names>" or null}
Use the 24-hour clock: 9 p.m. is "21:00", 9 in the morning is "09:00". Use null for time when the request names none.
Car names: ${carNames.joinToString(", ").ifBlank { "none" }}
Time now: $nowHhmm
Request: $query
""".trimIndent()

private val naturalJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Reads the model's reply. The reply is found by its braces, so a sentence around the JSON is ignored; an unknown intent becomes "none".
 * Null when there is no JSON to read.
 */
fun parseNaturalIntent(reply: String): NaturalIntent? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val parsed = runCatching { naturalJson.decodeFromString<NaturalIntent>(reply.substring(start, end + 1)) }.getOrNull() ?: return null
    val intent = parsed.intent.trim().lowercase()
    return NaturalIntent(
        intent = if (intent in NATURAL_INTENTS) intent else "none",
        time = parsed.time?.trim()?.takeIf { parseClockMinute(it) != null },
        car = parsed.car?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
    )
}
