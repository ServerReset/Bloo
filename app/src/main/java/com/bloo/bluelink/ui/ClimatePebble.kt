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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.splitPillShapes
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn


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
    dragHandle: Modifier,
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
            vm.reportInfo("AC changes might be rejected because the car is already running and remote climate control takes priority")
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

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm, dragHandle,
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
                    "Climate is on at the car. It ignores app commands while you're driving, so this is read-only.",
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Smart climate")
                    val smartSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = smartSource,
                        enabled = !pending && !climateOn,
                    ) {
                        // The shared MorphActionButton: an ordinary "tap this and the car does
                        // a thing" action, so it takes the standard outlined-tonal look rather
                        // than the bare default fill at its own one-off 12dp vertical padding.
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
                            interactionSource = smartSource,
                        )
                    }
                    Text(
                        "It's $ambientLabel where your car is. Smart climate is targeting $targetLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                    )
                }
            }
        }

        SectionLabel("Controls")

        // Show the set temperature when climate is running, with an animated entrance.
        AnimatedVisibility(
            visible = climateOn,
            enter = expandEnterSized(),
            exit = expandExitSized(),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MutedText("Set temperature")
                // color resolved explicitly to onSurface -- same fix, same reason as
                // the update pebble's own AnimatedValue calls: BasicText (which this
                // renders through) doesn't fall back to LocalContentColor the way a
                // plain Text() does, so this rendered unreadably dark instead of
                // standing out against the muted label beside it -- the value, not
                // the label, is the important half of this row.
                com.bloo.uicommon.AnimatedValue(
                    degLabel(tempF.toString(), fahrenheit),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }

        // Was a hand-rolled version of the same blue->green->warm mapping
        // uicommon.tempColor() now centralizes (an earlier copy had drifted to
        // a different, unanimated palette).
        val tempRange = CLIMATE_TEMP_RANGE_F.first.toFloat()..CLIMATE_TEMP_RANGE_F.last.toFloat()
        val tempColor = com.bloo.uicommon.tempColor(tempF, tempRange.start, tempRange.endInclusive)
        // The label + value readout is the same in either unit -- only degLabel's
        // suffix (°F/°C) and the slider below differ -- so it's hoisted out of the
        // branch. RollingNumber (used for the hero's %/range) rather than the plain
        // AnimatedValue this had: it rolls the DIRECTION the value actually moved (up
        // when dragged warmer, down when cooler) instead of always sliding one way.
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MutedText("Temperature")
            RollingNumber(
                text = degLabel(tempF.toString(), fahrenheit),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = tempColor,
            )
        }
        if (fahrenheit) {
            AnimatedSlider(
                value = tempF.toFloat(),
                onValueChange = { tempF = it.roundToInt() },
                valueRange = tempRange,
                steps = 19,
                accent = tempColor,
            )
        } else {
            // Celsius: drive the slider in whole °C but keep tempF canonical for
            // the command, converting on each side.
            val tempC = ((tempF - 32) * 5 / 9f).roundToInt()
            AnimatedSlider(
                value = tempC.toFloat(),
                onValueChange = { tempF = (it * 9 / 5f + 32).roundToInt() },
                valueRange = 17f..28f,
                steps = 10,
                accent = tempColor,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "Run time",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            // RollingNumber, not StepRow's built-in roll: StepRow's AnimatedContent
            // always slides the same direction regardless of which way the value
            // moved, which reads oddly on a slider you're actively dragging both
            // ways. RollingNumber rolls up when the minutes increase, down when
            // they decrease, matching every other draggable number in the app.
            RollingNumber(
                text = "$duration min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        AnimatedSlider(
            value = duration.toFloat(),
            // Extended range: the car itself has no single command past
            // CLIMATE_DURATION_RANGE's 10-minute cap -- a request beyond that
            // is auto-chained into follow-up commands instead (see
            // AppViewModel.startClimate / ClimateExtendWorker), so the slider
            // can go further than any one command actually could.
            onValueChange = { duration = it.roundToInt() },
            valueRange = CLIMATE_EXTENDED_DURATION_RANGE.first.toFloat()..CLIMATE_EXTENDED_DURATION_RANGE.last.toFloat(),
            steps = CLIMATE_EXTENDED_DURATION_RANGE.last - CLIMATE_EXTENDED_DURATION_RANGE.first - 1,
        )
        AnimatedVisibility(
            visible = duration > CLIMATE_DURATION_RANGE.last,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            LabelSmallText(
                "Sent as ${climateChunksLabel(duration)}, continued automatically",
            )
        }

        ToggleRow("Defrost", defrost) { defrost = it }
        if (seats.steeringWheel) {
            WheelHeatControl(steeringHeat) { steeringHeat = it }
        }

        val isGen5W = state.isGen5WEffective(v)
        // state.powertrainOf(v), not the raw v.isEv -- the same "one shared
        // powertrain rule" every other display decision in the app goes
        // through (see resolvePowertrain's own doc, SettingsStore.kt), so a
        // user's own powertrain override (e.g. correcting a PHEV the API
        // reports as gas) is honoured here too, not just everywhere else.
        if (seats.any && !(isGen5W && state.powertrainOf(v) == com.bloo.bluelink.data.Powertrain.EV)) {
            SectionLabel("Seats")
            if (seats.driverHeat || seats.driverCool) {
                SeatControl("Driver seat", driver, seats.driverCool, seats.driverHeat) { driver = it }
            }
            if (seats.passHeat || seats.passCool) {
                SeatControl("Passenger seat", passenger, seats.passCool, seats.passHeat) { passenger = it }
            }
            if (seats.rearLeftHeat || seats.rearLeftCool) {
                SeatControl("Rear left seat", rearLeft, seats.rearLeftCool, seats.rearLeftHeat) { rearLeft = it }
            }
            if (seats.rearRightHeat || seats.rearRightCool) {
                SeatControl("Rear right seat", rearRight, seats.rearRightCool, seats.rearRightHeat) { rearRight = it }
            }
        }

        SectionLabel("Save")
        val savePresetSource = remember { MutableInteractionSource() }
        SafeExpansiveButton(
            interactionSource = savePresetSource,
            enabled = true,
        ) {
            MorphTextButton(
                text = "Save as preset",
                interactionSource = savePresetSource,
                onClick = { presetName = ""; showAddPreset = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showAddPreset) {
            // Standardized on the shared GlassAlertDialog shell (stacked buttons).
            GlassAlertDialog(
                onDismissRequest = { showAddPreset = false },
                icon = Icons.Filled.Thermostat,
                title = "Save preset",
                text = {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                buttons = {
                    val saveSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = saveSource,
                        enabled = presetName.isNotBlank(),
                    ) {
                        // MorphTextButton, not a hand-rolled MorphButton{Text(...)} -- that Text
                        // had no `style`, so it rendered at ambient size instead of
                        // ButtonLabelStyle. Primary colours stand in for the old `active = true`,
                        // which MorphTextButton has no direct param for.
                        MorphTextButton(
                            "Save",
                            onClick = {
                                if (presetName.isNotBlank()) {
                                    vm.saveClimatePreset(v, presetName.trim(), currentReq)
                                    showAddPreset = false
                                }
                            },
                            enabled = presetName.isNotBlank(),
                            interactionSource = saveSource,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val cancelSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = cancelSource,
                        enabled = true,
                    ) {
                        MorphTextButton(
                            "Cancel",
                            onClick = { showAddPreset = false },
                            interactionSource = cancelSource,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            )
        }
    }
}

