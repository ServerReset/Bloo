package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled

// --- On-device AI (Gemini Nano): summaries, free-form questions, command resolution (extracted from AppViewModel) --

/**
 * The only actions [AppViewModel.aiResolveCommand] may return -- exactly the ids
 * VehicleCommandRunner has a case for. Deliberately NOT derived from a broader list: if the runner
 * cannot execute it, the model must not be able to name it.
 */
private val AI_COMMANDS = setOf(
    "lock", "unlock", "charge_on", "charge_off", "climate_on", "climate_off",
    "lights", "horn",
)
// charge_limit is deliberately absent: it carries a number a model could get wrong undetectably, so
// the deterministic parser reads it from the query instead.

fun AppViewModel.setAiEnabled(value: Boolean) {
    _state.update { it.copy(aiEnabled = value) }
    viewModelScope.launch { settingsStore.setAiEnabled(value) }
}

/**
 * Auto-summarize a car (after open/refresh/command) when on-device AI is enabled. Silent: no nudge
 * and no error toast, since the user didn't ask.
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
 * Manual "Summarize" tap. Needs a status already in [UiState.statuses] (otherwise asks the user to
 * refresh); marks the VIN busy and always clears it on both branches of [Result.fold].
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
                    // The exception text is AICore implementation detail: log it, show the user a
                    // sentence.
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
        // On failure the card would silently vanish (it renders on `thinking || reply != null`), so
        // say something.
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
 * Maps a free-form command to a structured one the app can run, or null if it cannot be mapped
 * safely. A hallucination can only produce null, never reach the vehicle.
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
    // The model sometimes wraps the line in prose; take the first line containing the separator.
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
