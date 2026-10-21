package com.bloo.bluelink.ui

/** The Climate pebble: [ClimatePebble]. */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.TempValue
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.deleteClimatePreset
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.saveClimatePreset
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.smartClimateLabel
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.smartClimateTargetLine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.denyTone
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.container
import com.bloo.uicommon.content

// --- Climate --------------------------------------------------------------

/**
 * The climate control pebble. Editable state (temp, duration, defrost, wheel heat, seats) is keyed
 * on `v.vin` so switching cars resets it.
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

    // Copies a ClimateRequest into the slider state; shared by restore and preset-apply. The
    // cross-composition sync below maps through SeatLevel.fromApi, so it doesn't reuse this.
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

    val presets = state.climatePresets[v.vin].orEmpty()
    var showAddPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    // Warn if the engine is on before changing climate settings.
    // What was last sent and when: the car's own reading of its set temperature lags a command by a refresh, and
    // mirroring that stale reading straight back put the slider on whatever the car last reported (72) the moment a
    // start went out. Until the car reports what was sent (or a minute passes), the slider keeps what was sent.
    var lastSent by remember(v.vin) { mutableStateOf<Pair<ClimateRequest, Long>?>(null) }
    val startClimateWithEngineCheck: (ClimateRequest) -> Unit = { req ->
        if (status?.engine == true) {
            vm.reportInfo("Climate changes may be rejected while the car is running")
        }
        lastSent = req to System.currentTimeMillis()
        vm.startClimate(v, req)
    }
    // Which preset (if any) is currently applied: set when you start one, and cleared automatically
    // once the live settings drift away from it (e.g. you nudge a slider) so the highlight only
    // marks a true match.
    var activePresetId by remember(v.vin) { mutableStateOf<String?>(null) }
    // Start at [target] degrees F with defrost off, clearing any highlighted preset: the one-tap
    // "smart" start.
    val startAtTarget: (Int, ClimateRequest) -> Unit = { target, base ->
        tempF = target; defrost = false; activePresetId = null
        startClimateWithEngineCheck(base.copy(tempF = target, defrost = false))
    }
    LaunchedEffect(currentReq, activePresetId, presets) {
        val active = presets.firstOrNull { it.id == activePresetId }
        if (active != null && active.request != currentReq) activePresetId = null
    }

    // --- Cross-composition climate sync ---------------------------------------
    // Reflect another live composition of this car's pebble: sliders + active preset.
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
    // Persist + publish once settings stop changing, not per drag tick (which recomposed the whole
    // screen).
    LaunchedEffect(currentReq, activePresetId) {
        if (settingsLoaded) vm.saveClimateDebounced(v, currentReq, activePresetId)
    }

    val climateOn = status?.airCtrlOn == true
    // The car rejects remote climate commands while it is running (moving, or its engine on), so the
    // whole control goes read-only then: it cannot be started, and a climate already running cannot
    // be stopped remotely either. If it IS on, we show what the car is currently set to at the car
    // instead of editable inputs.
    val running = state.isRunning(v)
    val weather = state.carWeather[v.vin] ?: state.homeWeather
    val simpleMode = state.settingsMode != "advanced"
    // Mirrors Pebble()'s own expanded computation so this and the header Start button agree.
    val expanded = LocalForceExpanded.current || state.isPebbleExpanded(v.vin, "climate")

    // While climate runs the controls are locked, so they should show what the CAR is doing, not
    // whatever the sliders last held. Only the fields the car reports back are mirrored.
    val carTempF = status?.airTemp?.asFahrenheit()
    val carDefrost = status?.defrost
    val carWheelHeat = status?.steerWheelHeat
    val syncFromCar: () -> Unit = {
        val sent = lastSent?.takeIf { System.currentTimeMillis() - it.second < CarCatchUpMs }?.first
        val carCaughtUp = sent == null || (
            (carTempF == null || carTempF == sent.tempF) &&
                (carDefrost == null || carDefrost == sent.defrost) &&
                (carWheelHeat == null || carWheelHeat == sent.steeringWheelHeat.apiValue)
            )
        // The car still shows its OLD settings after a start: keep what was sent until it reports them.
        if (carCaughtUp) {
            carTempF?.let { tempF = it.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last) }
            carDefrost?.let { defrost = it }
            carWheelHeat?.let { steeringHeat = WheelHeatLevel.fromApi(it) }
        }
    }
    LaunchedEffect(climateOn, carTempF, carDefrost, carWheelHeat) { if (climateOn) syncFromCar() }
    // Collapsing discards unsent edits: re-opening shows the car's settings while running, else the
    // last started climate.
    LaunchedEffect(expanded) {
        if (!expanded && settingsLoaded) {
            if (climateOn) syncFromCar() else vm.loadSavedClimate(v)?.let(applyRequest)
        }
    }

    // Starting a saved preset, or stopping the one running.
    val startPreset: (com.bloo.bluelink.data.ClimatePreset) -> Unit = { preset ->
        if (activePresetId == preset.id && climateOn) {
            vm.stopClimate(v)
            activePresetId = null
        } else {
            applyRequest(preset.request)
            startClimateWithEngineCheck(preset.request)
            activePresetId = preset.id
        }
    }
    // Smart climate needs the weather where the car is parked; load it here rather than waiting for the Location pebble to.
    // (Without a reading the button is still there and finds the weather itself when pressed.)
    LaunchedEffect(v.vin, state.locations[v.vin]) {
        if (state.carWeather[v.vin] == null) vm.loadCarWeather(v)
        if (state.homeWeather == null) vm.loadHomeWeather()
    }

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm, modifier,
        summary = when {
            climateOn && running -> "On · running"
            climateOn -> "On"
            else -> "Off"
        },
        headerAction = PebbleHeaderAction(
            label = when {
                climateOn && running -> "On"
                climateOn -> "Stop"
                else -> "Start"
            },
            icon = Icons.Filled.AcUnit,
            onClick = {
                if (climateOn) {
                    vm.stopClimate(v); activePresetId = null
                } else if (expanded) {
                    // The sliders are live, so Start sends exactly what they show, not the
                    // smart/preset logic.
                    startClimateWithEngineCheck(currentReq)
                } else if (simpleMode && weather != null) {
                    startAtTarget(smartClimateTargetF(ambientFahrenheit(weather.tempC)), currentReq)
                } else {
                    val defaultId = state.defaultClimatePresets[v.vin]
                    val matchingPreset = defaultId?.let { id -> presets.firstOrNull { it.id == id } }
                    if (matchingPreset != null) {
                        applyRequest(matchingPreset.request)
                        startClimateWithEngineCheck(matchingPreset.request)
                        activePresetId = matchingPreset.id
                    } else if (weather != null) {
                        startAtTarget(smartClimateTargetF(ambientFahrenheit(weather.tempC)), currentReq)
                    } else startClimateWithEngineCheck(currentReq)
                }
            },
            enabled = !running,
            pending = pending,
            active = climateOn,
            spinning = climateOn,
            // Running means the button is Stop: the deny red.
            activeContainer = denyTone().container,
            activeContent = denyTone().content,
        ),
    ) {
        // No hero: the summary is the same expression and already renders as the tile headline.
        if (running) {
            if (climateOn) {
                RevealText(
                    "On at the car. Read-only while the car is running.",
                    style = SecondaryTextStyle(),
                    color = mutedContentColor(),
                )
                status.airTemp?.let { t ->
                    t.value?.let { StatusRow("Set to", degLabel(it, fahrenheit, t.unit)) }
                }
                status.defrost?.let { StatusRow("Defrost", if (it) "On" else "Off") }
                status.steerWheelHeat?.let { StatusRow("Steering wheel heat", onOff(it)) }
                status.seatHeaterVentState?.let { s ->
                    s.flSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Driver seat", onOff(it)) }
                    s.frSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Passenger seat", onOff(it)) }
                }
            } else {
                RevealText(
                    "Climate can't be started while the car is running.",
                    style = SecondaryTextStyle(),
                    color = mutedContentColor(),
                )
            }
            return@Pebble
        }

        ClimatePresetSection(
            presets = presets,
            activeId = activePresetId,
            fahrenheit = fahrenheit,
            onStart = startPreset,
            onDelete = { id ->
                if (activePresetId == id) activePresetId = null
                vm.deleteClimatePreset(v, id)
            },
            onReorder = { vm.reorderClimatePresets(v, it) },
            onRename = { id, name -> vm.renameClimatePreset(v, id, name) },
        )

        // Smart climate: read the weather where the car is (falling back to home) and pick a target
        // -- see smartClimateTargetF, the same rule the tile command runner uses: ~10°F off ambient
        // normally, or the car's most aggressive setting on a genuinely extreme day, always within
        // what the car's own climate range actually accepts.
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            SectionLabel("Smart climate")
            val w = weather
            if (w != null) {
                val ambientF = ambientFahrenheit(w.tempC)
                val smartTarget = smartClimateTargetF(ambientF)
                val smartLabel = smartClimateLabel(ambientF, fahrenheit)
                MorphActionButton(
                    label = smartLabel,
                    icon = Icons.Filled.AcUnit,
                    onClick = { startAtTarget(smartTarget, currentReq) },
                    enabled = !pending && !climateOn,
                )
                RevealText(
                    smartClimateTargetLine(ambientF, fahrenheit),
                    style = SecondaryTextStyle(),
                    color = mutedContentColor(),
                )
            } else {
                // No reading yet (no car location, or still loading): the button finds the weather itself when pressed.
                MorphActionButton(
                    label = "Smart climate",
                    icon = Icons.Filled.AcUnit,
                    onClick = { vm.startSmartClimate(v) },
                    enabled = !pending && !climateOn,
                )
                RevealText(
                    "Sets the temperature for the weather where your car is parked.",
                    style = SecondaryTextStyle(),
                    color = mutedContentColor(),
                )
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
            // state.powertrainOf, not the raw v.isEv: a user's powertrain override is honoured here
            // too.
            showSeats = seats.any && !(isGen5W && state.powertrainOf(v) == com.bloo.bluelink.data.Powertrain.EV),
            driver = driver, onDriver = { driver = it },
            passenger = passenger, onPassenger = { passenger = it },
            rearLeft = rearLeft, onRearLeft = { rearLeft = it },
            rearRight = rearRight, onRearRight = { rearRight = it },
            onSaveAsPreset = { presetName = ""; showAddPreset = true },
        )

        if (showAddPreset) {
            // Shared GlassAlertDialog shell (stacked buttons).
            GlassAlertDialog(
                onDismissRequest = { showAddPreset = false },
                icon = Icons.Filled.Thermostat,
                title = "Save preset",
                text = {
                    BlooTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { RevealText("Name") },
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
                        emphasis = ButtonEmphasis.Confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MorphTextButton(
                        "Cancel",
                        onClick = { showAddPreset = false },
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = ButtonEmphasis.Deny,
                    )
                },
            )
        }
    }
}

private fun TempValue.asFahrenheit(): Int? {
    val n = value?.toDoubleOrNull() ?: return null
    return (if (unit == 0) n * 9 / 5 + 32 else n).roundToInt()
}

/** How long after a start the car's old reading of its settings is distrusted (see `lastSent`). */
private const val CarCatchUpMs = 90_000L
