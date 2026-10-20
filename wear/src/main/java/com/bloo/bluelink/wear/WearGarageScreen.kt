package com.bloo.bluelink.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
// The PULL-TO-REFRESH comes from the phone Material3, not the Wear one: wear-compose-material3 has no
// pull-to-refresh, and the modifier is theme-agnostic -- it is a nested-scroll offset and nothing else.
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import com.bloo.bluelink.data.WatchPinPolicy

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
    val signedIn by WearCredentialSync.signedIn.collectAsStateWithLifecycle()
    val pendingCommands by WearCommands.pending.collectAsStateWithLifecycle()
    // The command waiting on an "are you sure": anything that opens the car. Null when none is.
    var confirmAction by remember { mutableStateOf<String?>(null) }
    // The quarter whose control is being chosen (index), or null; and the saved choice for each.
    var choosingSlot by remember { mutableStateOf<Int?>(null) }
    val faceContext = LocalContext.current
    val slotKeys by WearFacePrefs.flow(faceContext).collectAsStateWithLifecycle()
    // An action chosen on the Tile. It runs through requestCommand, so the PIN gate, the pending
    // state and the haptic result are the same as a tap on the face -- the tile is a second door into
    // the same room, not a second room.
    LaunchedEffect(tileRequest) {
        val key = tileRequest?.key ?: return@LaunchedEffect
        // The tile's button is one of the face's own controls, so it runs exactly as a tap on the face would.
        WearFaceControls.byKey(key)?.takeIf { it.available(v) }?.let { control ->
            if (control.confirm(v)) confirmAction = control.action
            else requestCommand { repo.command(v.vin, control.action) }
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
    confirmAction?.let { pendingAction ->
        WearConfirmScreen(
            icon = Icons.Filled.LockOpen,
            title = "Unlock ${v.name}?",
            detail = "The doors open for anyone nearby.",
            confirmLabel = "Unlock",
            destructive = true,
            onConfirm = { confirmAction = null; requestCommand { repo.command(v.vin, pendingAction) } },
            onCancel = { confirmAction = null },
        )
        return
    }
    choosingSlot?.let { slot ->
        WearFaceEditor(
            car = v,
            slots = slotKeys,
            initialSlot = slot,
            onChange = { slots ->
                WearFacePrefs.save(faceContext, slots)
                // The tile shows the same controls: ask it to redraw.
                WearSurfaces.refresh(faceContext)
            },
            onDone = { choosingSlot = null },
        )
        return
    }
    val pageState = androidx.wear.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    // Page 0 is the face, full-bleed so its ring IS the display's edge and is centred on it; page 1 is the
    // car's details. The pull-down refresh lives on the pager, so it works from the face (nothing above it
    // to scroll) exactly the way the phone's does.
    androidx.wear.compose.foundation.pager.VerticalPager(
        state = pageState,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        if (index == 0) {
            WearFacePage(
                repo = repo, v = v, slotKeys = slotKeys, pendingCommands = pendingCommands,
                faceStatus = faceStatus, refreshing = refreshing,
                onConfirm = { confirmAction = it }, requestCommand = requestCommand,
                onEditFace = { choosingSlot = 0 },
            )
        } else {
            WearDetailsPage(
                repo = repo, v = v, slotKeys = slotKeys, refreshing = refreshing, signedIn = signedIn,
                updateError = updateError, updateRun = updateRun, downloading = downloading, progress = progress,
                lastResult = lastResult, onChooseSlot = { choosingSlot = it },
            )
        }
    }
}

// The watch's settings pebbles -- Watch alerts and Auto lock -- are in WearSettingsPebbles.kt. They
// are the watch's settings rather than the car page's furniture, and this file is the car page.

/** How long a command's result stays in the face's hub. */
private const val FaceResultMs = 4_000L
