/**
 * Search results surface: the ranked settings and car-data result list, the command suggestions, and the
 * answer cards beneath them.
 */
package com.bloo.bluelink.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.rethrowIfCancellation
import com.bloo.uicommon.RevealText

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
    /** Fill the search box with this text, and run it when the flag is true. */
    onPick: (String, Boolean) -> Unit = { _, _ -> },
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
    // Live read of the whole store, so a value changed here or by a sync from another device shows up.
    val prefs by remember(vm) { vm.settingsStore.allPrefs }.collectAsState(initial = emptyMap())
    val entries = remember(state, appearance, notif, vm, canBio, state.settingsMode, prefs) {
        buildSettingsSearchEntries(state, appearance, notif, vm, canBio, prefs)
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

    // Command suggestions from CommandIndex when available
    val suggestedCommands = remember(query, state.vehicles, results.isEmpty()) {
        if (query.isNotBlank()) {
            searchCommands(query, state.vehicles, fuzzy = false)
                .take(if (results.isNotEmpty()) 1 else 3) // Show fewer if settings results exist
        } else {
            emptyList()
        }
    }

    if (results.isEmpty() && suggestedCommands.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = GapGroup), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            RevealText("No matches for \"$query\"", style = MaterialTheme.typography.titleSmall)
            // Not a dead end: point at the three kinds of thing the bar understands.
            RevealText(
                "Try a car name, a setting, or a command like \"lock my car\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        // Split the single best match out on top, then group the rest. One obvious answer should say
        // so at a glance instead of hiding at the head of a list, and a heading carrying its count
        // tells you how much is under it without scrolling. Still one flat list under small headings,
        // not a stack of bordered cards.
        val top = if (tokens.isNotEmpty() && results.size > 1) results.first() else null
        val rest = if (top != null) results.drop(1) else results

        if (suggestedCommands.isNotEmpty()) {
            ResultGroup("Actions") {
                suggestedCommands.forEach { cmd -> androidx.compose.runtime.key(cmd.id) {
                    ResultRow(
                        cmd.icon, MaterialTheme.colorScheme.secondary, caption = null,
                        // Runs it, unless it needs a value first ("set charge limit"): then it just fills the box.
                        onClick = { onPick(if (cmd.requiresArg) cmd.title + " " else cmd.title, !cmd.requiresArg) },
                    ) {
                        TitleSmallText(cmd.title)
                        RevealText(cmd.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
        }
        if (top != null) {
            androidx.compose.runtime.key(top.title) {
                ResultGroup("Top result") {
                    val car = top.title.substringAfter(" · ", "").takeIf { it.isNotBlank() }
                    ResultRow(
                        if (car != null) AppIcons.DirectionsCar else AppIcons.Settings,
                        MaterialTheme.colorScheme.primary,
                        caption = car,
                        content = top.content,
                    )
                }
            }
        }
        rest.groupBy { if (" · " in it.title) "Your cars" else "Settings" }
            .forEach { (heading, entriesInGroup) -> androidx.compose.runtime.key(heading) {
                ResultGroup(if (entriesInGroup.size > 1) "$heading · ${entriesInGroup.size}" else heading) {
                    entriesInGroup.forEach { e -> androidx.compose.runtime.key(e.title) {
                        val car = e.title.substringAfter(" · ", "").takeIf { it.isNotBlank() }
                        ResultRow(
                            if (car != null) AppIcons.DirectionsCar else AppIcons.Settings,
                            MaterialTheme.colorScheme.primary,
                            caption = car,
                            content = e.content,
                        )
                    } }
                }
            } }
    }

    // A recognised command ("lock my Ioniq") runs via VehicleCommandRunner, the same path as the
    // rest of the app. No named car falls back to a single car, or asks which when several exist.
    val metricUnits = !appearance.useFahrenheit
    var command by remember(submittedQuery, metricUnits) {
        mutableStateOf(if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits))
    }
    // When Gemini Nano is enabled, use it to enhance command parsing for ambiguous/unmatched
    // queries
    if (state.aiActive && command == null && submittedQuery.isNotBlank()) {
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
    if (command == null && state.aiActive && submittedQuery.isNotBlank()) {
        AiCommandProposal(submittedQuery, state, vm, hazeState)
    }

    // ...and the same for a plain-language SETTING request ("stop it vibrating"): Gemini Nano picks one
    // of the on/off switches, and it only changes when Apply is tapped.
    if (command == null && state.aiActive && submittedQuery.isNotBlank()) {
        AiSettingProposal(submittedQuery, state, appearance, notif, vm, hazeState)
    }

    // On-device AI reply (when enabled): a plain-language answer, a complement to structured
    // matches. Gated on submittedQuery, not the live query: it fires a real AI request and must
    // wait for a deliberate submit.
    if (state.aiActive) {
        AiAnswer(state, vm, submittedQuery, hazeState)
    }
}

/** A small heading over a run of result rows, with hairline dividers between the rows. */
@Composable
internal fun ResultGroup(heading: String, rows: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RevealText(
            heading,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = GapHairline),
        )
        rows()
    }
}

/** One result: its icon, an optional muted caption (the car), then the result's own control. */
@Composable
internal fun ResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    caption: String?,
    /** Makes the whole row a button (and adds the chevron): tapping it acts on the result. */
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Each row eases in the first time it appears, so a changing list grows and fades rather than popping.
    EnterOnce(expandFrom = androidx.compose.ui.Alignment.Top) {
    Column(Modifier.glassEdgeFocus()) {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clip(StandardShape).clickable { haptics?.click(); onClick() }
                } else {
                    Modifier
                },
            )
            .padding(vertical = GapGroup),
        verticalAlignment = if (onClick != null) androidx.compose.ui.Alignment.CenterVertically else androidx.compose.ui.Alignment.Top,
    ) {
        IconBadge(icon, tint = tint, size = 32.dp, iconSize = 17.dp)
        Spacer(Modifier.width(GapGroup))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
            if (caption != null) {
                RevealText(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
        if (onClick != null) {
            Icon(
                AppIcons.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    }
    }
}
