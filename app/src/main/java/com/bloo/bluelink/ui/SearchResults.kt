package com.bloo.bluelink.ui

import com.bloo.bluelink.rethrowIfCancellation
/**
 * Search results surface: the ranked settings/car-data result list, its stagger timing constant and
 * the per-result pop-in helper.
 */

import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleCommandRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.setAutoLockConfig
import com.bloo.bluelink.data.settingsMode

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
        val ctx = LocalContext.current
        // Whole-word, longest-match car resolution, not a bare substring test ("Ioniq" must not
        // match inside "Ioniq 5"). Ties at the longest length are ambiguous: refuse and ask which
        // car.
        val targetVehicle = remember(submittedQuery, state.vehicles) {
            val q = submittedQuery.lowercase()
            val nameMatches = state.vehicles.filter { v ->
                v.name.isNotBlank() &&
                    Regex("\\b" + Regex.escape(v.name.lowercase()) + "\\b").containsMatchIn(q)
            }
            val longestMatchLen = nameMatches.maxOfOrNull { it.name.length }
            val namedVehicle = nameMatches.filter { it.name.length == longestMatchLen }.singleOrNull()
            // Fall back to "the one car" only when NO name matched; an ambiguous match must not
            // pick one.
            namedVehicle ?: if (nameMatches.isEmpty()) state.vehicles.singleOrNull() else null
        }
        var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
        var commandExecuted by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            if (targetVehicle != null && !commandExecuted) {
                if (resolvedCommand.cmd == "open_app") {
                    // Not a car command: launches the OEM companion app (as OwnerLinks does).
                    val links = targetVehicle.brand.links
                    openApp(ctx, listOf(links.appPackage), links.playStoreUrl)
                    actionResult = "Opening ${links.appName}"
                    commandExecuted = true
                } else {
                    actionRunning = true
                    val result = runCatching { VehicleCommandRunner.run(ctx, targetVehicle.vin, resolvedCommand.cmd, resolvedCommand.climateTarget) }.getOrNull()
                    actionResult = result?.message ?: "Command failed"
                    actionRunning = false
                    vm.refreshStatus(targetVehicle)
                    commandExecuted = true
                }

                // Track this command as recently used
                try {
                    RecentCommandsTracker(ctx).recordUsage(resolvedCommand.cmd)
                } catch (e: Exception) {
                    // Silently fail - tracking is not critical
                }
            }
        }
        GlassSurface(
            shape = resultCardShape,
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
        ) {
            Column(Modifier.padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(GapRow))
                    TitleSmallText("Action")
                }
                Text(
                    when {
                        targetVehicle == null -> {
                            val example = state.vehicles.firstOrNull()?.name ?: "car"
                            "Which car? Mention its name, e.g. \"${resolvedCommand.label} my $example\"."
                        }
                        actionRunning -> "${resolvedCommand.label} ${targetVehicle.name}…"
                        actionResult != null -> actionResult ?: "${resolvedCommand.label} ${targetVehicle.name}"
                        else -> "${resolvedCommand.label} ${targetVehicle.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Free-form command via the AI when the deterministic parser did not recognise the phrasing.
    if (command == null && state.aiEnabled && submittedQuery.isNotBlank()) {
        val ctx = LocalContext.current
        var proposal by remember(submittedQuery) { mutableStateOf<Pair<String, String>?>(null) }
        var thinking by remember(submittedQuery) { mutableStateOf(true) }
        var ran by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var running by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            proposal = vm.aiResolveCommand(submittedQuery)
            thinking = false
        }
        val p = proposal
        if (p != null) {
            val car = state.vehicles.firstOrNull { it.vin == p.second }
            GlassSurface(
                shape = resultCardShape,
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
            ) {
                Column(Modifier.padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(GapRow))
                        TitleSmallText("Did you mean?")
                    }
                    Text(
                        ran ?: "${aiCommandLabel(p.first)} ${car?.name ?: "your car"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ran == null && car != null) {
                        val scope = rememberCoroutineScope()
                        // MorphTextButton, not a bare Button: this was the one plain Material
                        // button left in the app, so it was the only standard button that neither
                        // morphed on press nor fired the click haptic every other button gives.
                        SafeMorphTextButton(
                            text = if (running) "Working…" else "Run it",
                            onClick = {
                                running = true
                                scope.launch {
                                    val r = runCatching {
                                        VehicleCommandRunner.run(ctx, car.vin, p.first, "default")
                                    }.getOrNull()
                                    ran = r?.message ?: "Command failed"
                                    running = false
                                    vm.refreshStatus(car)
                                }
                            },
                            enabled = !running,
                            emphasis = ButtonEmphasis.Confirm,
                        )
                    }
                }
            }
        }
    }

    // On-device AI reply (when enabled): a plain-language answer, a complement to structured
    // matches. Gated on submittedQuery, not the live query: it fires a real AI request and must
    // wait for a deliberate submit.
    if (state.aiEnabled) {
        LaunchedEffect(submittedQuery) {
            if (submittedQuery.isNotBlank()) {
                vm.askAi(submittedQuery)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        AnimatedVisibility(
            visible = thinking || reply != null,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            GlassSurface(
                shape = resultCardShape,
                modifier = Modifier.fillMaxWidth(),
                hazeState = hazeState,
            ) {
                Column(Modifier.padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(GapRow))
                        TitleSmallText("AI answer")
                    }
                    if (reply != null) {
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One AutoLock toggle, as a search result. */
@Composable
internal fun AutoLockSearchToggle(
    v: Vehicle,
    vm: AppViewModel,
    label: String,
    checked: (AutoLockConfig) -> Boolean,
    update: (AutoLockConfig, Boolean) -> AutoLockConfig,
) {
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return
    ToggleRow(label, checked(current)) { value ->
        val updated = update(current, value)
        config = updated
        vm.setAutoLockConfig(v.vin, updated)
    }
}

/**
 * AutoLock's grace period as a search result: the same slider the AutoLock card shows, loaded and
 * saved the same way.
 */
@Composable
internal fun AutoLockGraceSearchRow(v: Vehicle, vm: AppViewModel) {
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return
    StepRow("Grace period", "${current.graceSeconds}s")
    AnimatedSlider(
        value = current.graceSeconds.toFloat(),
        onValueChange = { config = current.copy(graceSeconds = it.roundToInt()) },
        onValueSettled = {
            val updated = current.copy(graceSeconds = it.roundToInt())
            config = updated
            vm.setAutoLockConfig(v.vin, updated)
        },
        valueRange = 10f..120f,
        steps = 10,
    )
}
