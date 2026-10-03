@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/** Search results surface: the ranked settings/car-data result list, its stagger
 *  timing constant, and the per-result pop-in helper. Peeled out of
 *  SettingsSearch.kt into its own file. */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.bloo.bluelink.data.TileCommandRunner
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
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
internal fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    /** Show at most this many, best first. See the call site: with a keyboard
     *  up there is no room for a long list, and ranking is what makes taking
     *  the top few the right answer rather than an arbitrary one. */
    limit: Int = Int.MAX_VALUE,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    // remember(query), for the same reason `entries` below is remembered: this composable
    // recomposes on every UiState emission (it takes the whole UiState), not just on a
    // keystroke, and the token list only ever depends on `query`. The regex itself is already
    // compiled once at file scope (RxSearchTokens); the split + filter it drives were still
    // re-running per emission, and their result is the `results` memo's key below, so an
    // equal-but-new list would have defeated that memo on every recomposition.
    val tokens = remember(query) {
        query.lowercase().split(RxSearchTokens)
            .filter { it.isNotBlank() && it !in SearchStopwords }
    }
    // Same source the main Settings screen uses for its own Security card gate.
    val canBio = remember { vm.canUseBiometrics() }

    // remember(state, appearance, notif, vm, canBio), not rebuilt inline: none of the ~50+
    // add(...) calls below read `query` at all -- only the scoring pass further down does --
    // so building this whole list (and every entry's own composable lambda) fresh on every
    // keystroke was pure waste. Now it only rebuilds when the underlying settings/vehicle data
    // actually changes. Also include settingsMode so entries rebuild when simple/advanced mode changes.
    val entries = remember(state, appearance, notif, vm, canBio, state.settingsMode) {
        buildSettingsSearchEntries(state, appearance, notif, vm, canBio)
    }

    // Matches render FIRST (top of this composable's output), the AI answer
    // LAST -- this composable is placed above the floating search bar, so the
    // resulting stack top-to-bottom is [suggested results] [AI tile]
    // [search bar], matching the requested reading order bottom-up.
    // Ranked, not filtered. The fuzzy pass is a FALLBACK, only reached when the
    // strict one found nothing -- so a real match is never outranked by a
    // one-typo guess, and the cost of scanning every word of every entry is
    // only paid on a query that was going to show "no matches" otherwise.
    // remember(entries, tokens, limit) -- the exact same reasoning as the `entries` memo
    // above, applied to the pass that CONSUMES it. Scoring is a full scan of every one of the
    // ~50 entries' keyword strings (twice, when the strict pass finds nothing and the fuzzy
    // fallback runs) plus a sort, and none of it depends on anything but these three values.
    // It was re-running on every recomposition of this composable, which includes every
    // unrelated UiState emission while the panel is open -- a car's status tick, a weather
    // fetch, a log line -- not just the keystrokes it actually tracks.
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
    // Floating search results use GlassSurface, the same unified glass chrome
    // as every other floating surface (dialogs, overlays, status bar).
    // This replaces the old plain Card + tonal elevation approach.
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
        ) {
            Text(
                "No matches for \"$query\"",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        // Restarts the stagger whenever the actual SET of results changes -- not on every
        // keystroke, which would re-pop a list that hasn't actually moved just because the
        // user is still typing the same word. Titles joined is cheap and exactly captures
        // "did the ranked list change," which is the only thing that should trigger this.
        val resultsKey = results.joinToString("|") { it.title }
        results.forEachIndexed { i, e ->
            PopVisible(visible = staggeredResultVisible(resultsKey, i)) {
                GlassSurface(
                    shape = resultCardShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp)) {
                        // A small icon badge per result, the same "leading circle" language
                        // the update pebble and settings hero stats use -- these cards used
                        // to open straight on bold text with nothing to distinguish a
                        // toggle-able setting from an informational readout at a glance.
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
            ) {
                Row(Modifier.padding(16.dp)) {
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

    // A recognised command ("lock my Ioniq", "start smart climate", "stop
    // charging") actually runs -- reuses TileCommandRunner, the same
    // execution path used elsewhere in the app, so this isn't a separate,
    // untested way of sending vehicle commands. If the query doesn't name a
    // specific car, this falls back to a single car (unambiguous) or asks
    // the user to be more specific (multiple cars, none named).
    //
    // Gated on submittedQuery, NOT the live query -- this actually sends a
    // command to the car, so it must only run once the user has deliberately
    // submitted (Enter/search key, or a suggestion tap), never mid-typing off
    // a debounce timer. Typing "lock my car" used to run the lock the moment
    // the debounce elapsed, whether or not that's what the user meant to do.
    // parseVehicleCommand reads a bare temperature ("climate at 70"), so this follows the TEMPERATURE unit.
    val metricUnits = !appearance.useFahrenheit
    var command by remember(submittedQuery, metricUnits) {
        mutableStateOf(if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits))
    }
    // When Gemini Nano is enabled, use it to enhance command parsing for ambiguous/unmatched queries
    if (state.aiEnabled && command == null && submittedQuery.isNotBlank()) {
        LaunchedEffect(submittedQuery) {
            try {
                val enhanced = vm.enhanceCommandParsing(submittedQuery, null)
                if (enhanced != null) {
                    command = enhanced
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Graceful fallback if AI enhancement fails
            }
        }
    }
    val resolvedCommand = command
    if (resolvedCommand != null) {
        val ctx = LocalContext.current
        // Whole-word, longest-match car resolution -- NOT a bare substring test.
        // A plain `name in query` lets "Ioniq" match inside "lock my Ioniq 5",
        // so a command meant for the "Ioniq 5" would be sent to the "Ioniq"
        // (list-order-first). Instead require the name to appear as a bounded
        // token sequence, and when several names match prefer the longest. If
        // several still match at that longest length the query is genuinely
        // ambiguous, so refuse to dispatch and ask which car (targetVehicle
        // stays null → the "Which car?" branch below).
        //
        // remember(submittedQuery, state.vehicles): this is the one pattern SettingsIndex's
        // own Rx* block explicitly could NOT hoist to file scope ("any pattern built from a
        // runtime value (a vehicle's own name) is left where it is"), so it gets the other
        // half of that fix instead -- memoized rather than constant. `Regex(...)` compiles
        // its pattern on CONSTRUCTION, and this constructed ONE PER CAR inside a composable
        // body that recomposes on every UiState emission while a submitted command is on
        // screen (this branch is live for as long as the Action card shows, and the command
        // it ran is exactly what makes the car's status start ticking). The resolution only
        // depends on the submitted text and the car list, so those are the keys.
        val targetVehicle = remember(submittedQuery, state.vehicles) {
            val q = submittedQuery.lowercase()
            val nameMatches = state.vehicles.filter { v ->
                v.name.isNotBlank() &&
                    Regex("\\b" + Regex.escape(v.name.lowercase()) + "\\b").containsMatchIn(q)
            }
            val longestMatchLen = nameMatches.maxOfOrNull { it.name.length }
            val namedVehicle = nameMatches.filter { it.name.length == longestMatchLen }.singleOrNull()
            // Only fall back to "the one car" when NO name matched at all; if a name
            // matched but was ambiguous, do not silently pick a car.
            namedVehicle ?: if (nameMatches.isEmpty()) state.vehicles.singleOrNull() else null
        }
        var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
        var commandExecuted by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            if (targetVehicle != null && !commandExecuted) {
                if (resolvedCommand.cmd == "open_app") {
                    // Not a car command -- launches the OEM companion app
                    // (same action as OwnerLinks' own "<appName> app" button)
                    // rather than going through TileCommandRunner.
                    val links = targetVehicle.brand.links
                    openApp(ctx, listOf(links.appPackage), links.playStoreUrl)
                    actionResult = "Opening ${links.appName}"
                    commandExecuted = true
                } else {
                    actionRunning = true
                    val result = runCatching { TileCommandRunner.run(ctx, targetVehicle.vin, resolvedCommand.cmd, resolvedCommand.climateTarget) }.getOrNull()
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
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

    // Free-form command, via the AI, when the deterministic parser did not
    // recognise the phrasing. Data questions already go through askAi below --
    // this is the other half: making the car DO something described in words
    // the parser has no pattern for.
    //
    // It asks before it acts, and that is deliberate rather than timid. The
    // parser runs its commands immediately because a pattern it matched is a
    // phrasing someone wrote down on purpose; a model's reading of an
    // unanticipated sentence is a guess, and the cost of a wrong guess here is
    // a car unlocked on a street somewhere. One tap is a small price for the
    // difference between "the app did what I said" and "the app did what a
    // model thought I said". aiResolveCommand has already thrown out anything
    // that is not a real action on a real car of yours, so what this offers is
    // always executable -- the question is only whether it is what you meant.
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
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
                        // MorphTextButton, not a bare Button: this was the one plain
                        // Material button left in the app, so it was the only standard
                        // button that neither morphed on press nor fired the click
                        // haptic every other button gives. Its label also flips
                        // "Run it" -> "Working…", which is exactly the content-width
                        // spring MorphButton exists to animate.
                        //
                        // primary/onPrimary passed explicitly because they are what
                        // Material's Button defaulted to here. MorphTextButton's own
                        // default is the calmer buttonContainer(), and this is the
                        // card's primary action -- the conversion should change the
                        // FEEL, not quietly demote the emphasis.
                        SafeMorphTextButton(
                            text = if (running) "Working…" else "Run it",
                            onClick = {
                                running = true
                                scope.launch {
                                    val r = runCatching {
                                        TileCommandRunner.run(ctx, car.vin, p.first, "default")
                                    }.getOrNull()
                                    ran = r?.message ?: "Command failed"
                                    running = false
                                    vm.refreshStatus(car)
                                }
                            },
                            enabled = !running,
                            emphasis = ButtonEmphasis.Primary,
                        )
                    }
                }
            }
        }
    }

    // On-device AI reply (when enabled): answer the question in natural
    // language -- a fallback/complement for questions with no structured
    // match above, or a plain-language gloss when there is one.
    //
    // Gated on submittedQuery, not the live query -- this fires a real AI
    // request (network/compute cost, and it used to visibly show "Thinking…"
    // while the user was still mid-word), so it must wait for a deliberate
    // submit rather than firing on every keystroke's debounce.
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
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

/**
 * One AutoLock toggle, as a search result. AutoLockConfig isn't part of [UiState] (it lives in
 * SettingsStore's own DataStore keys -- see AutoLockConfig's own doc for why), so unlike every
 * other per-car toggle in this file it can't be driven by a synchronous [checked]/[onToggle]
 * pair reading straight off `state`. This loads its own copy the exact same way
 * AutoLockSettingsGroup's Settings-card row does (LaunchedEffect(v.vin) on first composition),
 * so a result only renders its real value once that read completes rather than flashing a
 * default first.
 */
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
