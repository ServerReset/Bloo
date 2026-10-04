package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled

// --- On-device AI (Gemini Nano): summaries, free-form questions, command resolution (extracted from AppViewModel) --

/** The only actions [AppViewModel.aiResolveCommand] may return -- exactly the
 *  ids VehicleCommandRunner has a case for. Deliberately NOT derived from a
 *  broader list: if the runner cannot execute it, the model must not be able to
 *  name it. */
private val AI_COMMANDS = setOf(
    "lock", "unlock", "charge_on", "charge_off", "climate_on", "climate_off",
    "lights", "horn",
)
// charge_limit is deliberately NOT here. Every action above is a verb with no
// argument, so validating it means checking one word against this set. A charge
// limit carries a NUMBER, and a model that picks the wrong number sets the wrong
// limit with no way for this layer to tell -- 80 and 90 are equally plausible
// strings. The deterministic parser reads that number out of the query itself,
// where it is either present and correct or absent, so that is where the limit
// stays.


fun AppViewModel.setAiEnabled(value: Boolean) {
    _state.update { it.copy(aiEnabled = value) }
    viewModelScope.launch { settingsStore.setAiEnabled(value) }
}

/**
 * Auto-summarize a car (called after open/refresh/command) whenever on-device AI is
 * enabled -- summaries always refresh on their own now, no separate opt-in.
 * Silent: no "refresh first" nudge and no error toast, since the user didn't
 * explicitly ask — they can always tap Summarize for the surfaced version.
 */
internal fun AppViewModel.autoSummarize(v: Vehicle) {
    val s = _state.value
    if (!s.aiSupported || !s.aiEnabled) return
    if (v.vin in s.aiBusy) return
    val status = s.statusFor(v) ?: return
    _state.update { it.copy(aiBusy = it.aiBusy + v.vin) }
    viewModelScope.launch {
        val result = runCatching { ai.summarize(summaryPrompt(v, status, _state.value)) }
        _state.update { st ->
            result.fold(
                onSuccess = { sum ->
                    st.copy(aiBusy = st.aiBusy - v.vin, aiSummaries = st.aiSummaries + (v.vin to sum))
                },
                onFailure = { e ->
                    AppLog.log("⚠ Auto AI summary: ${e.message}")
                    st.copy(aiBusy = st.aiBusy - v.vin)
                },
            )
        }
    }
}

/**
 * Manual "Summarize" tap. Requires a status already in [UiState.statuses]
 * (if there isn't one, it asks the user to refresh first rather than
 * triggering a fetch itself); marks the VIN busy so the button can show a
 * spinner and a second tap is ignored via the guard above, runs the model
 * off the main thread inside [viewModelScope], and always clears the busy
 * flag on either branch of [Result.fold] -- success writes the summary
 * into [UiState.aiSummaries], failure logs and surfaces a snackbar.
 */
fun AppViewModel.summarizeCar(v: Vehicle) {
    if (v.vin in _state.value.aiBusy) return
    val status = _state.value.statusFor(v) ?: run {
        _state.update { it.copy(message = "Refresh ${v.name} first, then summarize.", messageType = "info") }
        return
    }
    _state.update { it.copy(aiBusy = it.aiBusy + v.vin) }
    viewModelScope.launch {
        // Build the prompt for THIS car only, so the result reflects just it.
        val prompt = summaryPrompt(v, status, _state.value)
        val result = runCatching { ai.summarize(prompt) }
        _state.update { st ->
            result.fold(
                onSuccess = { s ->
                    st.copy(aiBusy = st.aiBusy - v.vin, aiSummaries = st.aiSummaries + (v.vin to s))
                },
                onFailure = { e ->
                    // The exception text is AICore/Gemini-Nano implementation
                    // detail ("Feature not available: ...", binder/ExecutionException
                    // strings). Log it for diagnostics; show the user a sentence.
                    AppLog.log("⚠ AI summary: ${e.message}")
                    st.copy(aiBusy = st.aiBusy - v.vin, message = "Couldn't summarize ${v.name} right now.")
                },
            )
        }
    }
}

/** Ask Gemini Nano a free-form question answered from the cars' live data. */
fun AppViewModel.askAi(query: String) {
    if (!_state.value.aiEnabled || query.isBlank()) return
    _state.update { it.copy(aiBusy = it.aiBusy + "search") }
    viewModelScope.launch {
        val data = _state.value.vehicles.joinToString("\n\n") { v ->
            carText(v, _state.value.statusFor(v), _state.value)
        }
        val reply = runCatching {
            ai.summarize("Answer this question using only the data below.\nQuestion: $query\n\nData:\n$data")
        }.onFailure { AppLog.log("⚠ AI search: ${it.message}") }.getOrNull()
        // On failure both `thinking` and `reply` go false/null, and the answer
        // card renders on `thinking || reply != null` — so without a message the
        // whole card just silently vanished after "Thinking…", with no log line
        // either. Say something, like the per-car summary path already does.
        _state.update {
            it.copy(
                aiBusy = it.aiBusy - "search",
                aiSearchReply = reply,
                message = if (reply == null) "Couldn't answer that. Try again." else it.message,
            )
        }
    }
}

/** Enhance command parsing using Gemini Nano for ambiguous queries. */
internal suspend fun AppViewModel.enhanceCommandParsing(
    query: String,
    initialCommand: ParsedVehicleCommand?,
): ParsedVehicleCommand? =
    enhanceCommandWithAi(query, initialCommand, ai)

/**
 * Maps a free-form command to a structured one the app can actually run,
 * or null if it cannot be mapped SAFELY.
 *
 * The deterministic parser in the search UI handles the phrasings it knows;
 * this is the fallback for everything else ("make it toasty in the Ioniq
 * before I head out"). Gemini Nano is a small on-device model with no
 * function calling, so it is asked for one line in a fixed shape and every
 * part of that line is then checked against reality:
 *
 *  - the action must be one of the runner's own command ids. Anything else,
 *    including a plausible-sounding invention like "open_trunk", is
 *    discarded rather than attempted.
 *  - the car must match one of THIS user's cars by name. The model never
 *    supplies a VIN and is never trusted to; it names a car, and the name
 *    is resolved here.
 *
 * So a hallucination cannot reach the vehicle: the worst case is this
 * returns null and the user is told it did not understand. That property is
 * the reason this returns a validated pair rather than a command string.
 */
suspend fun AppViewModel.aiResolveCommand(query: String): Pair<String, String>? {
    if (!_state.value.aiEnabled || query.isBlank()) return null
    val cars = _state.value.vehicles
    if (cars.isEmpty()) return null
    val names = cars.joinToString(", ") { it.name }
    val prompt = buildString {
        append("Map the request to one car action. Reply with ONE line, no explanation.\n")
        append("Format: ACTION|CAR\n")
        append("ACTION must be exactly one of: ")
        append(AI_COMMANDS.joinToString(", "))
        append(", none\n")
        append("CAR must be exactly one of: ").append(names).append("\n")
        append("Use none if the request is not one of those actions.\n")
        append("Request: ").append(query)
    }
    val raw = runCatching { ai.summarize(prompt) }
        .onFailure { AppLog.log("⚠ AI command: ${it.message}") }
        .getOrNull() ?: return null
    // The model will sometimes wrap the line in prose despite being asked
    // not to; take the first line that actually has the separator in it.
    val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.contains('|') } ?: return null
    val action = line.substringBefore('|').trim().lowercase().removePrefix("action:").trim()
    val carName = line.substringAfter('|').trim().removePrefix("car:").trim()
    if (action !in AI_COMMANDS) return null
    val car = cars.firstOrNull { it.name.equals(carName, ignoreCase = true) }
        ?: cars.singleOrNull()
        ?: return null
    return action to car.vin
}

/** Dismiss the AI search-answer card. */
fun AppViewModel.clearAiReply() = _state.update { it.copy(aiSearchReply = null) }
