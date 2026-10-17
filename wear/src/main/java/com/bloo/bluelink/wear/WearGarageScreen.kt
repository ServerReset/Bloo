package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
// The PULL-TO-REFRESH comes from the phone Material3, not the Wear one: wear-compose-material3 has no
// pull-to-refresh, and the modifier is theme-agnostic -- it is a nested-scroll offset and nothing else.
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale
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
    /** An action chosen on the watch's Tile, to run through the same gate as a tap on the face. */
    tileRequest: WearMainActivity.TileRequest? = null,
    /** Called once that action has been run, so the activity stops holding it. */
    onTileRequestHandled: () -> Unit = {},
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
            // The way out is the ✕ beside the code: cancelling on the phone alone left this screen covering the
            // whole watch with nothing to press.
            WearMessageScreen(
                icon = Icons.Filled.Watch,
                title = "Sign in from your phone",
                detail = "Check this code matches the phone.",
            ) {
                WearHero(value = code, caption = "", tint = MaterialTheme.colorScheme.primary)
                WearActionRow("Cancel", Icons.Filled.Close) { WearCredentialSync.cancel() }
            }
            return@AppScaffold
        }
        if (vehicles.isEmpty()) {
            // What to do about it, on the screen itself. `connected` is a live StateFlow fed by the Data Layer,
            // so this clears the moment the phone appears; there is no "Check again", because a tap could do
            // nothing that waiting will not.
            WearMessageScreen(
                icon = if (connected) Icons.Filled.DirectionsCar else Icons.Filled.PhoneAndroid,
                title = if (connected) "No cars yet" else "Waiting for your phone",
                detail = if (connected) "Add a car in Bloo on your phone." else "Open Bloo on your phone and keep it near the watch.",
            )
            return@AppScaffold
        }
        val pagerState = rememberPagerState(pageCount = { vehicles.size })
        Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            vehicles.getOrNull(page)?.let { v ->
                WearCarPage(
                    repo = repo,
                    v = v,
                    // A Tile action belongs to the car the tile was showing, so only the page actually
                    // on screen takes it: the pager composes its neighbours, and they would run it too.
                    tileRequest = tileRequest?.takeIf { page == pagerState.currentPage },
                    onTileRequestHandled = onTileRequestHandled,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WearCarPage(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    requestCommand: (((() -> Unit)) -> Unit),
    tileRequest: WearMainActivity.TileRequest? = null,
    onTileRequestHandled: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The face is the first item and the one centred on arrival, so opening the app lands on it.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val signedIn by WearCredentialSync.signedIn.collectAsStateWithLifecycle()
    val pendingCommands by WearCommands.pending.collectAsStateWithLifecycle()
    var confirmUnlock by remember { mutableStateOf(false) }
    // An action chosen on the Tile. It runs through requestCommand, so the PIN gate, the pending
    // state and the haptic result are the same as a tap on the face -- the tile is a second door into
    // the same room, not a second room.
    LaunchedEffect(tileRequest) {
        val key = tileRequest?.key ?: return@LaunchedEffect
        when (key) {
            // Unlock is the one command that opens the car, so it asks once more, exactly as the button does.
            WearTileActions.KEY_LOCK ->
                if (v.locked == true) confirmUnlock = true else requestCommand { repo.lock(v.vin) }
            WearTileActions.KEY_CLIMATE -> requestCommand { repo.climate(v.vin) }
            WearTileActions.KEY_CHARGE -> requestCommand { repo.charge(v.vin) }
            WearTileActions.KEY_FLASH -> requestCommand { repo.flashLights(v.vin) }
        }
        onTileRequestHandled()
    }
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    val updateAdvice by WearDataLayerSync.updateAdvice.collectAsStateWithLifecycle(initialValue = null)
    // The update checker's own error, which used to be written and never read. It set "Download
    // failed", "Allow installing apps, then tap again" and the exception text, and no screen collected
    // it -- so a watch update that failed to download, or that needed the unknown-sources toggle,
    // showed nothing at all. Exactly the silent failure the checker's own comments say they exist to
    // avoid, with the plumbing already in place.
    val updateError by WearUpdateChecker.error.collectAsStateWithLifecycle(initialValue = null)
    // Pull-to-refresh: the standard Material pull, applied to the column. The gesture only fires when
    // the list is already at the very top -- mid-scroll pulls belong to the list -- which is what
    // "pull down from the top of the car" means.
    val refreshing by repo.refreshing.collectAsStateWithLifecycle(initialValue = false)
    val pullState = rememberPullToRefreshState()
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
    // The hub's moment line: "Sending…" while a command is out, the result for a few seconds after, and
    // otherwise a warning when the reading is old -- the face says what is happening without a toast.
    var recentResult by remember { mutableStateOf<com.bloo.bluelink.data.WatchCommandResult?>(null) }
    LaunchedEffect(lastResult) {
        val r = lastResult?.takeIf { it.vin == v.vin } ?: return@LaunchedEffect
        recentResult = r
        kotlinx.coroutines.delay(FaceResultMs)
        if (recentResult == r) recentResult = null
    }
    val sending = pendingCommands.any { it.startsWith("${v.vin}:") }
    val faceStatus = when {
        sending -> FaceStatus("Sending…")
        recentResult != null -> if (recentResult!!.ok) FaceStatus("Done") else FaceStatus("Didn't go through", error = true)
        v.isStale() -> v.updatedLabel()?.let { FaceStatus("Updated $it", error = true) }
        else -> null
    }
    val updateRun = selfUpdate ?: updateAdvice?.let {
        com.bloo.bluelink.data.WorkflowRun(it.runNumber, htmlUrl = "", watchApkUrl = it.apkUrl)
    }

    // Unlocking is the one command that opens the car, so it asks once more.
    if (confirmUnlock) {
        WearConfirmScreen(
            icon = Icons.Filled.LockOpen,
            title = "Unlock ${v.name}?",
            detail = "The doors open for anyone nearby.",
            confirmLabel = "Unlock",
            destructive = true,
            onConfirm = { confirmUnlock = false; requestCommand { repo.lock(v.vin) } },
            onCancel = { confirmUnlock = false },
        )
        return
    }
    val pageState = androidx.wear.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    // Page 0 is the face, full-bleed so its ring IS the display's edge and is centred on it; page 1 is the
    // car's details. The pull-down refresh lives on the pager, so it works from the face (nothing above it
    // to scroll) exactly the way the phone's does.
    androidx.wear.compose.foundation.pager.VerticalPager(
        state = pageState,
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = refreshing, state = pullState, onRefresh = { repo.refresh() }),
    ) { index ->
        if (index == 0) {
            ScreenScaffold(timeText = { TimeText() }) {
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
                WearCarFace(
                    modifier = Modifier.fillMaxSize(),
                    value = v.bigValue(),
                    caption = v.name,
                    progress = v.percent?.let { it / 100f },
                    tint = faceTint(v.percent, v.charging == true, MaterialTheme.colorScheme.onBackground),
                    actions = actions,
                    charging = v.charging == true,
                    stale = v.isStale(),
                    status = if (refreshing && faceStatus == null) FaceStatus("Updating…") else faceStatus,
                    refreshing = refreshing,
                )
            }
        } else {
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 0.62f),
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            // The car's details, one pebble past the face, with the refresh where the freshness is.
            item { WearStatusPebble(v, refreshing = refreshing, canRefresh = signedIn, onRefresh = { repo.refresh() }) }
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

            item { WearAlertsPebble() }
            // Auto lock needs the car's credentials on the watch, which only the pairing step in Settings on
            // the phone hands over; until then the pebble is a pointer to that step, not a switch.
            item { if (signedIn) WearAutoLockPebble() else WearPairPebble() }

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
    }
}

// The watch's settings pebbles -- Watch alerts and Auto lock -- are in WearSettingsPebbles.kt. They
// are the watch's settings rather than the car page's furniture, and this file is the car page.

/** How long a command's result stays in the face's hub. */
private const val FaceResultMs = 4_000L
