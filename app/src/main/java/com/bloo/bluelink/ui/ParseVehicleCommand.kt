package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.only
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.data.degLabel
import kotlin.math.max

internal fun parseVehicleCommand(query: String, metric: Boolean = false): ParsedVehicleCommand? {
    val q = query.lowercase()
    // Only for a climate start, and not when smart climate is asked (it computes its own target).
    val temp = parseClimateTemperature(q, metric)
    // degLabel owns the F<->C-and-round rule; `temp` is an Int in °F and fahrenheit = !metric.
    val tempLabel = temp?.let { degLabel(it.toString(), fahrenheit = !metric) }
    // Defrost implies climate at full heat unless the query also names a temperature.
    val wantsDefrost = RxDefrost.containsMatchIn(q)
    return when {
        // Checked first: "open Bluelink"/"open the app" launches the OEM companion app (dispatched
        // in SearchResults.kt via openApp with the vehicle's BrandLinks), not a car command.
        RxOpenApp.containsMatchIn(q) ->
            ParsedVehicleCommand("open_app", label = "Opening the app for")
        // Unlock before lock: "unlock" contains "lock".
        RxUnlock.containsMatchIn(q) ->
            ParsedVehicleCommand("unlock", label = "Unlocking")
        RxLock.containsMatchIn(q) ->
            ParsedVehicleCommand("lock", label = "Locking")
        RxSmartClimate.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", "smart", "Starting smart climate for")
        // Defrost is a start-climate request, matched before the generic stop/start patterns.
        wantsDefrost && !RxNegation.containsMatchIn(q) -> {
            val f = temp ?: CLIMATE_TEMP_RANGE_F.last
            ParsedVehicleCommand(
                "climate_on",
                VehicleCommandRunner.TEMP_PREFIX + f + VehicleCommandRunner.DEFROST_SUFFIX,
                "Defrosting",
            )
        }
        RxClimateOff
            .containsMatchIn(q) -> ParsedVehicleCommand("climate_off", label = "Stopping climate for")
        RxClimateStart.containsMatchIn(q) ->
            if (temp != null) {
                ParsedVehicleCommand(
                    "climate_on",
                    VehicleCommandRunner.TEMP_PREFIX + temp,
                    "Starting climate at $tempLabel for",
                )
            } else {
                ParsedVehicleCommand("climate_on", "default", "Starting climate for")
            }
        // Bare "heat <car> to 80": needs a real temperature, the same guard that stops "Ioniq 5"
        // reading as one.
        temp != null && RxHeatCoolVerb.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", VehicleCommandRunner.TEMP_PREFIX + temp, "Starting climate at $tempLabel for")
        // Charge limit before charge start/stop: "set the charge limit to 80" contains "charg".
        RxChargeLimit
            .containsMatchIn(q) -> {
            val pct = RxPercent.find(q)?.groupValues?.get(1)?.toIntOrNull()
            if (pct != null && pct in CHARGE_LIMIT_RANGE) {
                ParsedVehicleCommand("charge_limit", pct.toString(), "Setting charge limit to $pct% on")
            } else {
                null
            }
        }
        RxFlashLights.containsMatchIn(q) ->
            ParsedVehicleCommand("lights", label = "Flashing lights on")
        RxHorn.containsMatchIn(q) ->
            ParsedVehicleCommand("horn", label = "Sounding horn on")
        RxChargeStop.containsMatchIn(q) ->
            ParsedVehicleCommand("charge_off", label = "Stopping charge for")
        RxChargeStart
            .containsMatchIn(q) -> ParsedVehicleCommand("charge_on", label = "Starting charge for")
        else -> null
    }
}

/**
 * Every constant search/command pattern, compiled once at class init instead of per call (the token
 * splitter would otherwise compile on every keystroke). Patterns built from runtime values stay
 * inline.
 */
internal val RxColdest = Regex("coldest|as cold as|max(imum)? (cold|cool)|lowest temp|full (cold|cool)")

internal val RxWarmest = Regex("warmest|hottest|as (warm|hot) as|max(imum)? (heat|warm)|highest temp|full heat")

internal val RxTempAtTo = Regex("\\b(?:at|to)\\s*(\\d{2,3})\\s*°?\\s*([fc])?\\b")

internal val RxTempDegrees = Regex("\\b(\\d{2,3})\\s*°?\\s*(?:degrees?\\b|([fc])\\b)")

internal val RxDefrost = Regex("defrost|defog|demist|clear (the )?(wind(screen|shield)|glass|ice)|de-ice")

// "open" plus a known companion-app name/alias or "the app"/"my app"; not bare "open" (that is
// RxUnlock's territory) or bare "app". Brand names are lowercase and kept in sync with
// BrandLinks.appName (Brand.kt) by hand.
internal val RxOpenApp = Regex(
    "\\bopen\\b.*(bluelink|kia access|kia connect|uvo|genesis( app| connected)?|" +
        "\\bthe app\\b|\\bmy app\\b|\\bowner('?s)? app\\b|\\bcar app\\b|\\bcompanion app\\b)",
)

internal val RxUnlock = Regex("\\bunlock\\b|\\bopen (the |my )?(car|doors?)\\b|let me in")

internal val RxLock = Regex("\\block\\b|secure (the |my )?car|lock (it|up)\\b")

internal val RxSmartClimate = Regex("smart climate|smart (ac|a/c|heat|clim)")

internal val RxNegation = Regex("stop|turn off|cancel")

internal val RxClimateOff = Regex("(stop|turn off|cancel|kill|end) (the )?(climate|ac|a/c|heat(er)?|aircon|air con|cooling|warming)")

// Start-climate phrasings.
internal val RxClimateStart = Regex(
    "(start|turn on|run|fire up|kick on) (the )?(climate|ac|a/c|heat(er)?|aircon|air con)" +
        "|pre.?(heat|cool|condition)|warm (it|the car|my car) up|cool (it|the car|my car) down" +
        "|(warm|cool) up (the|my) car",
)

internal val RxChargeLimit = Regex("(charge|charging) (limit|target)|limit .*(charge|charging)|charge to \\d{2,3}")

internal val RxPercent = Regex("\\b(\\d{2,3})\\s*%?")

internal val RxFlashLights = Regex("(flash|blink) (the )?(lights|headlights)|lights? (on|flash)")

internal val RxHorn = Regex("\\bhonk\\b|sound (the )?horn|\\bhorn\\b|beep (the )?(car|horn)|find (my|the) car")

internal val RxChargeStop = Regex("(stop|turn off|cancel|halt|end) (the )?charg|unplug")

internal val RxChargeStart = Regex("(start|begin|turn on|resume) (the )?charg|charge (it|the car|my car)( now)?|top (it )?up")

// Bare verb; always paired with `temp != null` at its call site so it doesn't fire on unrelated
// sentences.
internal val RxHeatCoolVerb = Regex("\\b(heat|cool|warm)\\b")

internal val RxSearchTokens = Regex("[^a-z0-9%]+")

/**
 * Enhances command parsing with Gemini Nano when available and the regex parse found nothing; falls
 * back gracefully if it is unavailable or fails.
 */
internal suspend fun enhanceCommandWithAi(
    query: String,
    initialCommand: ParsedVehicleCommand?,
    ai: com.bloo.bluelink.data.Ai,
): ParsedVehicleCommand? {
    // Keep a confident regex match.
    if (initialCommand != null) return initialCommand

    // Only try AI when the regex patterns didn't match.
    if (query.isBlank()) return null

    return try {
        // Ask the model to identify the command intent.
        val availableCommands = listOf(
            "lock - lock the car doors",
            "unlock - unlock the car doors",
            "charge_on - start charging the battery",
            "charge_off - stop charging the battery",
            "climate_on - start the climate control/AC",
            "climate_off - stop the climate control/AC",
            "lights - flash the lights",
            "horn - sound the horn",
        )
        val commandsList = availableCommands.joinToString(", ")

        val prompt = """User query: "$query"

Available vehicle commands: $commandsList

What is the user most likely trying to do? Answer with ONLY the command name (e.g., "lock", "unlock", "charge_on") or "none" if no clear command."""

        // Padded to meet the model's minimum character requirement.
        val paddedPrompt = if (prompt.length < 400) {
            prompt + "\n\n" + prompt.repeat((400 / prompt.length) + 1)
        } else {
            prompt
        }

        val result = ai.summarize(paddedPrompt).trim().lowercase()

        // Map the AI's answer to a command.
        return when {
            result.contains("lock") && !result.contains("unlock") -> ParsedVehicleCommand("lock", label = "Locking")
            result.contains("unlock") -> ParsedVehicleCommand("unlock", label = "Unlocking")
            result.contains("charge_on") || (result.contains("charge") && !result.contains("off")) -> ParsedVehicleCommand("charge_on", label = "Starting charge for")
            result.contains("charge_off") || (result.contains("charge") && result.contains("off")) -> ParsedVehicleCommand("charge_off", label = "Stopping charge for")
            result.contains("climate_on") || (result.contains("climate") && !result.contains("off")) -> ParsedVehicleCommand("climate_on", "default", "Starting climate for")
            result.contains("climate_off") -> ParsedVehicleCommand("climate_off", label = "Stopping climate for")
            result.contains("light") -> ParsedVehicleCommand("lights", label = "Flashing lights on")
            result.contains("horn") -> ParsedVehicleCommand("horn", label = "Sounding horn on")
            else -> null
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        // Fall back to the original parse result.
        null
    }
}
