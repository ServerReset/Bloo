package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchPinPolicy
import kotlinx.coroutines.launch

/**
 * The whole watch UI, built for a round face: swipe left/right between cars, scroll up/down a
 * car's pebbles. Each page leads with a centred HERO (the car's name + its at-a-glance state),
 * then compact pebble cards. Wear's `ScalingLazyColumn` centres the item under the crown and
 * shrinks the rest toward the bezel, so nothing runs off the round edge.
 *
 * The PIN gate wraps everything: if the policy says this open needs an unlock, [WearPinScreen]
 * is shown INSTEAD of the garage until the user proves the PIN. Commands are gated separately
 * via [requestCommand] -- see [WatchPinPolicy] for the exact rules.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearGarageScreen(
    repo: WearSnapshotRepository,
    pinStore: WatchPinStore,
    modifier: Modifier = Modifier,
) {
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    val connected by repo.connected.collectAsStateWithLifecycle(initialValue = false)
    val timing = pinStore.timing
    // Session unlock: true once the user has proven the PIN since this screen opened. Only ever
    // consulted for the OPEN gate (and the BOTH command gate) -- see WatchPinPolicy.
    var sessionUnlocked by remember { mutableStateOf(false) }
    // A command requested while the PIN gate is pending, replayed after a successful unlock.
    var pendingCommand by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Whether the PIN screen is up right now.
    var pinPromptVisible by remember { mutableStateOf(false) }

    val needsOpenUnlock = pinStore.hasPin &&
        WatchPinPolicy.requiresUnlock(timing, WatchPinPolicy.GateEvent.OPEN_APP, sessionUnlocked)

    AppScaffold(modifier = modifier) {
        if (needsOpenUnlock) {
            ScreenScaffold(timeText = { TimeText() }) {
                WearPinScreen(
                    store = pinStore,
                    title = "Enter PIN to open",
                    onUnlocked = { sessionUnlocked = true; pinPromptVisible = false },
                )
            }
            return@AppScaffold
        }
        if (pinPromptVisible) {
            ScreenScaffold(timeText = { TimeText() }) {
                WearPinScreen(
                    store = pinStore,
                    title = "Enter PIN to send",
                    onUnlocked = {
                        sessionUnlocked = true
                        pinPromptVisible = false
                        pendingCommand?.invoke()
                        pendingCommand = null
                    },
                )
            }
            return@AppScaffold
        }
        if (vehicles.isEmpty()) {
            ScreenScaffold(timeText = { TimeText() }) {
                ScalingLazyColumn(state = rememberScalingLazyListState(), modifier = Modifier.fillMaxSize()) {
                    item {
                        WearCenteredText(
                            if (connected) "No cars on your phone yet" else "Waiting for your phone…",
                        )
                    }
                }
            }
            return@AppScaffold
        }
        val pagerState = rememberPagerState(pageCount = { vehicles.size })
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            vehicles.getOrNull(page)?.let { v ->
                WearCarPage(
                    repo = repo,
                    v = v,
                    requestCommand = { action ->
                        // Route every command through the PIN gate first.
                        val gated = pinStore.hasPin && WatchPinPolicy.requiresUnlock(
                            timing, WatchPinPolicy.GateEvent.SEND_COMMAND, sessionUnlocked,
                        )
                        if (gated) {
                            pendingCommand = action
                            pinPromptVisible = true
                        } else {
                            action()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WearCarPage(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    requestCommand: (((() -> Unit)) -> Unit),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    val updateAdvice by WearDataLayerSync.updateAdvice.collectAsStateWithLifecycle(initialValue = null)
    val selfUpdate by WearUpdateChecker.available.collectAsStateWithLifecycle(initialValue = null)
    val downloading by WearUpdateChecker.downloading.collectAsStateWithLifecycle(initialValue = false)
    val progress by WearUpdateChecker.progress.collectAsStateWithLifecycle(initialValue = 0f)
    val updateRun = selfUpdate ?: updateAdvice?.let {
        com.bloo.bluelink.data.WorkflowRun(it.runNumber, htmlUrl = "", watchApkUrl = it.apkUrl)
    }

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Generous vertical inset so the hero and the top pebble clear the bezel; Wear's
            // auto-centring then keeps the focused item centred as the crown scrolls.
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            updateRun?.let { run ->
                item {
                    WearPebble(title = "Update available", icon = Icons.Filled.SystemUpdate) {
                        if (downloading) {
                            WearCenteredText("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            WearActionRow(label = "Update now", icon = Icons.Filled.Download) {
                                scope.launch { WearUpdateChecker.downloadAndInstall(context) }
                            }
                        }
                    }
                }
            }

            // HERO: the car, at a glance. Name as the caption, the one thing you look at a watch
            // for as the big value.
            item {
                WearHero(
                    value = v.heroValue(),
                    caption = v.name,
                    tint = v.heroTint(),
                )
            }

            item {
                WearPebble(title = "Controls", icon = Icons.Filled.DirectionsCar) {
                    val locked = v.locked == true
                    WearActionRow(
                        label = if (locked) "Unlock" else "Lock",
                        icon = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    ) { requestCommand { repo.lock(v.vin) } }
                }
            }
            item {
                WearPebble(title = "Climate", icon = Icons.Filled.AcUnit) {
                    val on = v.climateOn == true
                    WearActionRow(
                        label = if (on) "Climate off" else "Climate on",
                        icon = Icons.Filled.AcUnit,
                        active = on,
                    ) { requestCommand { repo.climate(v.vin) } }
                }
            }

            val failure = lastResult?.takeIf { it.vin == v.vin && !it.ok }
            if (failure != null) {
                item {
                    WearCenteredText(
                        failure.message ?: "Command failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** The single value a glance at this car should show: charge/fuel %, else lock state. */
private fun VehicleSnapshot.heroValue(): String =
    percent?.let { "$it%" } ?: locked?.let { if (it) "Locked" else "Unlocked" } ?: "—"

/** Charge/fuel reads in the brand accent; a lock-only car reads in the plain on-background tone. */
@Composable
private fun VehicleSnapshot.heroTint(): Color =
    if (percent != null) Color(0xFF2EBD59) else MaterialTheme.colorScheme.onBackground
