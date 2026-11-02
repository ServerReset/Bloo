package com.bloo.bluelink.wear
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment

import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.uicommon.lowPowerCalm
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import kotlin.math.abs

/**
 * What a car's pages need to show and do, in one place: the car, whether the watch has its own sign-in, what is in flight,
 * the last result, and [send] (runs a command as the wearer taps it).
 */
@Stable
internal class WearCarUi(
    val repo: WearSnapshotRepository,
    val car: VehicleSnapshot,
    /** Every car on the account, for the map. */
    val cars: List<VehicleSnapshot>,
    val signedIn: Boolean,
    val refreshing: Boolean,
    val pending: Set<String>,
    val lastResult: WatchCommandResult?,
    val send: (() -> Unit) -> Unit,
    /** The phone just refused a command as still pending: every command is held off until the hold lapses. */
    val busy: Boolean = false,
) {
    /**
     * Runs [control] for this car, carrying its climate settings when it has any.
     *
     * Two things are decided NOW, at the press, not when the phone gets to it: a toggle becomes the
     * direction the wearer was shown ([resolved]) -- so a stale screen can never turn "Lock" into an unlock the confirm never
     * covered -- and a preset's settings are read, so a preset deleted meanwhile cannot start climate on defaults.
     */
    fun run(control: FaceControl) {
        val action = resolved(control.action)
        val request = control.request?.invoke(car)
        send { repo.command(car.vin, action, request) }
    }

    /** [action], with a lock, climate or charge TOGGLE turned into the lock/unlock, on/off the car's state on screen calls for. */
    fun resolved(action: String): String =
        if (action in TOGGLES) CarCommandRunner.resolveToggle(car, action) else action

    fun isPending(action: String) = busy || WearCommands.key(car.vin, action) in pending
}

/**
 * One car: its face and, a swipe up, its details; over them, in turn, the face editor and the control list it opens. A
 * command that opens the car (Unlock) asks once more first. Back or a swipe right peels one layer off.
 */
/**
 * What the app hands a car page: the gate its commands go through, and a request from the watch's Tile to act on, and the call that
 * says the request was handled. Passed as one value, since every page takes all three.
 */
internal data class CarPageActions(
    val send: (() -> Unit) -> Unit,
    val tileRequest: WearMainActivity.TileRequest?,
    val onTileRequestHandled: () -> Unit,
)

@Composable
internal fun WearCarPage(
    repo: WearSnapshotRepository,
    car: VehicleSnapshot,
    cars: List<VehicleSnapshot>,
    /** Whether this is the car on screen: only it moves the shared page, and the others follow it. */
    current: Boolean,
    actions: CarPageActions,
) {
    val (send, tileRequest, onTileRequestHandled) = actions
    val context = LocalContext.current
    val signedIn by WearCredentialSync.signedIn.collectAsStateWithLifecycle()
    val pending by WearCommands.pending.collectAsStateWithLifecycle()
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    // The phone turned a command away because the car still had another request in hand: its buttons are held off for a while, so the
    // wearer is not sent into the same refusal by tapping again.
    val busy by produceState(false, lastResult, car.vin) {
        val r = lastResult
        val refused = r != null && r.vin == car.vin && !r.ok && r.message?.contains("previous request is pending", ignoreCase = true) == true
        value = refused
        if (refused) {
            delay(BusyHoldMs)
            value = false
        }
    }
    val refreshing by repo.refreshing.collectAsStateWithLifecycle(initialValue = false)
    val slotKeys by WearFacePrefs.flow(context).collectAsStateWithLifecycle()
    val ui = WearCarUi(repo, car, cars, signedIn, refreshing, pending, lastResult, send, busy)

    // The command waiting on an "are you sure" (anything that opens the car), the quarter being chosen, and the editor.
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var choosingSlot by remember { mutableStateOf<Int?>(null) }
    var editingFace by remember { mutableStateOf(false) }
    // The climate list, and what a pick from it means: put in a quarter (and, when that quarter was pressed, run too).
    var climateAsk by remember { mutableStateOf<ClimateAsk?>(null) }

    // An action chosen on the tile is one of the face's own controls and runs exactly as a tap on the face would: the
    // pending state and the haptic are the same -- the tile is a second door into the same room.
    LaunchedEffect(tileRequest) {
        val key = tileRequest?.key ?: return@LaunchedEffect
        WearFaceControls.byKey(key)?.takeIf { it.available(car) }?.let { control ->
            when {
                // A climate quarter whose preset has gone: ask which to use (for that quarter), as a press on the face does.
                control.needsChoice(car) -> climateAsk = ClimateAsk(tileRequest.slot.takeIf { it in 0..3 } ?: slotKeys.indexOf(key), run = true)
                control.confirm(car) -> confirmAction = control.action
                else -> ui.run(control)
            }
        }
        onTileRequestHandled()
    }
    ResultHaptics(lastResult, car.vin)
    // The outcome of a command, in a toast over the bottom third for a few seconds: the same words the face's hub uses.
    val toastBaseline = remember { lastResult }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lastResult) {
        val r = lastResult ?: return@LaunchedEffect
        if (r === toastBaseline || r.vin != car.vin) return@LaunchedEffect
        toast = if (r.ok) "Done" else (r.message ?: "Didn't go through")
        delay(ToastMs)
        toast = null
    }
    // Above the screens that replace the page, so opening one and coming back keeps the page, its hub line and its place.
    val faceStatus = rememberFaceStatus(ui)
    val update = rememberWearUpdateState()
    // The face, then the car's sections, swiped up and down; the last section swipes on to the face. The page is one memory for every car,
    // so a swipe left or right to another car keeps the wearer on the same page of it.
    val wrap = rememberWearWrap(realCount = CarPageCount, initialRealIndex = WearPageMemory.page)
    // A pull down on the face refreshes the car once it is pulled past the threshold (see WearPullRefresh).
    val latestUi by rememberUpdatedState(ui)
    val editingNow by rememberUpdatedState(editingFace)
    val pullThreshold = with(LocalDensity.current) { PullRefreshDistance.toPx() }
    val pullScope = rememberCoroutineScope()
    val pull = remember(pullThreshold) {
        WearPullRefresh(
            scope = pullScope,
            thresholdPx = pullThreshold,
            // "At rest" tolerates the sliver a settle can leave, so the face still takes the pull just after a wrap from the last section.
            canPull = {
                wrap.wrap.real(wrap.pager.currentPage) == 0 && abs(wrap.pager.currentPageOffsetFraction) < RestFraction && !editingNow
            },
            onRefresh = { if (latestUi.signedIn) latestUi.repo.refresh() },
        )
    }
    val pagerState = wrap.pager
    val page = wrap.wrap.real(pagerState.currentPage)
    LaunchedEffect(page, current) { if (current) WearPageMemory.page = page }
    // The other cars follow the shared page, so they are on it when a swipe brings them up.
    LaunchedEffect(WearPageMemory.page) {
        val step = WearPageMemory.page - page
        if (step != 0 && !current) pagerState.scrollToPage(pagerState.currentPage + wrapStep(step))
    }
    // Back from a section goes to the face (where the car opens), not out of the app.
    val pageScope = rememberCoroutineScope()
    BackHandler(enabled = current && page != 0) {
        pageScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - page) }
    }

    confirmAction?.let { action ->
        WearConfirmScreen(
            icon = AppIcons.LockOpen,
            title = "Unlock ${car.name}?",
            detail = "The doors open for anyone nearby.",
            confirmLabel = "Unlock",
            destructive = true,
            onConfirm = { confirmAction = null; val resolved = ui.resolved(action); send { repo.command(car.vin, resolved) } },
            onCancel = { confirmAction = null },
        )
        return
    }
    climateAsk?.let { ask ->
        WearClimateList(
            car = car,
            title = "Which climate?",
            onPick = { key ->
                climateAsk = null
                if (ask.slot in slotKeys.indices) {
                    // The quarter now holds this one (kept with the tile, the phone and Drive); a press runs it right away.
                    WearFacePrefs.save(context, slotKeys.toMutableList().also { it[ask.slot] = key })
                    WearSurfaces.refresh(context)
                    WearLook.sendToPhone(context)
                    choosingSlot = null
                }
                if (ask.run) WearFaceControls.byKey(key)?.let(ui::run)
            },
            onBack = { climateAsk = null },
        )
        return
    }
    choosingSlot?.let { slot ->
        WearControlPicker(
            position = WearFacePrefs.positions[slot],
            current = slotKeys[slot],
            car = car,
            onPick = { key ->
                if (key == WearFaceControls.CLIMATE_PICK) {
                    // "Climate preset…": the quarter holds whichever preset (or smart climate) is picked from the list.
                    climateAsk = ClimateAsk(slot, run = false)
                } else {
                    WearFacePrefs.save(context, slotKeys.toMutableList().also { it[slot] = key })
                    // The tile shows the same controls, and the phone (and Drive) keeps them.
                    WearSurfaces.refresh(context)
                    WearLook.sendToPhone(context)
                    choosingSlot = null
                }
            },
            onBack = { choosingSlot = null },
        )
        return
    }
    // Holding the face zooms it out to two thirds, and the quarters' editor zooms in from the same size, so changing the quarters reads as
    // the face opening up rather than a new screen. The pager is taken out once it has faded, so it is not drawn or touched behind the editor.
    val editT by animateFloatAsState(
        targetValue = if (editingFace) 1f else 0f,
        animationSpec = lowPowerCalm<Float>(Spring.StiffnessMediumLow),
        label = "faceEdit",
    )
    Box(Modifier.fillMaxSize()) {
        if (editT > 0.001f) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val s = FaceEditFrom + (1f - FaceEditFrom) * editT
                scaleX = s
                scaleY = s
                alpha = editT
            }) {
                WearFaceEditor(car = car, slots = slotKeys, onPickSlot = { choosingSlot = it }, onDone = { editingFace = false })
            }
        }
        if (!editingFace || editT < 0.999f) {
            // Page 0 is the face, full-bleed so its ring IS the display's edge; the rest are the car's sections, in CarSection order.
            Box(Modifier.fillMaxSize().graphicsLayer {
                val s = 1f - (1f - FaceEditFrom) * editT
                scaleX = s
                scaleY = s
                alpha = 1f - editT
            }) {
                // The pull-to-refresh is taken from the pager's own drags on every page (see WearPullRefresh). The pages stretch under it,
                // gently; the refresh disc sits outside that stretch, so it stays pulled down for as long as the refresh runs.
                Box(
                    Modifier.fillMaxSize()
                        .nestedScroll(pull.connection)
                        .graphicsLayer {
                            // A refresh holds the page still: only a pull under the finger takes a little squash and stretch.
                            val p = if (ui.refreshing) 0f else pull.progress().coerceIn(0f, 1f)
                            translationY = if (ui.refreshing) 0f else pull.offsetPx()
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            scaleY = 1f + 0.02f * p
                            scaleX = 1f - 0.01f * p
                        },
                ) {
                    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { virtual ->
                        val real = wrap.wrap.real(virtual)
                        // Each page carries the car's name at the bottom, so the name travels with the pages as they swipe.
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalFaceUnit provides minOf(maxWidth, maxHeight) / ReferenceFace) {
                                if (real == 0) {
                                    WearFacePage(
                                        ui, slotKeys, faceStatus,
                                        onConfirm = { confirmAction = it },
                                        onChooseClimate = { climateAsk = ClimateAsk(it, run = true) },
                                        onEditFace = { editingFace = true },
                                    )
                                } else {
                                    WearCarSection(ui, update, CarSection.entries[real - 1])
                                }
                            }
                        }
                    }
                }
                WearPullDisc(pull, refreshing = ui.refreshing)
                if (current) {
                    // The car's name floats over every page of the car on screen: one element, not one per page.
                    CurvedName(car.name, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxSize())
                }
                toast?.let { WearToast(it) }
            }
        }
    }
}

/** How long a refused command holds the car's buttons off, and how long a result toast stays up. */
private const val BusyHoldMs = 12_000L
private const val ToastMs = 3_000L

/**
 * A toast over the bottom third of the round display: the outcome of a command, on a translucent dark panel so the words read over the
 * page behind (the watch has no backdrop blur to draw with).
 */
@Composable
private fun WearToast(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .padding(bottom = 22.dp)
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            WearCenteredText(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** How far the face must be pulled down to refresh the car. */
private val PullRefreshDistance = 56.dp

/** How close to a whole page the pager must be for the face to count as at rest: a settle can leave a sliver short of exact. */
private const val RestFraction = 0.001f

/** How small the face gets when its quarters are opened: the editor starts from this size and the face shrinks to it. */
private const val FaceEditFrom = 0.6f

/** The smallest step, forwards or back, that moves a wrapping pager of [CarPageCount] pages by [step]. */
private fun wrapStep(step: Int): Int {
    val n = CarPageCount
    val forwards = ((step % n) + n) % n
    return if (forwards > n / 2) forwards - n else forwards
}

/** A car's sections after its face, in the order a left swipe reaches them. */
internal enum class CarSection { CLIMATE, STATUS, CARE, SETTINGS }

/** The face plus every section. */
private val CarPageCount get() = CarSection.entries.size + 1

/**
 * The page the wearer is on, shared by every car for as long as the watch app runs: the face, or one of the car's sections. The car on
 * screen sets it, and the others follow, so a swipe left or right keeps the wearer on the same page of the next car.
 */
internal object WearPageMemory {
    var page by mutableIntStateOf(0)
}

private val TOGGLES = setOf(CarAction.TOGGLE_LOCK, CarAction.TOGGLE_CLIMATE, CarAction.TOGGLE_CHARGE)

/** Why the climate list is open: to pick a preset for face quarter [slot] (and run it too, when that quarter was pressed). */
private class ClimateAsk(val slot: Int, val run: Boolean)

/** A buzz when a command comes back (a confirm for success, a reject for a failure): a wrist is often felt, not looked at. */
@Composable
private fun ResultHaptics(result: WatchCommandResult?, vin: String) {
    val haptics = LocalHapticFeedback.current
    // The result already there when this page appeared belongs to an earlier moment: only a NEW one buzzes.
    val baseline = remember { result }
    LaunchedEffect(result) {
        if (result === baseline) return@LaunchedEffect
        result?.takeIf { it.vin == vin }?.let {
            haptics.performHapticFeedback(if (it.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        }
    }
}

/**
 * The hub's moment line: "Sending…" while a command is out, its result for a few seconds after, else a warning when the
 * reading is old -- the face says what is happening without a toast.
 */
@Composable
private fun rememberFaceStatus(ui: WearCarUi): FaceStatus? {
    val vin = ui.car.vin
    var recent by remember { mutableStateOf<WatchCommandResult?>(null) }
    val baseline = remember { ui.lastResult }
    LaunchedEffect(ui.lastResult) {
        if (ui.lastResult === baseline) return@LaunchedEffect
        val r = ui.lastResult?.takeIf { it.vin == vin } ?: return@LaunchedEffect
        recent = r
        delay(FaceResultMs)
        if (recent == r) recent = null
    }
    val shown = recent
    return when {
        ui.pending.any { it.startsWith("$vin:") } -> FaceStatus("Sending…")
        shown != null -> if (shown.ok) FaceStatus("Done") else FaceStatus("Didn't go through", error = true)
        ui.car.isStale() -> ui.car.updatedLabel()?.let { FaceStatus("Updated $it", error = true) }
        else -> null
    }
}

/** How long a command's result stays in the face's hub. */
private const val FaceResultMs = 4_000L
