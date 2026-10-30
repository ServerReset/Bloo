package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.PageDots
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.WatchPinPolicy
import com.bloo.uicommon.RevealText

/**
 * The watch app: the gates a launch passes (the PIN after a restart, the PIN to open, a phone sign-in to confirm), then
 * a page per car, swiped left and right. Each car's page is [WearCarPage].
 *
 * Every command goes through [send], which holds it behind the PIN prompt when the wearer's policy asks for one and runs it
 * once the PIN is right -- one gate for the face, the details page, the tile and a notification.
 */
@Composable
fun WearApp(
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
    val context = LocalContext.current
    val vehicles by repo.vehicles.collectAsStateWithLifecycle(initialValue = emptyList())
    val connected by repo.connected.collectAsStateWithLifecycle(initialValue = false)
    val signInCode by WearCredentialSync.signInCode.collectAsStateWithLifecycle()
    var bootLocked by remember { mutableStateOf(WearBootGate.isLocked(context)) }
    // True once the PIN has been proven since this screen opened; consulted for the OPEN gate and the BOTH command gate.
    var sessionUnlocked by remember { mutableStateOf(false) }
    // A command waiting behind the PIN prompt, replayed after a right PIN.
    var pendingCommand by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinPromptVisible by remember { mutableStateOf(false) }

    // The PIN opens the app for as long as it is in front of the wearer: once it stops (screen off, home, another app) the
    // proof is gone, so a tile or complication tap that brings the same instance back asks again.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                sessionUnlocked = false
                pinPromptVisible = false
                pendingCommand = null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val timing = pinStore.timing
    val needsOpenUnlock = pinStore.hasPin &&
        WatchPinPolicy.requiresUnlock(timing, WatchPinPolicy.GateEvent.OPEN_APP, sessionUnlocked)
    val send: (() -> Unit) -> Unit = { command ->
        val gated = pinStore.hasPin &&
            WatchPinPolicy.requiresUnlock(timing, WatchPinPolicy.GateEvent.SEND_COMMAND, sessionUnlocked)
        if (gated) { pendingCommand = command; pinPromptVisible = true } else command()
    }

    AppScaffold(modifier = modifier) {
        when {
            bootLocked -> PinGate(pinStore, "Restarted: enter PIN") {
                WearBootGate.open(context)
                bootLocked = false
                sessionUnlocked = true
                onBootUnlocked()
            }
            needsOpenUnlock -> PinGate(pinStore, "Enter PIN to open") { sessionUnlocked = true; pinPromptVisible = false }
            pinPromptVisible -> PinGate(pinStore, "Enter PIN to send") {
                sessionUnlocked = true
                pinPromptVisible = false
                pendingCommand?.invoke()
                pendingCommand = null
            }
            signInCode != null -> SignInCode(signInCode.orEmpty())
            vehicles.isEmpty() -> WearMessageScreen(
                icon = if (connected) AppIcons.DirectionsCar else AppIcons.PhoneAndroid,
                title = if (connected) "No cars yet" else "Waiting for your phone",
                detail = if (connected) "Add a car in Bloo on your phone." else "Open Bloo on your phone and keep it near the watch.",
            )
            else -> Cars(repo, vehicles, CarPageActions(send, tileRequest, onTileRequestHandled))
        }
    }
}

/** The PIN keypad as a screen of its own. */
@Composable
private fun PinGate(store: WatchPinStore, title: String, onUnlocked: () -> Unit) {
    ScreenScaffold(timeText = { TimeText() }) { WearPinScreen(store = store, title = title, onUnlocked = onUnlocked) }
}

/** The phone is signing this watch in: the code to match, and the way out (the ✕ beside it) if the phone's end is gone. */
@Composable
private fun SignInCode(code: String) {
    WearMessageScreen(icon = AppIcons.Watch, title = "Sign in from your phone", detail = "Check this code matches the phone.") {
        RevealText(
            code,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        WearActionRow("Cancel", AppIcons.Close) { WearCredentialSync.cancel() }
    }
}

/** One page per car. Opens on the car last shown, which is the car the tile shows, so a tile button acts on its own car. */
@Composable
private fun Cars(
    repo: WearSnapshotRepository,
    vehicles: List<com.bloo.bluelink.data.VehicleSnapshot>,
    actions: CarPageActions,
) {
    val (send, tileRequest, onTileRequestHandled) = actions
    val context = LocalContext.current
    val startPage = remember {
        WearTileCar.saved(context)?.let { vin -> vehicles.indexOfFirst { it.vin == vin } }?.coerceAtLeast(0) ?: 0
    }
    // The cars wrap: swiping on from the last car brings up the first, and the other way round.
    val wrap = rememberWearWrap(realCount = vehicles.size, initialRealIndex = startPage)
    val pagerState = wrap.pager
    // The tile follows the car on screen: it shows this car, with this car's actions, as the face does.
    LaunchedEffect(pagerState.currentPage, vehicles) {
        vehicles.getOrNull(wrap.wrap.real(pagerState.currentPage))?.vin?.let { vin ->
            if (WearTileCar.save(context, vin)) WearSurfaces.refresh(context)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            vehicles.getOrNull(wrap.wrap.real(page))?.let { car ->
                WearCarPage(
                    repo = repo,
                    car = car,
                    // A tile action belongs to the car the tile showed, so only the page on screen takes it: the pager
                    // composes its neighbours, and they would run it too.
                    actions = CarPageActions(
                        send = send,
                        tileRequest = tileRequest?.takeIf { page == pagerState.currentPage },
                        onTileRequestHandled = onTileRequestHandled,
                    ),
                )
            }
        }
        if (vehicles.size > 1) PageDots(count = vehicles.size, current = wrap.wrap.real(pagerState.currentPage))
    }
}
