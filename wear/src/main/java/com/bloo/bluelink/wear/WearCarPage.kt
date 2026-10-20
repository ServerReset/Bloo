package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import kotlinx.coroutines.delay

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
    /** Runs [control] for this car through the PIN gate, carrying its climate settings when it has any. */
    fun run(control: FaceControl) = send { repo.command(car.vin, control.action, control.request?.invoke(car)) }

    fun isPending(action: String) = WearCommands.key(car.vin, action) in pending
}

/**
 * One car: its face and, a swipe up, its details; over them, in turn, the face editor and the control list it opens. A
 * command that opens the car (Unlock) asks once more first. Back or a swipe right peels one layer off.
 */
@Composable
internal fun WearCarPage(
    repo: WearSnapshotRepository,
    car: VehicleSnapshot,
    send: (() -> Unit) -> Unit,
    tileRequest: WearMainActivity.TileRequest?,
    onTileRequestHandled: () -> Unit,
) {
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
    // The climate list, and what a pick from it means: put in a quarter (and, when it was pressed, run), or just run.
    var climateAsk by remember { mutableStateOf<ClimateAsk?>(null) }

    // An action chosen on the tile is one of the face's own controls and runs exactly as a tap on the face would: the PIN
    // gate, the pending state and the haptic are the same -- the tile is a second door into the same room.
    LaunchedEffect(tileRequest) {
        val key = tileRequest?.key ?: return@LaunchedEffect
        WearFaceControls.byKey(key)?.takeIf { it.available(car) }?.let { control ->
            when {
                // A climate quarter whose preset has gone: ask which to use (for that quarter), as a press on the face does.
                control.needsChoice(car) -> climateAsk = ClimateAsk.Assign(slotKeys.indexOf(key), run = true)
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
    val pagerState = rememberPagerState(pageCount = { 2 })

    confirmAction?.let { action ->
        WearConfirmScreen(
            icon = Icons.Filled.LockOpen,
            title = "Unlock ${car.name}?",
            detail = "The doors open for anyone nearby.",
            confirmLabel = "Unlock",
            destructive = true,
            onConfirm = { confirmAction = null; send { repo.command(car.vin, action) } },
            onCancel = { confirmAction = null },
        )
        return
    }
    climateAsk?.let { ask ->
        WearClimateList(
            car = car,
            title = if (ask is ClimateAsk.Assign) "Which climate?" else "Climate presets",
            onPick = { key ->
                climateAsk = null
                if (ask is ClimateAsk.Assign && ask.slot in slotKeys.indices) {
                    // The quarter now holds this one (kept with the tile, the phone and Drive); a press runs it right away.
                    WearFacePrefs.save(context, slotKeys.toMutableList().also { it[ask.slot] = key })
                    WearSurfaces.refresh(context)
                    WearLook.sendToPhone(context)
                    choosingSlot = null
                }
                if (ask !is ClimateAsk.Assign || ask.run) WearFaceControls.byKey(key)?.let(ui::run)
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
                    climateAsk = ClimateAsk.Assign(slot, run = false)
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

    // Page 0 is the face, full-bleed so its ring IS the display's edge; page 1 is the car's details.
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            WearFacePage(
                ui, slotKeys, faceStatus,
                onConfirm = { confirmAction = it },
                onChooseClimate = { climateAsk = ClimateAsk.Assign(it, run = true) },
                onEditFace = { editingFace = true },
            )
        } else {
            WearDetailsPage(ui, update, onEditFace = { editingFace = true }, onClimateList = { climateAsk = ClimateAsk.Run })
        }
    }
}

/** Why the climate list is open: to pick a preset for a face quarter (and run it too, when the quarter was pressed), or to start one. */
private sealed interface ClimateAsk {
    class Assign(val slot: Int, val run: Boolean) : ClimateAsk
    data object Run : ClimateAsk
}

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
