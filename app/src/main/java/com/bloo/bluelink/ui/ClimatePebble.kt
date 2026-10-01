@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Climate controls: ClimatePebble, SeatControl, seatTint, preset section,
 * PresetPill, ChargeLimitPill -- extracted from Pebbles.kt.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.smartClimateIsCooling
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.deleteClimatePreset
import com.bloo.bluelink.data.saveClimatePreset
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.TempValue


// --- Climate --------------------------------------------------------------

/**
 * The climate control pebble -- by far the most stateful pebble in the app.
 * Local editable state (temp, duration, defrost, steering-wheel heat, and
 * all four seat levels) is `remember(v.vin)`-keyed so switching cars resets
 * to that car's own values rather than carrying over the previous car's.
 *
 * Three things keep this state in sync with the outside world:
 *  1. On first composition per car, `vm.loadSavedClimate` restores whatever
 *     was last saved for this car (`settingsLoaded` gates the debounced
 *     save below so it doesn't immediately re-save the values it just
 *     loaded).
 *  2. `remoteClimate` (from `state.climateSync`) mirrors whatever another
 *     LIVE composition of this same car's climate pebble just set -- the dual-
 *     column hotspot can pin "controls" to a secondary slot while the full
 *     pebble list still renders it too, so the same car's climate can be on
 *     screen twice at once; a [LaunchedEffect] keyed on it snaps all the
 *     local state to match whenever either instance changes it.
 *  3. A single debounced [LaunchedEffect] keyed on `(currentReq,
 *     activePresetId)` persists + publishes the current settings back out
 *     (to storage, and to `state.climateSync` for the other instance above)
 *     after they stop changing -- the actual 400ms debounce lives in the
 *     ViewModel's own coroutine scope rather than in this effect, specifically
 *     so a car-switch or pebble collapse
 *     that removes this composable from the tree within that window can't
 *     silently cancel and drop the pending save.
 *
 * `activePresetId` tracks which saved preset (if any) matches the live
 * settings exactly; it's cleared automatically the moment any control
 * drifts away from that preset's exact values, so the "active" highlight
 * only ever marks a true match, never a stale one.
 *
 * The header's Start/Stop button is context-sensitive: while climate is
 * already on it stops it; while the pebble is expanded (sliders visible) it
 * starts with exactly what's shown; while collapsed in Simple mode it
 * computes a "smart" one-tap target temperature from the current weather
 * instead of making the user open the pebble first.
 */
@Composable
internal fun ClimatePebble(
    v: Vehicle,
    status: VehicleStatus?,
    seats: SeatConfig,
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier,
) {
    val pending = state.isPending(v.vin, "climate")
    val fahrenheit = LocalAppearance.current.useFahrenheit
    var tempF by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_TEMP_F) }
    var duration by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_DURATION_MIN) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(WheelHeatLevel.OFF) }
    var driver by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var passenger by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearLeft by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearRight by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var settingsLoaded by remember(v.vin) { mutableStateOf(false) }

    // Copy a ClimateRequest's nine fields into the sliders' state. Defined up here so the
    // restore effect just below and the preset-apply buttons further down share ONE copy of the
    // assignment -- it was written out twice, byte-for-byte, and "restore last-used" and "apply
    // preset" are the same operation (set the sliders from a request). Captures only the nine
    // `var` setters above it. NOT reused by the cross-composition sync effect below, which maps
    // through SeatLevel.fromApi and so is genuinely different.
    val applyRequest: (ClimateRequest) -> Unit = { r ->
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steeringWheelHeat
        driver = r.seatFrontLeft
        passenger = r.seatFrontRight
        rearLeft = r.seatRearLeft
        rearRight = r.seatRearRight
    }

    // Restore the car's last-used climate settings the first time the pebble shows.
    LaunchedEffect(v.vin) {
        vm.loadSavedClimate(v)?.let(applyRequest)
        settingsLoaded = true
    }

    val currentReq = ClimateRequest(
        tempF = tempF,
        defrost = defrost,
        durationMinutes = duration,
        steeringWheelHeat = steeringHeat,
        seatFrontLeft = driver,
        seatFrontRight = passenger,
        seatRearLeft = rearLeft,
        seatRearRight = rearRight,
    )
    // Persist + cross-composition mirror is handled by ONE debounced call further
    // down (after activePresetId exists) - see the LaunchedEffect near the
    // climate sync block.

    val presets = state.climatePresets[v.vin].orEmpty()
    var showAddPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    // Helper to warn if the car engine is on before changing AC/climate settings
    val startClimateWithEngineCheck: (ClimateRequest) -> Unit = { req ->
        if (status?.engine == true) {
            vm.reportInfo("Climate changes may be rejected while the car is running")
        }
        vm.startClimate(v, req)
    }
    // Which preset (if any) is currently applied: set when you start one, and
    // cleared automatically once the live settings drift away from it (e.g. you
    // nudge a slider) so the highlight only marks a true match.
    var activePresetId by remember(v.vin) { mutableStateOf<String?>(null) }
    // applyPreset was here; it was the same body as applyRequest (defined above, next to the
    // sliders' state). The preset buttons below call applyRequest directly now.
    LaunchedEffect(currentReq, activePresetId, presets) {
        val active = presets.firstOrNull { it.id == activePresetId }
        if (active != null && active.request != currentReq) activePresetId = null
    }

    // --- Cross-composition climate sync ---------------------------------------
    // Reflect whatever another live composition of this same car's climate
    // pebble just set: sliders + active preset.
    val remoteClimate = state.climateSync[v.vin]
    LaunchedEffect(remoteClimate) {
        val r = remoteClimate ?: return@LaunchedEffect
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = WheelHeatLevel.fromApi(r.steering)
        driver = SeatLevel.fromApi(r.seatFrontLeft)
        passenger = SeatLevel.fromApi(r.seatFrontRight)
        rearLeft = SeatLevel.fromApi(r.seatRearLeft)
        rearRight = SeatLevel.fromApi(r.seatRearRight)
        activePresetId = r.activePresetId
    }
    // Persist + cross-composition publish once settings stop changing, not on every
    // drag tick: publishClimateState updates the shared ViewModel StateFlow the whole
    // screen collects, so per-tick commits recomposed far more than the slider
    // being dragged (read as "the sliders don't react until long after you
    // change them"). The 400ms debounce lives in the ViewModel (viewModelScope),
    // NOT here: an effect-side delay was cancelled whenever this pebble left
    // composition within 400ms of the last adjustment (cover-screen tile swipe,
    // car switch, collapse), silently reverting the user's change.
    LaunchedEffect(currentReq, activePresetId) {
        if (settingsLoaded) vm.saveClimateDebounced(v, currentReq, activePresetId)
    }

    val climateOn = status?.airCtrlOn == true
    // The car rejects remote climate commands while it's moving, so the whole
    // control goes read-only when driving - and if it's already on, we show
    // what it's currently set to at the car instead of editable inputs.
    val driving = state.isDriving(v)
    val startClimate = { vm.startClimate(v, currentReq) }
    val weather = state.carWeather[v.vin] ?: state.homeWeather
    val simpleMode = state.settingsMode != "advanced"
    // Whether the pebble's own body (the live sliders below) is actually on
    // screen right now -- mirrors Pebble()'s own expanded computation exactly
    // so this and the header's Start button agree on what "expanded" means.
    val expanded = LocalForceExpanded.current || state.isPebbleExpanded(v.vin, "climate")

    // While climate runs the controls are locked, so they should show what the CAR is doing, not
    // whatever the sliders last held. Only the fields the car reports back are mirrored.
    val carTempF = status?.airTemp?.asFahrenheit()
    val carDefrost = status?.defrost
    val carWheelHeat = status?.steerWheelHeat
    val syncFromCar: () -> Unit = {
        carTempF?.let { tempF = it.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last) }
        carDefrost?.let { defrost = it }
        carWheelHeat?.let { steeringHeat = WheelHeatLevel.fromApi(it) }
    }
    LaunchedEffect(climateOn, carTempF, carDefrost, carWheelHeat) { if (climateOn) syncFromCar() }
    // Collapsing throws away edits that were never sent: re-opening shows the car's real settings
    // while it runs, or the last climate actually started while it doesn't.
    LaunchedEffect(expanded) {
        if (!expanded && settingsLoaded) {
            if (climateOn) syncFromCar() else vm.loadSavedClimate(v)?.let(applyRequest)
        }
    }

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm, modifier,
        summary = when {
            climateOn && driving -> "On · driving"
            climateOn -> "On"
            else -> "Off"
        },
        headerAction = PebbleHeaderAction(
            label = when {
                climateOn && driving -> "On"
                climateOn -> "Stop"
                else -> "Start"
            },
            icon = Icons.Filled.AcUnit,
            onClick = {
                if (climateOn) {
                    vm.stopClimate(v); activePresetId = null
                } else if (expanded) {
                    // The sliders are visible and live-editable right here --
                    // Start should do exactly what they're currently set to,
                    // not second-guess with the smart/preset logic meant for
                    // the collapsed one-tap case below.
                    startClimateWithEngineCheck(currentReq)
                } else if (simpleMode && weather != null) {
                    val ambientF = ambientFahrenheit(weather.tempC)
                    val smartTarget = smartClimateTargetF(ambientF)
                    tempF = smartTarget; defrost = false; activePresetId = null
                    startClimateWithEngineCheck(currentReq.copy(tempF = smartTarget, defrost = false))
                } else {
                    val defaultId = state.defaultClimatePresets[v.vin]
                    val matchingPreset = defaultId?.let { id -> presets.firstOrNull { it.id == id } }
                    if (matchingPreset != null) {
                        applyRequest(matchingPreset.request)
                        startClimateWithEngineCheck(matchingPreset.request)
                        activePresetId = matchingPreset.id
                    } else if (weather != null) {
                        val ambientF = ambientFahrenheit(weather.tempC)
                        val smartTarget = smartClimateTargetF(ambientF)
                        tempF = smartTarget; defrost = false; activePresetId = null
                        startClimateWithEngineCheck(currentReq.copy(tempF = smartTarget, defrost = false))
                    } else startClimateWithEngineCheck(currentReq)
                }
            },
            enabled = !driving,
            pending = pending,
            active = climateOn,
            spinning = climateOn,
        ),
    ) {
        // No cover hero here any more: this pebble's summary ("On · driving" / "On" / "Off")
        // is the identical expression, and CoverTile now renders it as the tile's headline.
        // Two lines saying "On" ten dp apart was the duplication, not the glance.
        if (driving) {
            if (climateOn) {
                Text(
                    "On at the car. Read-only while driving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                )
                status?.airTemp?.let { t ->
                    t.value?.let { StatusRow("Set to", degLabel(it, fahrenheit, t.unit)) }
                }
                status?.defrost?.let { StatusRow("Defrost", if (it) "On" else "Off") }
                status?.steerWheelHeat?.let { StatusRow("Steering wheel heat", onOff(it)) }
                status?.seatHeaterVentState?.let { s ->
                    s.flSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Driver seat", onOff(it)) }
                    s.frSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Passenger seat", onOff(it)) }
                }
            } else {
                Text(
                    "Climate can't be started while the car is driving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                )
            }
            return@Pebble
        }

        ClimatePresetSection(
            presets = presets,
            activeId = activePresetId,
            fahrenheit = fahrenheit,
            onStart = { preset ->
                // Tapping the running preset turns climate back off.
                if (activePresetId == preset.id && climateOn) {
                    vm.stopClimate(v)
                    activePresetId = null
                } else {
                    applyRequest(preset.request)
                    startClimateWithEngineCheck(preset.request)
                    activePresetId = preset.id
                }
            },
            onDelete = { id ->
                if (activePresetId == id) activePresetId = null
                vm.deleteClimatePreset(v, id)
            },
            onReorder = { vm.reorderClimatePresets(v, it) },
        )

        // Smart climate: read the weather where the car is (falling back to home)
        // and pick a target -- see smartClimateTargetF, the same rule the tile
        // command runner uses: ~10°F off ambient normally, or the car's most
        // aggressive setting on a genuinely extreme day, always within what the
        // car's own climate range actually accepts.
        // Its own PopVisible: weather can arrive AFTER the pebble is already open (it's
        // a separate fetch), so this section pops in live rather than only ever being
        // present from the first frame.
        PopVisible(visible = weather != null) {
            val w = weather
            if (w != null) {
                val ambientF = ambientFahrenheit(w.tempC)
                val smartTarget = smartClimateTargetF(ambientF)
                val targetLabel = degLabel(smartTarget.toString(), fahrenheit)
                val ambientLabel = degLabel(ambientF.toString(), fahrenheit)
                val smartLabel = if (smartClimateIsCooling(ambientF)) "Cool to $targetLabel" else "Heat to $targetLabel"
                Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                    SectionLabel("Smart climate")
                    MorphActionButton(
                        label = smartLabel,
                        icon = Icons.Filled.AcUnit,
                        onClick = {
                            tempF = smartTarget
                            defrost = false
                            activePresetId = null
                            startClimateWithEngineCheck(currentReq.copy(tempF = smartTarget, defrost = false))
                        },
                        enabled = !pending && !climateOn,
                    )
                    Text(
                        "It's $ambientLabel by your car. Smart climate targets $targetLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                    )
                }
            }
        }

        val isGen5W = state.isGen5WEffective(v)
        ClimateControlsSection(
            locked = climateOn,
            fahrenheit = fahrenheit,
            tempF = tempF, onTempF = { tempF = it },
            duration = duration, onDuration = { duration = it },
            defrost = defrost, onDefrost = { defrost = it },
            steeringHeat = steeringHeat, onSteeringHeat = { steeringHeat = it },
            seats = seats,
            // state.powertrainOf, not the raw v.isEv: a user's powertrain override is honoured here too.
            showSeats = seats.any && !(isGen5W && state.powertrainOf(v) == com.bloo.bluelink.data.Powertrain.EV),
            driver = driver, onDriver = { driver = it },
            passenger = passenger, onPassenger = { passenger = it },
            rearLeft = rearLeft, onRearLeft = { rearLeft = it },
            rearRight = rearRight, onRearRight = { rearRight = it },
            onSaveAsPreset = { presetName = ""; showAddPreset = true },
        )

        if (showAddPreset) {
            // Standardized on the shared GlassAlertDialog shell (stacked buttons).
            GlassAlertDialog(
                onDismissRequest = { showAddPreset = false },
                icon = Icons.Filled.Thermostat,
                title = "Save preset",
                text = {
                    BlooTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                buttons = {
                    MorphTextButton(
                        "Save",
                        onClick = {
                            if (presetName.isNotBlank()) {
                                vm.saveClimatePreset(v, presetName.trim(), currentReq)
                                showAddPreset = false
                            }
                        },
                        enabled = presetName.isNotBlank(),
                        emphasis = ButtonEmphasis.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SafeMorphTextButton(
                        "Cancel",
                        onClick = { showAddPreset = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

/** A reported setpoint as whole °F: the API's own unit code says whether it is Celsius (0) or °F (1). */
private fun TempValue.asFahrenheit(): Int? {
    val n = value?.toDoubleOrNull() ?: return null
    return (if (unit == 0) n * 9 / 5 + 32 else n).roundToInt()
}
