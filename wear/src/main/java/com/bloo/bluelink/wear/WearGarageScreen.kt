package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchLockTiming
import com.bloo.bluelink.data.WatchPinPolicy
import kotlinx.coroutines.launch

/**
 * The whole watch UI: swipe left/right between cars, scroll up/down a car's pebbles.
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
                    title = "Enter PIN to open Bloo",
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
                WearMessage("Open Bloo on your phone to add a car.")
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
    requestCommand: ((() -> Unit)) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // The phone's last command result, so a failure can be surfaced and pending cleared.
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    // A newer watch build the phone is advertising.
    val updateAdvice by WearDataLayerSync.updateAdvice.collectAsStateWithLifecycle(initialValue = null)

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            updateAdvice?.let { advice ->
                item {
                    WearPebble(title = "Update available", icon = Icons.Filled.SystemUpdate) {
                        WearActionRow(label = "Update Bloo", icon = Icons.Filled.Download) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(advice.apkUrl),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
            item { WearMessage(v.name) }
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
            lastResult?.takeIf { it.vin == v.vin && !it.ok }?.let { r ->
                item {
                    WearMessage(r.message ?: "Command failed")
                }
            }
        }
    }
}

@Composable
private fun WearMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = TextAlign.Center,
            maxLines = 3,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
