package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
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
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.links
import kotlinx.coroutines.launch

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
    AnimatedVisibility(
        visible = thinking || reply != null,
        enter = expandEnterSized(Alignment.Bottom),
        exit = expandExitSized(Alignment.Bottom),
    ) {
        GlassSurface(
            shape = StandardShape,
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
        GlassSurface(
            shape = StandardShape,
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
    var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
    var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
    var commandExecuted by remember(submittedQuery) { mutableStateOf(false) }
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
    GlassSurface(
        shape = StandardShape,
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
                        "Which car? Mention its name, e.g. \"${command.label} my $example\"."
                    }
                    actionRunning -> "${command.label} ${targetVehicle.name}…"
                    actionResult != null -> actionResult ?: "${command.label} ${targetVehicle.name}"
                    else -> "${command.label} ${targetVehicle.name}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
