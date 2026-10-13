package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.stateLine
import com.bloo.bluelink.data.updatedLabel
import com.bloo.bluelink.data.WatchPinPolicy
import com.bloo.bluelink.data.supportsHornLights
import kotlinx.coroutines.launch

/**
 * The whole watch UI, built for a round face: swipe left/right between cars, scroll up/down a car's
 * pebbles. Each page leads with a centred HERO (the car's name + its at-a-glance state), then
 * compact pebble cards.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearGarageScreen(
    repo: WearSnapshotRepository,
    pinStore: WatchPinStore,
    modifier: Modifier = Modifier,
    /** Called once the post-restart PIN has opened the watch, so the activity can start its work. */
    onBootUnlocked: () -> Unit = {},
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var bootLocked by remember { mutableStateOf(WearBootGate.isLocked(appContext)) }
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    val connected by repo.connected.collectAsStateWithLifecycle(initialValue = false)
    val timing = pinStore.timing
    val signInCode by WearCredentialSync.signInCode.collectAsStateWithLifecycle()
    // Session unlock: true once the user has proven the PIN since this screen opened. Only ever
    // consulted for the OPEN gate (and the BOTH command gate) -- see WatchPinPolicy.
    var sessionUnlocked by remember { mutableStateOf(false) }
    // A command requested while the PIN gate is pending, replayed after a successful unlock.
    var pendingCommand by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinPromptVisible by remember { mutableStateOf(false) }

    val needsOpenUnlock = pinStore.hasPin &&
        WatchPinPolicy.requiresUnlock(timing, WatchPinPolicy.GateEvent.OPEN_APP, sessionUnlocked)

    AppScaffold(modifier = modifier) {
        if (bootLocked) {
            ScreenScaffold(timeText = { TimeText() }) {
                WearPinScreen(
                    store = pinStore,
                    title = "Restarted: enter PIN",
                    onUnlocked = {
                        WearBootGate.open(appContext)
                        bootLocked = false
                        sessionUnlocked = true
                        onBootUnlocked()
                    },
                )
            }
            return@AppScaffold
        }
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
        signInCode?.let { code ->
            ScreenScaffold(timeText = { TimeText() }) {
                ScalingLazyColumn(state = rememberScalingLazyListState(), modifier = Modifier.fillMaxSize()) {
                    item { WearCenteredText("Sign in from your phone") }
                    item { WearHero(value = code, caption = "Confirm this code", tint = MaterialTheme.colorScheme.primary) }
                }
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
        Box(Modifier.fillMaxSize()) {
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
        // Which car this is, when there is more than one.
        if (vehicles.size > 1) HorizontalPageIndicator(pagerState = pagerState)
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
    val signedIn by WearCredentialSync.signedIn.collectAsStateWithLifecycle()
    val pendingCommands by WearCommands.pending.collectAsStateWithLifecycle()
    var confirmUnlock by remember { mutableStateOf(false) }
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    val updateAdvice by WearDataLayerSync.updateAdvice.collectAsStateWithLifecycle(initialValue = null)
    val selfUpdate by WearUpdateChecker.available.collectAsStateWithLifecycle(initialValue = null)
    val downloading by WearUpdateChecker.downloading.collectAsStateWithLifecycle(initialValue = false)
    val progress by WearUpdateChecker.progress.collectAsStateWithLifecycle(initialValue = 0f)
    // A buzz when a command comes back: a confirm for success, a reject for a failure -- the watch is
    // often glanced at, not looked at.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(lastResult) {
        lastResult?.takeIf { it.vin == v.vin }?.let {
            haptics.performHapticFeedback(if (it.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        }
    }
    val updateRun = selfUpdate ?: updateAdvice?.let {
        com.bloo.bluelink.data.WorkflowRun(it.runNumber, htmlUrl = "", watchApkUrl = it.apkUrl)
    }

    // Unlocking is the one command that opens the car, so it asks once more.
    if (confirmUnlock) {
        ScreenScaffold(timeText = { TimeText() }) {
            ScalingLazyColumn(state = rememberScalingLazyListState(), modifier = Modifier.fillMaxSize()) {
                item { WearCenteredText("Unlock ${v.name}?") }
                item {
                    WearActionRow("Unlock", Icons.Filled.LockOpen) {
                        confirmUnlock = false
                        requestCommand { repo.lock(v.vin) }
                    }
                }
                item { WearActionRow("Cancel", Icons.Filled.Close) { confirmUnlock = false } }
            }
        }
        return
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
                    value = v.bigValue(),
                    caption = v.name,
                    tint = v.heroTint(),
                    progress = v.percent?.let { it / 100f },
                )
            }
            v.stateLine()?.let { line ->
                item {
                    WearCenteredText(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            v.updatedLabel()?.let { updated ->
                item {
                    WearCenteredText(
                        "Updated $updated",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (v.isStale()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            item {
                val actions = buildList {
                    val locked = v.locked == true
                    add(WearQuickAction(
                        if (locked) "Unlock" else "Lock", if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        pending = WearCommands.key(v.vin, CarAction.TOGGLE_LOCK) in pendingCommands,
                    ) {
                        if (locked) confirmUnlock = true else requestCommand { repo.lock(v.vin) }
                    })
                    val on = v.climateOn == true
                    add(WearQuickAction(if (on) "Climate off" else "Climate", Icons.Filled.AcUnit, active = on, pending = WearCommands.key(v.vin, CarAction.TOGGLE_CLIMATE) in pendingCommands) {
                        requestCommand { repo.climate(v.vin) }
                    })
                    if (v.hasBattery) {
                        val charging = v.charging == true
                        add(WearQuickAction(if (charging) "Stop charge" else "Charge", Icons.Filled.Bolt, active = charging, pending = WearCommands.key(v.vin, CarAction.TOGGLE_CHARGE) in pendingCommands) {
                            requestCommand { repo.charge(v.vin) }
                        })
                    }
                    if (v.toVehicle().supportsHornLights) {
                        add(WearQuickAction("Flash", Icons.Filled.FlashOn, pending = WearCommands.key(v.vin, CarAction.FLASH_LIGHTS) in pendingCommands) { requestCommand { repo.flashLights(v.vin) } })
                    }
                }
                WearQuickActions(actions)
            }
            if (signedIn) {
                item { WearActionRow(label = "Refresh", icon = Icons.Filled.Refresh) { repo.refresh() } }
            }
            item { WearAlertsPebble() }
            item { WearAutoLockPebble() }

            val failure = lastResult?.takeIf { it.vin == v.vin && !it.ok }
            if (failure != null) {
                item {
                    WearCenteredText(
                        failure.message ?: "Command failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** Charge/fuel reads in the brand accent; a lock-only car reads in the plain on-background tone. */
@Composable
private fun VehicleSnapshot.heroTint(): Color =
    if (percent != null) Color(BlooColors.chargeGreen) else MaterialTheme.colorScheme.onBackground

/** The watch's notification switches: a pebble of three toggles, kept deliberately short. */
@Composable
private fun WearAlertsPebble() {
    val context = LocalContext.current
    val prefs = remember { WearNotificationPrefs(context) }
    var charging by remember { mutableStateOf(prefs.charging) }
    var complete by remember { mutableStateOf(prefs.chargeComplete) }
    var low by remember { mutableStateOf(prefs.lowBattery) }
    WearPebble(title = "Watch alerts", icon = Icons.Filled.Notifications) {
        WearActionRow("Charging card", Icons.Filled.Bolt, active = charging) { charging = !charging; prefs.charging = charging; prefs.sendToPhone(context) }
        WearActionRow("Charged", Icons.Filled.BatteryFull, active = complete) { complete = !complete; prefs.chargeComplete = complete; prefs.sendToPhone(context) }
        WearActionRow("Low battery", Icons.Filled.BatteryAlert, active = low) { low = !low; prefs.lowBattery = low; prefs.sendToPhone(context) }
    }
}

/**
 * The watch's own AutoLock, tagged **beta**: lock the car when the wearer walks away. Off by
 * default, and honest about what beta means -- the phone does not need to be nearby, and the watch
 * only ever locks a car it has data for and that is not already locked.
 */
@Composable
private fun WearAutoLockPebble() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(WearAutoLock.isEnabled(context)) }
    WearPebble(title = if (WearAutoLock.BETA) "Auto lock · Beta" else "Auto lock", icon = Icons.Filled.Lock) {
        WearActionRow("Lock when I walk away", Icons.Filled.Lock, active = enabled) {
            enabled = !enabled
            WearAutoLock.setEnabled(context, enabled)
        }
        WearCenteredText(
            if (enabled) "On: the watch locks the car about a minute after it feels you set off."
            else "Beta: the watch locks the car when it feels you leave. Off by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )
    }
}
