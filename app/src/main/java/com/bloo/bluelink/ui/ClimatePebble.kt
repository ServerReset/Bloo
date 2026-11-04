package com.bloo.bluelink.ui

/** The Climate pebble: [ClimatePebble]. */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.AnimatedValue
import com.bloo.uicommon.AnimatedSlider
import com.bloo.bluelink.data.FEEL_STEP_F
import com.bloo.bluelink.data.smartClimateMode
import com.bloo.bluelink.data.smartComfort
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.feelLabel
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.SecondaryTextStyle
import com.bloo.uicommon.RevealText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.TempValue
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.cToF
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.deleteClimatePreset
import com.bloo.bluelink.data.saveClimatePreset
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.ClimateChoices
import com.bloo.bluelink.data.SmartClimatePlan
import com.bloo.bluelink.data.smartClimatePlan
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.smartClimateTargetF
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
    val steeringLevels = if (state.isGen5WEffective(v)) Gen5WSteeringLevels else WheelHeatLevel.entries
    var showAddPreset by remember { mutableStateOf(false) }

    // Warn if the engine is on before changing climate settings.
    // What was last sent and when: the car's own reading of its set temperature lags a command by a refresh, and
    // mirroring that stale reading straight back put the slider on whatever the car last reported (72) the moment a
    // start went out. Until the car reports what was sent (or a minute passes), the slider keeps what was sent.
    var lastSent by remember(v.vin) { mutableStateOf<Pair<ClimateRequest, Long>?>(null) }
    val startClimateWithEngineCheck: (ClimateRequest) -> Unit = { req ->
        if (status?.engine == true) vm.reportInfo("Changes may be rejected while the car runs.")
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
        // The seats, wheel and defrost follow the weather where the car is, for what this car has and what the person has taught it.
        val w = state.carWeather[v.vin] ?: state.homeWeather
        val comfort = w?.let { smartComfort(it, comfortCapsOf(state.seatConfigFor(v)), state.comfortModelOf(v.vin)) }
        if (w != null && comfort != null) {
            SmartComfortLearning.noteSmart(v.vin, comfort, smartClimateMode(cToF(w.feelsLikeC).roundToInt()))
        }
        startClimateWithEngineCheck(
            base.copy(
                tempF = target,
                defrost = comfort?.defrost ?: false,
                steeringWheelHeat = comfort?.wheel ?: base.steeringWheelHeat,
                seatFrontLeft = comfort?.seatDriver ?: base.seatFrontLeft,
                seatFrontRight = comfort?.seatPassenger ?: base.seatFrontRight,
                seatRearLeft = comfort?.seatRearLeft ?: base.seatRearLeft,
                seatRearRight = comfort?.seatRearRight ?: base.seatRearRight,
            ),
        )
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
    val climateContext = LocalContext.current
    // What smart climate would set right now: the weather, and the temperatures this person has chosen by hand before.
    val smartPlan = weather?.let {
        smartClimatePlan(
            it, ClimateChoices.list(climateContext, v.vin), fahrenheit, ClimateChoices.feedback(climateContext, v.vin),
            learned = state.climateModels[v.vin],
        )
    }
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
                    // smart/preset logic. A hand-set start is kept, so smart climate learns what this person picks.
                    weather?.let { ClimateChoices.record(climateContext, v.vin, ambientFahrenheit(it.tempC), currentReq.tempF) }
                    startClimateWithEngineCheck(currentReq)
                } else if (simpleMode && weather != null) {
                    startAtTarget(smartPlan?.targetF ?: smartClimateTargetF(ambientFahrenheit(weather.tempC)), currentReq)
                } else {
                    val defaultId = state.defaultClimatePresets[v.vin]
                    val matchingPreset = defaultId?.let { id -> presets.firstOrNull { it.id == id } }
                    if (matchingPreset != null) {
                        applyRequest(matchingPreset.request)
                        startClimateWithEngineCheck(matchingPreset.request)
                        activePresetId = matchingPreset.id
                    } else if (weather != null) {
                        startAtTarget(smartPlan?.targetF ?: smartClimateTargetF(ambientFahrenheit(weather.tempC)), currentReq)
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
                    listOf(
                        "Driver seat" to s.flSeatHeatState,
                        "Passenger seat" to s.frSeatHeatState,
                        "Rear left seat" to s.rlSeatHeatState,
                        "Rear right seat" to s.rrSeatHeatState,
                    ).forEach { (label, level) ->
                        if (level != null) StatusRow(label, seatLevelLabel(level))
                    }
                }
            } else {
                RevealMutedText("Climate can't be started while the car is running.")
            }
            return@Pebble
        }

        // While climate runs, its presets, smart climate and controls go away, and what is running reads back in their place.
        ExpandVisibility(!climateOn) {
            SmartClimateSection(
                ambientF = weather?.let { ambientFahrenheit(it.tempC) },
                plan = smartPlan,
                fahrenheit = fahrenheit,
                enabled = !pending && !climateOn,
                onSubmitFeel = { delta ->
                    // The feel slider: what they wanted, against the plan, is kept, and the car starts at that setting.
                    weather?.let {
                        ClimateChoices.recordFeedback(climateContext, v.vin, ambientFahrenheit(it.tempC), delta)
                        vm.learnClimate(v, ambientFahrenheit(it.tempC), delta)
                    }
                    smartPlan?.let { startAtTarget(it.targetF + delta, currentReq) }
                },
                onStartUnread = {
                    if (!pending) vm.startSmartClimate(v) { req ->
                        form.load(req); activePresetId = null; lastSent = req to System.currentTimeMillis()
                    }
                },
            )
        }

        // Presets, departures and seats & wheel are all shown inline, each in its own inner panel, so the climate controls are
        // in reach without opening anything first. They give way to the running summary while climate is on.
        ExpandVisibility(!climateOn) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                ClimateSubPanel {
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
                        steeringLevels = steeringLevels,
                        seats = seats,
                        showSeats = showSeatControls,
                    )
                }
                ClimateSubPanel { DepartureSection(v, state, vm) }
                ClimateSubPanel {
                    SectionLabel("Seats & wheel")
                    ClimateControlsSection(
                        form = form,
                        fahrenheit = fahrenheit,
                        seats = seats,
                        // state.powertrainOf, not the raw v.isEv: a user's powertrain override is honoured here
                        // too.
                        showSeats = showSeatControls,
                        onSaveAsPreset = { showAddPreset = true },
                        steeringLevels = steeringLevels,
                    )
                }
            }
        }

        ExpandVisibility(showAddPreset && !climateOn) {
            SavePresetForm(
                onSave = { name -> vm.saveClimatePreset(v, name, currentReq); showAddPreset = false },
                onCancel = { showAddPreset = false },
            )
        }

        ExpandVisibility(climateOn) {
            ClimateRunningSummary(
                status = status,
                presetName = presets.firstOrNull { it.id == activePresetId }?.name,
                fahrenheit = fahrenheit,
            )
        }
    }
}

/**
 * Smart climate, as one card: the target the car will take and the outside temperature it answers, an influence slider that leans the
 * target toward hot or cold, one button that starts the car there, and the factors that moved the target. With no weather reading yet
 * the button finds the weather itself when pressed.
 */
@Composable
private fun SmartClimateSection(
    ambientF: Int?,
    plan: SmartClimatePlan?,
    fahrenheit: Boolean,
    enabled: Boolean,
    /** The influence slider's answer, in degrees from the plan (negative colder, positive hotter), submitted with the start. */
    onSubmitFeel: (Int) -> Unit,
    onStartUnread: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SectionLabel(CarLabels.SMART_CLIMATE)
        if (plan == null || ambientF == null) {
            MorphActionButton(label = CarLabels.SMART_CLIMATE, icon = Icons.Filled.AcUnit, onClick = onStartUnread, enabled = enabled)
            return@Column
        }
        RevealText(degLabel(plan.targetF.toString(), fahrenheit), style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
        RevealMutedText("${degLabel(ambientF.toString(), fahrenheit)} outside")

        // Influence: lean the target colder or hotter than the plan. It opens in the middle, on the plan as it stands.
        var influence by remember(plan.targetF) { mutableFloatStateOf(0f) }
        val step = influence.roundToInt().coerceIn(-1, 1)
        val target = plan.targetF + step * FEEL_STEP_F
        val scheme = MaterialTheme.colorScheme
        val haptics = com.bloo.uicommon.LocalHaptics.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RevealMutedText("Colder")
            RevealMutedText("Hotter")
        }
        AnimatedSlider(
            value = influence,
            onValueChange = { influence = it },
            valueRange = -1f..1f,
            steps = 1,
            accent = scheme.primary,
            inactiveColor = scheme.surfaceContainerHighest,
            dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
            dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            reduceMotion = com.bloo.uicommon.LocalReduceMotion.current,
            onStepTick = { haptics?.tick() },
            onSettle = { haptics?.click() },
            enabled = enabled,
        )
        RevealMutedText("${feelLabel(step)} · ${degLabel(target.toString(), fahrenheit)}")
        MorphActionButton(
            label = "Start at ${degLabel(target.toString(), fahrenheit)}",
            icon = Icons.Filled.AcUnit,
            onClick = { onSubmitFeel(step * FEEL_STEP_F) },
            enabled = enabled,
        )
        // Every factor that moved the target, one per line, so the number is never a mystery.
        plan.reasons.joinToString("\n").takeIf { it.isNotBlank() }?.let { RevealMutedText(it) }
    }
}

/** Names the climate settings on screen as a new preset, in place under the controls: no pop-up. */
@Composable
private fun SavePresetForm(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        LabeledField(name, { name = it }, "Name")
        ConfirmCancelButtons("Save", onConfirm = { if (name.isNotBlank()) onSave(name.trim()) }, onCancel = onCancel, enabled = name.isNotBlank())
    }
}

/**
 * What a running climate shows in place of its controls: the temperature it is set to, and what is on, each as a highlight. The car
 * reports these, so the summary is what is actually running. It goes away with the controls when climate stops.
 */
@Composable
private fun ClimateRunningSummary(status: VehicleStatus?, presetName: String?, fahrenheit: Boolean) {
    val setTo = status?.airTemp?.let { t -> t.value?.let { degLabel(it, fahrenheit, t.unit) } }
    val highlights = buildList {
        presetName?.let { add("Preset" to it) }
        if (status?.defrost == true) add("Defrost" to "On")
        if (status?.steerWheelHeat?.let { it != 0 } == true) add("Steering wheel" to "On")
        status?.seatHeaterVentState?.let { seatState ->
            // Every seat, always: an off seat reads "Off" rather than dropping out of the list.
            listOf(
                "Driver" to seatState.flSeatHeatState,
                "Passenger" to seatState.frSeatHeatState,
                "Rear left" to seatState.rlSeatHeatState,
                "Rear right" to seatState.rrSeatHeatState,
            ).forEach { (label, state) ->
                if (state != null) add(label to seatLevelLabel(state))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        setTo?.let {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RevealText("Set to", style = SecondaryTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                AnimatedValue(
                    it,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }
        highlights.forEach { (label, value) -> ClimateHighlight(label, value) }
        if (highlights.isEmpty()) RevealMutedText("Nothing else is on.")
    }
}

/** A seat's heat or vent level as the card shows it: its label, or "Off" for a seat that is off. */
private fun seatLevelLabel(state: Int): String {
    val level = SeatLevel.fromApi(state)
    return if (level == SeatLevel.OFF) "Off" else level.label
}

@Composable
private fun ClimateHighlight(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = GapGroup, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RevealText(
            label, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, modifier = Modifier.weight(1f),
        )
        RevealText(
            value, style = PrimaryTextStyle(), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1,
        )
    }
}

private fun TempValue.asFahrenheit(): Int? {
    val n = value?.toDoubleOrNull() ?: return null
    return (if (unit == 0) cToF(n) else n).roundToInt()
}

/** How long after a start the car's old reading of its settings is distrusted (see `lastSent`). */
private const val CarCatchUpMs = 90_000L

/** One of the climate pebble's inline sub-areas (presets, departures, seats & wheel): the app's inner glass panel, full width. */
@Composable
private fun ClimateSubPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().innerGlassPanel(), verticalArrangement = Arrangement.spacedBy(GapRow), content = content)
}
