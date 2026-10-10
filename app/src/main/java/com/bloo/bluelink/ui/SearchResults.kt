/**
 * Search results surface: the ranked settings/car-data result list, its stagger timing constant and
 * the per-result pop-in helper.
 */
package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.rethrowIfCancellation
import kotlinx.coroutines.delay

internal const val SEARCH_RESULT_STAGGER_MS = 35L

@Composable
internal fun staggeredResultVisible(resetKey: Any, index: Int): Boolean {
    var visible by remember(resetKey) { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        delay(index * SEARCH_RESULT_STAGGER_MS)
        visible = true
    }
    return visible
}

/**
 * Live search over app settings and per-car data/fields. Tokenises the query (dropping filler
 * words), so "odometer for xyz" finds the odometer of the car named xyz.
 */
@Composable
internal fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    /** Show at most this many, best first (a keyboard leaves no room for a long list). */
    limit: Int = Int.MAX_VALUE,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    // remember(query): this composable recomposes on every UiState emission, and an equal-but-new
    // token list would defeat the `results` memo below.
    val tokens = remember(query) { searchTokens(query) }
    // Same source as the main Settings screen's Security card gate.
    val canBio = remember { vm.canUseBiometrics() }

    // remember(state, appearance, notif, vm, canBio), not rebuilt inline: none of the ~50+ add(...)
    // calls below read `query` at all -- only the scoring pass further down does -- so building
    // this whole list (and every entry's own composable lambda) fresh on every keystroke was pure
    // waste.
    val entries = remember(state, appearance, notif, vm, canBio, state.settingsMode) {
        buildSettingsSearchEntries(state, appearance, notif, vm, canBio)
    }

    // Matches render FIRST (top of this composable's output), the AI answer LAST -- this composable
    // is placed above the floating search bar, so the resulting stack top-to-bottom is [suggested
    // results] [AI tile] [search bar], matching the requested reading order bottom-up. Ranked, not
    // filtered.
    val results = remember(entries, tokens, limit) {
        if (tokens.isEmpty()) {
            entries
        } else {
            val strict = entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = false)?.let { e to it } }
            val scored = strict.ifEmpty {
                entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = true)?.let { e to it } }
            }
            scored.sortedByDescending { it.second }.map { it.first }
        }.let { if (it.size > limit) it.take(limit) else it }
    }
    // Floating search results use GlassSurface, the shared glass chrome of floating surfaces.
    val resultCardShape = StandardShape

    // Command suggestions from CommandIndex when available
    val suggestedCommands = remember(query) {
        if (query.isNotBlank()) {
            searchCommands(query, state.vehicles, fuzzy = false)
                .take(if (results.isNotEmpty()) 1 else 3) // Show fewer if settings results exist
        } else {
            emptyList()
        }
    }

    if (results.isEmpty() && suggestedCommands.isEmpty()) {
        GlassSurface(
            shape = resultCardShape,
            modifier = Modifier.fillMaxWidth(),
            shadow = false,
        ) {
            Column(Modifier.padding(GapSection)) {
                Text(
                    "No matches for \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(GapRow))
                // Not a dead end: point at the three kinds of thing the bar understands.
                Text(
                    "Try a car name, a setting, or a command like \"lock my car\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // Restarts the stagger whenever the actual SET of results changes -- not on every
        // keystroke, which would re-pop a list that hasn't actually moved just because the user is
        // still typing the same word. Titles joined is cheap and exactly captures "did the ranked
        // list change," which is the only thing that should trigger this.
        val resultsKey = results.joinToString("|") { it.title }
        results.forEachIndexed { i, e ->
            PopVisible(visible = staggeredResultVisible(resultsKey, i)) {
                GlassSurface(
                    shape = resultCardShape,
                    modifier = Modifier.fillMaxWidth(),
                    shadow = false,
                ) {
                    Row(Modifier.padding(GapSection)) {
                        // A small leading icon badge per result, as in the update pebble and
                        // settings hero stats.
                        IconBadge(
                            AppIcons.Search,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 28.dp,
                            iconSize = 15.dp,
                        )
                        Spacer(Modifier.width(GapGroup))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                            TitleSmallText(e.title)
                            e.content()
                        }
                    }
                }
            }
        }
    }

    // Command suggestions from the command index, displayed inline
    suggestedCommands.forEachIndexed { i, cmd ->
        PopVisible(visible = staggeredResultVisible(suggestedCommands.joinToString("|") { it.id }, i + results.size)) {
            GlassSurface(
                shape = resultCardShape,
                modifier = Modifier.fillMaxWidth(),
                shadow = false,
            ) {
                Row(Modifier.padding(GapSection)) {
                    IconBadge(
                        cmd.icon,
                        tint = MaterialTheme.colorScheme.secondary,
                        size = 28.dp,
                        iconSize = 15.dp,
                    )
                    Spacer(Modifier.width(GapGroup))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                        TitleSmallText(cmd.title)
                        Text(cmd.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // A recognised command ("lock my Ioniq") runs via VehicleCommandRunner, the same path as the
    // rest of the app. No named car falls back to a single car, or asks which when several exist.
    val metricUnits = !appearance.useFahrenheit
    var command by remember(submittedQuery, metricUnits) {
        mutableStateOf(if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits))
    }
    // When Gemini Nano is enabled, use it to enhance command parsing for ambiguous/unmatched
    // queries
    if (state.aiEnabled && command == null && submittedQuery.isNotBlank()) {
        LaunchedEffect(submittedQuery) {
            try {
                val enhanced = vm.enhanceCommandParsing(submittedQuery, null)
                if (enhanced != null) {
                    command = enhanced
                }
            } catch (e: Exception) {
                rethrowIfCancellation(e)
                // Graceful fallback if AI enhancement fails
            }
        }
    }
    val resolvedCommand = command
    if (resolvedCommand != null) {
        CommandActionResult(resolvedCommand, submittedQuery, state, vm, hazeState)
    }

    // Free-form command via the AI when the deterministic parser did not recognise the phrasing.
    if (command == null && state.aiEnabled && submittedQuery.isNotBlank()) {
        AiCommandProposal(submittedQuery, state, vm, hazeState)
    }

    // On-device AI reply (when enabled): a plain-language answer, a complement to structured
    // matches. Gated on submittedQuery, not the live query: it fires a real AI request and must
    // wait for a deliberate submit.
    if (state.aiEnabled) {
        AiAnswer(state, vm, submittedQuery, hazeState)
    }
}
