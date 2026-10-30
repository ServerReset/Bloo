package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler

/**
 * What a car's pages need to show and do, in one place: the car, whether the watch has its own sign-in, what is in flight,
 * the last result, and [send] (a command held behind the PIN gate when the wearer's policy asks for one).
 */
@Stable
internal class WearCarUi(
    val repo: WearSnapshotRepository,
    val car: VehicleSnapshot,
    val signedIn: Boolean,
    val refreshing: Boolean,
    val pending: Set<String>,
    val lastResult: WatchCommandResult?,
    val send: (() -> Unit) -> Unit,
) {
    /**
     * Runs [control] for this car through the PIN gate, carrying its climate settings when it has any.
     *
     * Two things are decided NOW, at the press, not when the PIN is answered or the phone gets to it: a toggle becomes the
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

    fun isPending(action: String) = WearCommands.key(car.vin, action) in pending
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
    actions: CarPageActions,
) {
    val (send, tileRequest, onTileRequestHandled) = actions
    val context = LocalContext.current
    val signedIn by WearCredentialSync.signedIn.collectAsStateWithLifecycle()
    val pending by WearCommands.pending.collectAsStateWithLifecycle()
    val lastResult by repo.lastCommandResult.collectAsStateWithLifecycle(initialValue = null)
    val refreshing by repo.refreshing.collectAsStateWithLifecycle(initialValue = false)
    val slotKeys by WearFacePrefs.flow(context).collectAsStateWithLifecycle()
    val ui = WearCarUi(repo, car, signedIn, refreshing, pending, lastResult, send)

    // The command waiting on an "are you sure" (anything that opens the car), the quarter being chosen, and the editor.
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var choosingSlot by remember { mutableStateOf<Int?>(null) }
    var editingFace by remember { mutableStateOf(false) }
    // The climate list, and what a pick from it means: put in a quarter (and, when that quarter was pressed, run too).
    var climateAsk by remember { mutableStateOf<ClimateAsk?>(null) }

    // An action chosen on the tile is one of the face's own controls and runs exactly as a tap on the face would: the PIN
    // gate, the pending state and the haptic are the same -- the tile is a second door into the same room.
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
    // Above the screens that replace the page, so opening one and coming back keeps the page, its hub line and its place.
    val faceStatus = rememberFaceStatus(ui)
    val update = rememberWearUpdateState()
    // The face, then the car's sections, swiped left and right. The page it was left on is kept per car until the app closes.
    val pagerState = rememberPagerState(initialPage = WearSectionMemory.page(car.vin), pageCount = { CarPageCount })
    LaunchedEffect(pagerState.currentPage) { WearSectionMemory.remember(car.vin, pagerState.currentPage) }
    // Back from a section goes to the face (where the car opens), not out of the app.
    val pageScope = rememberCoroutineScope()
    BackHandler(enabled = pagerState.currentPage != 0) {
        pageScope.launch { pagerState.animateScrollToPage(0) }
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
    if (editingFace) {
        WearFaceEditor(car = car, slots = slotKeys, onPickSlot = { choosingSlot = it }, onDone = { editingFace = false })
        return
    }

    // Page 0 is the face, full-bleed so its ring IS the display's edge; the rest are the car's sections, in CarSection order.
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            WearFacePage(
                ui, slotKeys, faceStatus,
                onConfirm = { confirmAction = it },
                onChooseClimate = { climateAsk = ClimateAsk(it, run = true) },
                onEditFace = { editingFace = true },
            )
        } else {
            WearCarSection(ui, update, CarSection.entries[page - 1])
        }
    }
}

/** A car's sections after its face, in the order a left swipe reaches them. */
internal enum class CarSection { CLIMATE, STATUS, CARE, SETTINGS }

/** The face plus every section. */
private val CarPageCount get() = CarSection.entries.size + 1

/**
 * The page each car was last left on, for as long as the watch app runs. A process-wide memory rather than the page's own state, so it
 * survives moving to another car and back, and a swipe to a section is kept when the wearer goes away and returns.
 */
internal object WearSectionMemory {
    private val pages = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun page(vin: String): Int = pages[vin] ?: 0

    fun remember(vin: String, page: Int) {
        pages[vin] = page
    }
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
