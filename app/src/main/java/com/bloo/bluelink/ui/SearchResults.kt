/**
 * Search results surface: the ranked settings and car-data result list, the command suggestions, and the
 * answer cards beneath them.
 */
package com.bloo.bluelink.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.rethrowIfCancellation
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.content

/**
 * Live search, answered as floating pills: first what the app KNOWS (the live battery, range, lock or odometer of every car that
 * matches, so "battery" shows the batteries and not only an AI sentence about them), then the actions the words ask for, then the
 * settings that match, each its own pill. The AI cards (and a recognised command's result) sit pinned under the list, nearest the bar.
 * Tokenises the query (dropping filler words), so "odometer for xyz" finds the odometer of the car named xyz.
 */
@Composable
internal fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    /** Show at most this many settings, best first (a keyboard leaves no room for a long list). */
    limit: Int = Int.MAX_VALUE,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    /** Fill the search box with this text, and run it when the flag is true. */
    onPick: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // remember(query): this composable recomposes on every UiState emission, and an equal-but-new
    // token list would defeat the memos below.
    val tokens = remember(query) { searchTokens(query) }
    // Same source as the main Settings screen's Security card gate.
    val canBio = remember { vm.canUseBiometrics() }
    // Live read of the whole store, so a value changed here or by a sync from another device shows up.
    val prefs by remember(vm) { vm.settingsStore.allPrefs }.collectAsState(initial = emptyMap())
    val entries = remember(state, appearance, notif, vm, canBio, state.settingsMode, prefs) {
        com.bloo.bluelink.perf.PerfMonitor.span("search.entries", thresholdMs = 4) {
            buildSettingsSearchEntries(state, appearance, notif, vm, canBio, prefs)
        }
    }
    val facts = remember(state, appearance) { carFacts(state, appearance) }
    val pieces = remember(facts, tokens) { rankPieces(tokens, facts) }
    val settings = remember(entries, tokens, limit) {
        val strict = entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = false)?.let { e to it } }
        val scored = strict.ifEmpty { entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = true)?.let { e to it } } }
        scored.sortedByDescending { it.second }.map { it.first }.let { if (it.size > limit) it.take(limit) else it }
    }
    val suggestedCommands = remember(query, state.vehicles) {
        if (query.isNotBlank()) searchCommands(query, state.vehicles, fuzzy = false).take(3) else emptyList()
    }

    // A recognised command ("lock my Ioniq") runs via VehicleCommandRunner, the same path as the
    // rest of the app. No named car falls back to a single car, or asks which when several exist.
    val metricUnits = !appearance.useFahrenheit
    var command by remember(submittedQuery, metricUnits) {
        mutableStateOf(if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits))
    }
    // When Gemini Nano is enabled, use it to enhance command parsing for ambiguous/unmatched queries.
    if (state.aiActive && command == null && submittedQuery.isNotBlank()) {
        LaunchedEffect(submittedQuery) {
            try {
                vm.enhanceCommandParsing(submittedQuery, null)?.let { command = it }
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                // Graceful fallback if AI enhancement fails
            }
        }
    }
    val resolvedCommand = command
    val listState = rememberLazyListState()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SearchPillList(Modifier.weight(1f, fill = false), listState) {
            if (pieces.isEmpty() && settings.isEmpty() && suggestedCommands.isEmpty()) {
                pillItem("none", listState) {
                    // Not a dead end: point at the kinds of thing the bar understands.
                    MessagePill("No matches for \"$query\"", "Try a car name, a setting, or a command like \"lock my car\".", hazeState)
                }
            }
            if (pieces.isNotEmpty()) {
                pillItem("head:cars", listState) { SearchHeading(if (pieces.size > 1) "From your cars · ${pieces.size}" else "From your cars", hazeState) }
                // Pieces of the car's own page, lifted out: the charge bar, the lock buttons, the climate switch, the map.
                pieces.forEach { p -> pillItem("piece:${p.vin}:${p.topic}", listState) { SearchPieceView(p, state, vm, appearance, hazeState) } }
            }
            if (suggestedCommands.isNotEmpty()) {
                pillItem("head:actions", listState) { SearchHeading("Actions", hazeState) }
                suggestedCommands.forEach { cmd ->
                    pillItem("cmd:${cmd.id}", listState) {
                        // Runs it, unless it needs a value first ("set charge limit"): then it just fills the box.
                        ActionPill(cmd.icon, cmd.title, cmd.description, MaterialTheme.colorScheme.secondary, hazeState) {
                            onPick(if (cmd.requiresArg) cmd.title + " " else cmd.title, !cmd.requiresArg)
                        }
                    }
                }
            }
            if (settings.isNotEmpty()) {
                pillItem("head:settings", listState) { SearchHeading(if (settings.size > 1) "Settings · ${settings.size}" else "Settings", hazeState) }
                settings.forEach { e ->
                    pillItem("set:${e.title}", listState) {
                        ControlPill(e.title.substringAfter(" · ", "").takeIf { it.isNotBlank() }, hazeState, e.content)
                    }
                }
            }
        }
        // Pinned under the list (not items in it): these start work when they appear, which must not repeat as the list scrolls.
        if (resolvedCommand != null) CommandActionResult(resolvedCommand, submittedQuery, state, vm, hazeState)
        // Free-form command via the AI when the deterministic parser did not recognise the phrasing...
        if (command == null && state.aiActive && submittedQuery.isNotBlank()) AiCommandProposal(submittedQuery, state, vm, hazeState)
        // ...and the same for a plain-language SETTING request ("stop it vibrating"): it only changes when Apply is tapped.
        if (command == null && state.aiActive && submittedQuery.isNotBlank()) AiSettingProposal(submittedQuery, state, appearance, notif, vm, hazeState)
        // On-device AI reply (when enabled): a plain-language answer to a deliberate submit, an addition to the answers above.
        if (state.aiActive) AiAnswer(state, vm, submittedQuery, hazeState)
    }
}
