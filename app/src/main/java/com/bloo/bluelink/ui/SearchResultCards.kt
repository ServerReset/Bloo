package com.bloo.bluelink.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
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
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.links
import kotlinx.coroutines.launch
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonIconSize
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.content
import androidx.activity.result.contract.ActivityResultContracts

// --- The AI-answer and command-action cards shown under search results ---

/** The on-device AI's plain-language answer to the submitted query. */
@Composable
internal fun AiAnswer(
    state: UiState,
    vm: AppViewModel,
    submittedQuery: String,
    hazeState: dev.chrisbanes.haze.HazeState?,
) {
    LaunchedEffect(submittedQuery) {
        if (submittedQuery.isNotBlank()) {
            vm.askAi(submittedQuery)
        } else {
            vm.clearAiReply()
        }
    }
    val thinking = "search" in state.aiBusy
    val reply = state.aiSearchReply
    ExpandVisibility(thinking || reply != null, from = Alignment.Bottom) {
        GlassSurface(
            shape = StandardShape,
            modifier = Modifier.fillMaxWidth(),
            hazeState = hazeState,
        ) {
            ResultCardColumn(AppIcons.AutoAwesome, "AI answer") {
                if (reply != null) {
                    RevealText(reply, style = SecondaryTextStyle())
                } else {
                    MutedText("Thinking…")
                }
            }
        }
    }
}

/** The AI's guessed command for a phrasing the deterministic parser did not recognise. */
@Composable
internal fun AiCommandProposal(
    submittedQuery: String,
    state: UiState,
    vm: AppViewModel,
    hazeState: dev.chrisbanes.haze.HazeState?,
) {
    val ctx = LocalContext.current
    var proposal by remember(submittedQuery) { mutableStateOf<Pair<String, String>?>(null) }
    var ran by remember(submittedQuery) { mutableStateOf<String?>(null) }
    var running by remember(submittedQuery) { mutableStateOf(false) }
    LaunchedEffect(submittedQuery) {
        proposal = vm.aiResolveCommand(submittedQuery)
    }
    val p = proposal
    if (p != null) {
        val car = state.vehicles.firstOrNull { it.vin == p.second }
        ResultCard(hazeState) {
            ResultCardColumn(AppIcons.Bolt, "Did you mean?") {
                RevealText(
                    ran ?: "${aiCommandLabel(p.first)} ${car?.name ?: "your car"}",
                    style = SecondaryTextStyle(),
                )
                if (ran == null && car != null) {
                    val scope = rememberCoroutineScope()
                    // MorphTextButton, not a bare Button: this was the one plain Material
                    // button left in the app, so it was the only standard button that neither
                    // morphed on press nor fired the click haptic every other button gives.
                    MorphTextButton(
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

/**
 * A plain-language request that names a setting ("stop it vibrating", "use kilometres", "make the
 * text bigger") mapped to one of the app's settings -- switches, pickers and ranges -- by Gemini
 * Nano. Nothing changes until the person taps Apply.
 */
@Composable
internal fun AiSettingProposal(
    submittedQuery: String,
    state: UiState,
    appearance: com.bloo.bluelink.data.SettingsStore.Appearance,
    notif: com.bloo.bluelink.data.SettingsStore.NotificationPrefs,
    vm: AppViewModel,
    hazeState: dev.chrisbanes.haze.HazeState?,
) {
    val ctx = remember(appearance, notif, state) { SettingContext(appearance, notif, state) }
    val actions = remember(state.aiSupported, state.aiEnabled, state.settingsMode, state.shizukuAvailable, state.vehicles, appearance.customPalettes) {
        availableSettingActions(state, appearance)
    }
    val candidates = remember(actions, ctx) { actions.map { it.candidate(ctx) } }
    var proposal by remember(submittedQuery) { mutableStateOf<com.bloo.bluelink.data.AiSettingResolver.Resolution?>(null) }
    var applied by remember(submittedQuery) { mutableStateOf(false) }
    LaunchedEffect(submittedQuery) { proposal = vm.aiResolveSetting(submittedQuery, candidates) }
    val p = proposal ?: return
    val action = actions.firstOrNull { it.id == p.id } ?: return
    val current = candidates.firstOrNull { it.id == p.id }?.current
    val already = !action.isAction && current == p.value
    val target = action.describe(p.value)
    // Location for weather needs the permission first; the card asks for it, then runs the action.
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) { action.write(vm, p.value); applied = true } else vm.reportError("Location permission denied. Type a place instead")
    }
    ResultCard(hazeState) {
        ResultCardColumn(AppIcons.AutoAwesome, "Did you mean?") {
            RevealText(
                when {
                    action.isAction -> action.title
                    action.text -> "Set ${action.title} to \"${p.value}\""
                    action.options.isEmpty() && action.min == null -> "Turn ${action.title} $target"
                    else -> "Set ${action.title} to $target"
                } + if (already && !action.isAction) " (already set)" else "",
                style = SecondaryTextStyle(),
            )
            if (!already) {
                MorphTextButton(
                    text = if (applied) "Done" else if (action.isAction) "Run" else "Apply",
                    onClick = {
                        if (action.needsLocationPermission) {
                            locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            action.write(vm, p.value)
                            applied = true
                        }
                    },
                    enabled = !applied,
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
        }
    }
}

/**
 * The card for a recognised command ("lock my Ioniq"): it runs via VehicleCommandRunner, the same
 * path as the rest of the app, and reports what happened. With no named car it falls back to the
 * single car, or asks which when several exist.
 */
@Composable
internal fun CommandActionResult(
    command: ParsedVehicleCommand,
    submittedQuery: String,
    state: UiState,
    vm: AppViewModel,
    hazeState: dev.chrisbanes.haze.HazeState?,
) {
    val ctx = LocalContext.current
    val targetVehicle = remember(submittedQuery, state.vehicles) {
        resolveCommandTarget(submittedQuery, state.vehicles)
    }
    var actionResult by rememberSaveable(submittedQuery) { mutableStateOf<String?>(null) }
    var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
    // Saveable: the submitted query survives an activity recreation, so the 'already ran' flag must too, or
    // a car command would be sent again on every rotation or theme change.
    var commandExecuted by rememberSaveable(submittedQuery) { mutableStateOf(false) }
    LaunchedEffect(submittedQuery) {
        if (targetVehicle != null && !commandExecuted) {
            if (command.cmd == "open_app") {
                // Not a car command: launches the OEM companion app (as OwnerLinks does).
                val links = targetVehicle.brand.links
                openApp(ctx, listOf(links.appPackage), links.playStoreUrl)
                actionResult = "Opening ${links.appName}"
                commandExecuted = true
            } else {
                actionRunning = true
                val result = runCatching { VehicleCommandRunner.run(ctx, targetVehicle.vin, command.cmd, command.climateTarget) }.getOrNull()
                actionResult = result?.message ?: "Command failed"
                actionRunning = false
                vm.refreshStatus(targetVehicle)
                commandExecuted = true
            }

            // Track this command as recently used
            try {
                RecentCommandsTracker(ctx).recordUsage(command.cmd)
            } catch (e: Exception) {
                // Silently fail - tracking is not critical
            }
        }
    }
    ResultCard(hazeState) {
        ResultCardColumn(AppIcons.Bolt, "Action") {
            RevealText(
                when {
                    targetVehicle == null -> {
                        val example = state.vehicles.firstOrNull()?.name ?: "car"
                        "Which car? Mention its name, e.g. \"${command.label} my $example\"."
                    }
                    actionRunning -> "${command.label} ${targetVehicle.name}…"
                    actionResult != null -> actionResult ?: "${command.label} ${targetVehicle.name}"
                    else -> "${command.label} ${targetVehicle.name}"
                },
                style = SecondaryTextStyle(),
            )
        }
    }
}

/** One answer card in the search results: glass, full width, and eased in when it arrives like every other card. */
@Composable
private fun ResultCard(hazeState: dev.chrisbanes.haze.HazeState?, content: @Composable () -> Unit) {
    EnterOnce {
        GlassSurface(shape = StandardShape, modifier = Modifier.fillMaxWidth(), hazeState = hazeState, content = { content() })
    }
}

/** The inside of a result card: a glyph and [title] on top, then [content], all on the app's card padding and row gap. */
@Composable
private fun ResultCardColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(GapSection), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
            Spacer(Modifier.width(GapRow))
            TitleSmallText(title)
        }
        content()
    }
}
