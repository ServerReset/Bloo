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
    // Only meaningful for a climate START, and only when the phrasing is not
    // already asking for smart climate (which computes its own target from the
    // weather -- naming a temperature and asking for smart at once is a
    // contradiction, and smart is the more specific request).
    val temp = parseClimateTemperature(q, metric)
    // degLabel owns the F<->C-and-round rule (this was an inline third copy of it). `temp` is an
    // Int °F from parseClimateTemperature, and fahrenheit = !metric, so the two branches map
    // exactly onto degValue's two branches -- verified against FormatUtils.degValue.
    val tempLabel = temp?.let { degLabel(it.toString(), fahrenheit = !metric) }
    // Defrost implies climate at full heat -- "clear the windscreen" is a
    // request about ice, not about a number, so it picks its own temperature
    // unless the query also named one.
    val wantsDefrost = RxDefrost.containsMatchIn(q)
    return when {
        // Checked first: "open Bluelink"/"open the app"/"open Kia Access" is a
        // request to launch the OEM companion app (OwnerLinks' own "<appName>
        // app" button, see InfoPebble.kt), not a car command -- it has no
        // VehicleCommandRunner id at all, so it's dispatched separately in
        // SearchResults.kt via openApp() using the target vehicle's own
        // BrandLinks. Every brand's app name/aliases are matched generically
        // rather than hard-coded per-brand, so a new brand only needs its
        // BrandLinks entry, not a new regex here.
        RxOpenApp.containsMatchIn(q) ->
            ParsedVehicleCommand("open_app", label = "Opening the app for")
        // Unlock before lock: "unlock" contains "lock".
        RxUnlock.containsMatchIn(q) ->
            ParsedVehicleCommand("unlock", label = "Unlocking")
        RxLock.containsMatchIn(q) ->
            ParsedVehicleCommand("lock", label = "Locking")
        RxSmartClimate.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", "smart", "Starting smart climate for")
        // Defrost on its own is a start-climate request, so it is matched
        // before the generic stop/start climate patterns below.
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
        // Bare "heat <car> to 80" / "cool <car> to 65" / "warm <car> to 70" --
        // no start/turn-on prefix, no "up" -- the pattern above requires one
        // of those, so a query that's just the verb plus a target temperature
        // fell through to "not a command" entirely. Requiring temp != null is
        // what keeps this safe: it's the same guard that stops "Ioniq 5" from
        // being read as a temperature (see parseClimateTemperature's own doc),
        // so a bare "heat" with no number attached still isn't a command here
        // either -- it needs a real "to/at N" or "N degrees" alongside it.
        temp != null && RxHeatCoolVerb.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", VehicleCommandRunner.TEMP_PREFIX + temp, "Starting climate at $tempLabel for")
        // Charge LIMIT before charge start/stop: "set the charge limit to 80"
        // contains "charg", and the limit is the more specific request.
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
 * Every CONSTANT search/command pattern, compiled once at class init instead of per call.
 *
 * `Regex(...)` parses its pattern and builds a matcher every time it is CONSTRUCTED, and all
 * of these were constructed inside the functions using them. Two distinct costs:
 *
 *  - The token splitter ran inside SettingsSearchResults, a composable whose `query`
 *    parameter changes on every KEYSTROKE -- a regex compiled per character typed, on the
 *    input path, with the keyboard up. That is the one a user can feel.
 *  - The command-parser vocabulary was ~17 compilations per parse, on every submitted query.
 *
 * File scope rather than `remember`: the patterns are constant, so that is their correct
 * lifetime, and a `remember` would still recompile once per composition that mis-keyed it.
 * Any pattern built from a runtime value (a vehicle's own name) is left where it is, since
 * it genuinely cannot be constant.
 *
 * Generated by extracting the literals from this file rather than by retyping them: doing it
 * by hand through two layers of escaping mangled the degree sign and several `\b` anchors.
 */
internal val RxColdest = Regex("coldest|as cold as|max(imum)? (cold|cool)|lowest temp|full (cold|cool)")

internal val RxWarmest = Regex("warmest|hottest|as (warm|hot) as|max(imum)? (heat|warm)|highest temp|full heat")

internal val RxTempAtTo = Regex("\\b(?:at|to)\\s*(\\d{2,3})\\s*°?\\s*([fc])?\\b")

internal val RxTempDegrees = Regex("\\b(\\d{2,3})\\s*°?\\s*(?:degrees?\\b|([fc])\\b)")

internal val RxDefrost = Regex("defrost|defog|demist|clear (the )?(wind(screen|shield)|glass|ice)|de-ice")

// "open" + a known companion-app name/alias, or the generic "the app"/"my app" --
// deliberately NOT bare "open" (that would swallow "open the car"/"open the
// doors", RxUnlock's own territory below) and NOT bare "app" (too broad).
// Brand names are lowercase, matching how they're compared against `q` (also
// lowercased) -- kept in sync with BrandLinks.appName (Brand.kt) by hand since
// that list is a fixed, rarely-changing set of OEM brands, not per-vehicle data.
internal val RxOpenApp = Regex(
    "\\bopen\\b.*(bluelink|kia access|kia connect|uvo|genesis( app| connected)?|" +
        "\\bthe app\\b|\\bmy app\\b|\\bowner('?s)? app\\b|\\bcar app\\b|\\bcompanion app\\b)",
)

internal val RxUnlock = Regex("\\bunlock\\b|\\bopen (the |my )?(car|doors?)\\b|let me in")

internal val RxLock = Regex("\\block\\b|secure (the |my )?car|lock (it|up)\\b")

internal val RxSmartClimate = Regex("smart climate|smart (ac|a/c|heat|clim)")

internal val RxNegation = Regex("stop|turn off|cancel")

internal val RxClimateOff = Regex("(stop|turn off|cancel|kill|end) (the )?(climate|ac|a/c|heat(er)?|aircon|air con|cooling|warming)")

// Was constructed fresh inline at its one call site, unlike every other
// pattern in this block -- missed when the rest were hoisted (see the
// doc above this block for why that hoist mattered: once per submitted
// query, not once per frame, but still worth not re-parsing).
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

// Bare verb, no "start"/"turn on"/"up" needed -- paired with `temp != null` at
// its one call site, which is what stops it from firing on every unrelated
// sentence that happens to contain "heat" or "cool".
internal val RxHeatCoolVerb = Regex("\\b(heat|cool|warm)\\b")

internal val RxSearchTokens = Regex("[^a-z0-9%]+")


/**
 * Try to enhance command parsing using Gemini Nano when available. If the query
 * is ambiguous or the initial parse didn't match, use AI to understand intent.
 * Gracefully falls back if Gemini Nano is unavailable or fails.
 */
internal suspend fun enhanceCommandWithAi(
    query: String,
    initialCommand: ParsedVehicleCommand?,
    ai: com.bloo.bluelink.data.Ai,
): ParsedVehicleCommand? {
    // If we already have a confident match, return it
    if (initialCommand != null) return initialCommand

    // Only try AI enhancement if the query didn't match regex patterns
    if (query.isBlank()) return null

    return try {
        // Build a prompt asking the model to identify vehicle command intent
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

        // Pad to meet minimum character requirement
        val paddedPrompt = if (prompt.length < 400) {
            prompt + "\n\n" + prompt.repeat((400 / prompt.length) + 1)
        } else {
            prompt
        }

        val result = ai.summarize(paddedPrompt).trim().lowercase()

        // Parse the AI's response
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
        // Graceful fallback - return original parse result
        null
    }
}
