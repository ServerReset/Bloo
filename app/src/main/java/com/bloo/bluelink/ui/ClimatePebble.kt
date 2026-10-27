package com.bloo.bluelink.ui

/** The Climate pebble: [ClimatePebble]. */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.ClimateRequest
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
import com.bloo.bluelink.data.saveClimatePreset
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.smartClimateLabel
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.smartClimateTargetLine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.denyTone
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
    // Seats show only where the car has them, and not on a Gen 5 EV that never reports them. Shared by the controls and the preset editor.
    val showSeatControls = seats.any && !(state.isGen5WEffective(v) && state.powertrainOf(v) == com.bloo.bluelink.data.Powertrain.EV)
    val form = rememberClimateForm(v.vin)
    var settingsLoaded by remember(v.vin) { mutableStateOf(false) }

    // Copies a ClimateRequest into the slider state; shared by restore and preset-apply. The
    // cross-composition sync below maps through SeatLevel.fromApi, so it doesn't reuse this.
    val applyRequest: (ClimateRequest) -> Unit = form::load

    // Restore the car's last-used climate settings the first time the pebble shows.
    LaunchedEffect(v.vin) {
        vm.loadSavedClimate(v)?.let(applyRequest)
        settingsLoaded = true
    }

    val currentReq = form.toRequest()

    val presets = state.climatePresets[v.vin].orEmpty()
    var showAddPreset by remember { mutableStateOf(false) }

    // Warn if the engine is on before changing climate settings.
    // What was last sent and when: the car's own reading of its set temperature lags a command by a refresh, and
    // mirroring that stale reading straight back put the slider on whatever the car last reported (72) the moment a
    // start went out. Until the car reports what was sent (or a minute passes), the slider keeps what was sent.
    var lastSent by remember(v.vin) { mutableStateOf<Pair<ClimateRequest, Long>?>(null) }
    val startClimateWithEngineCheck: (ClimateRequest) -> Unit = { req ->
        if (status?.engine == true) vm.reportInfo("Climate changes may be rejected while the car is running")
        lastSent = req to System.currentTimeMillis()
        vm.startClimate(v, req)
    }
    // Which preset (if any) is currently applied: set when you start one, and cleared automatically
    // once the live settings drift away from it (e.g. you nudge a slider) so the highlight only
    // marks a true match.
    var activePresetId by remember(v.vin) { mutableStateOf<String?>(null) }
    // Start at [target] degrees F with defrost off, clearing any highlighted preset: the one-tap
    // "smart" start.
    val startAtTarget: (Int, ClimateRequest) -> Unit = start@{ target, base ->
        if (pending) return@start
        form.tempF = target; form.defrost = false; activePresetId = null
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
        form.tempF = r.tempF
        form.duration = r.durationMinutes
        form.defrost = r.defrost
        form.steeringHeat = WheelHeatLevel.fromApi(r.steering)
        form.driver = SeatLevel.fromApi(r.seatFrontLeft)
        form.passenger = SeatLevel.fromApi(r.seatFrontRight)
        form.rearLeft = SeatLevel.fromApi(r.seatRearLeft)
        form.rearRight = SeatLevel.fromApi(r.seatRearRight)
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
            carTempF?.let { form.tempF = it.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last) }
            carDefrost?.let { form.defrost = it }
            carWheelHeat?.let { form.steeringHeat = WheelHeatLevel.fromApi(it) }
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
    val startPreset: (com.bloo.bluelink.data.ClimatePreset) -> Unit = start@{ preset ->
        // A request is still in flight: pressing again would send a second one (and a second notice).
        if (pending) return@start
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
                RevealMutedText("On at the car. Read-only while the car is running.")
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
                RevealMutedText("Climate can't be started while the car is running.")
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
            onUpdate = { id, name, request -> vm.updateClimatePreset(v, id, name, request) },
            seats = seats,
            showSeats = showSeatControls,
        )

        SmartClimateSection(
            ambientF = weather?.let { ambientFahrenheit(it.tempC) },
            fahrenheit = fahrenheit,
            enabled = !pending && !climateOn,
            onStartAtTarget = { target -> startAtTarget(target, currentReq) },
            onStartUnread = {
                if (!pending) vm.startSmartClimate(v) { req ->
                    form.load(req); activePresetId = null; lastSent = req to System.currentTimeMillis()
                }
            },
        )

        ClimateControlsSection(
            form = form,
            locked = climateOn,
            fahrenheit = fahrenheit,
            seats = seats,
            // state.powertrainOf, not the raw v.isEv: a user's powertrain override is honoured here
            // too.
            showSeats = showSeatControls,
            onSaveAsPreset = { showAddPreset = true },
        )

        if (showAddPreset) {
            SavePresetDialog(
                onSave = { name -> vm.saveClimatePreset(v, name, currentReq); showAddPreset = false },
                onDismiss = { showAddPreset = false },
            )
        }
    }
}

/**
 * Smart climate: the weather where the car is (falling back to home) picks a target, by [smartClimateTargetF], the same rule the
 * tile command runner uses: about 10°F off ambient, or the car's most aggressive setting on a genuinely extreme day, always within
 * what the car's climate range accepts. With no reading yet ([ambientF] null) the button finds the weather itself when pressed.
 */
@Composable
private fun SmartClimateSection(
    ambientF: Int?,
    fahrenheit: Boolean,
    enabled: Boolean,
    onStartAtTarget: (Int) -> Unit,
    onStartUnread: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SectionLabel(CarLabels.SMART_CLIMATE)
        MorphActionButton(
            label = ambientF?.let { smartClimateLabel(it, fahrenheit) } ?: CarLabels.SMART_CLIMATE,
            icon = Icons.Filled.AcUnit,
            onClick = { if (ambientF != null) onStartAtTarget(smartClimateTargetF(ambientF)) else onStartUnread() },
            enabled = enabled,
        )
        RevealMutedText(ambientF?.let { smartClimateTargetLine(it, fahrenheit) } ?: "Sets the temperature for the weather where your car is parked.")
    }
}

/** Names and saves the climate settings on screen as a preset. */
@Composable
private fun SavePresetDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Thermostat,
        title = "Save preset",
        text = {
            LabeledField(name, { name = it }, "Name")
        },
        buttons = {
            ConfirmCancelButtons("Save", onConfirm = { if (name.isNotBlank()) onSave(name.trim()) }, onCancel = onDismiss, enabled = name.isNotBlank())
        },
    )
}

private fun TempValue.asFahrenheit(): Int? {
    val n = value?.toDoubleOrNull() ?: return null
    return (if (unit == 0) n * 9 / 5 + 32 else n).roundToInt()
}

/** How long after a start the car's old reading of its settings is distrusted (see `lastSent`). */
private const val CarCatchUpMs = 90_000L
