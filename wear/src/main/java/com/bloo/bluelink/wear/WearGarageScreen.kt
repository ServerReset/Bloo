package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
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
                    // What to do about it, on the screen itself. This used to be one bare line, so
                    // "Waiting for your phone…" was the whole answer: nothing said that the phone has
                    // to be running Bloo, or that the two have to be paired, or that this is a state
                    // the watch gets itself out of. `connected` is a live StateFlow fed by the Data
                    // Layer, so this clears the moment the phone appears -- there is deliberately no
                    // "Check again" button, because there is nothing a tap could do that waiting
                    // will not.
                    item {
                        WearCenteredText(
                            text = if (connected) {
                                "Add a car in Bloo on your phone."
                            } else {
                                "Open Bloo on your phone and keep it near the watch."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = null,
                            maxLines = 3,
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
    // The update checker's own error, which was WRITTEN AND NEVER READ. It set "Download failed",
    // "Allow installing apps, then tap again" and the exception text, and no screen collected it -- so
    // a watch update that failed to download, or that needed the unknown-sources toggle, showed
    // nothing at all. Exactly the silent failure the checker's own comments say they were avoiding.
    val updateError by WearUpdateChecker.error.collectAsStateWithLifecycle(initialValue = null)
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
            // THE round-display mechanic, and it is the standard component rather than something
            // hand-rolled: ScalingLazyColumn scales each item by where it sits on the circle -- full
            // size at the centre, shrinking toward the edges -- so a pebble grows as the crown brings
            // it in and shrinks as it leaves.
            //
            // These are the DEFAULTS, written out and turned up. Default scaling is gentle enough that
            // on a full-width pebble you cannot see it; 0.62 at the edge against 1.0 at the centre is
            // the same mechanic with the effect actually legible. maxElementScale stays at 1f so the
            // centred item is never enlarged past its own size, which on a round screen would push its
            // corners under the bezel.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.62f,
            ),
            // Generous vertical inset so the hero and the top pebble clear the bezel; Wear's
            // auto-centring then keeps the focused item centred as the crown scrolls.
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            // The update error, shown where the update lives. It used to go nowhere at all.
            updateError?.let { message ->
                item {
                    WearPebble(title = "Watch update", icon = Icons.Filled.SystemUpdate) {
                        WearCenteredText(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

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

            // HERO, FULL SCREEN: the circle IS the page. The car's state and its four quick actions
            // live INSIDE it, so what the car is doing and the things you do about it are one object
            // rather than a badge with a list underneath. The ring takes the display's own width, so
            // on a round watch the stroke sits at the bezel and everything else is inside it.
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
                WearHero(
                    value = v.bigValue(),
                    caption = v.name,
                    tint = v.heroTint(),
                    progress = v.percent?.let { it / 100f },
                    ring = Modifier.fillMaxWidth().aspectRatio(1f),
                    body = { WearQuickActions(actions, compact = true) },
                )
            }
            // The state and freshness lines sit UNDER the ring, not inside it: the circle holds the
            // value and four 48dp targets and little else on a 227dp face, and these two read fine as
            // the first thing past the fold.
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
