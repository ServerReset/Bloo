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
import androidx.compose.animation.core.spring
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
 *  2. `remoteClimate` (from `state.climateSync`) mirrors whatever the watch
 *     app or another session set; a [LaunchedEffect] keyed on it snaps all
 *     the local state to match whenever it changes.
 *  3. A single debounced [LaunchedEffect] keyed on `(currentReq,
 *     activePresetId)` persists + publishes the current settings back out
 *     (to storage and to the watch) after they stop changing -- the actual
 *     400ms debounce lives in the ViewModel's own coroutine scope rather
 *     than in this effect, specifically so a car-switch or pebble collapse
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
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var driver by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var passenger by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearLeft by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearRight by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var settingsLoaded by remember(v.vin) { mutableStateOf(false) }

    // Copy a ClimateRequest's nine fields into the sliders' state. Defined up here so the
    // restore effect just below and the preset-apply buttons further down share ONE copy of the
    // assignment -- it was written out twice, byte-for-byte, and "restore last-used" and "apply
    // preset" are the same operation (set the sliders from a request). Captures only the nine
    // `var` setters above it. NOT reused by the watch-sync effect below, which maps through
    // SeatLevel.fromApi and so is genuinely different.
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
    // Persist + watch-mirror is handled by ONE debounced call further down
    // (after activePresetId exists) - see the LaunchedEffect near the climate
    // sync block.

    val presets = state.climatePresets[v.vin].orEmpty()
    var showAddPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
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

    // --- Two-way climate sync with the watch ----------------------------------
    // Reflect whatever the watch (or another session) set: sliders + active preset.
    val remoteClimate = state.climateSync[v.vin]
    LaunchedEffect(remoteClimate) {
        val r = remoteClimate ?: return@LaunchedEffect
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steering
        driver = SeatLevel.fromApi(r.seatFrontLeft)
        passenger = SeatLevel.fromApi(r.seatFrontRight)
        rearLeft = SeatLevel.fromApi(r.seatRearLeft)
        rearRight = SeatLevel.fromApi(r.seatRearRight)
        activePresetId = r.activePresetId
    }
    // Persist + publish-to-watch once settings stop changing, not on every drag
    // tick: publishClimateState updates the shared ViewModel StateFlow the whole
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
                    startClimate()
                } else if (simpleMode && weather != null) {
                    val ambientF = ambientFahrenheit(weather.tempC)
                    val smartTarget = smartClimateTargetF(ambientF)
                    tempF = smartTarget; defrost = false; activePresetId = null
                    vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                } else {
                    val defaultId = state.defaultClimatePresets[v.vin]
                    val matchingPreset = defaultId?.let { id -> presets.firstOrNull { it.id == id } }
                    if (matchingPreset != null) {
                        applyRequest(matchingPreset.request)
                        vm.startClimate(v, matchingPreset.request)
                        activePresetId = matchingPreset.id
                    } else if (weather != null) {
                        val ambientF = ambientFahrenheit(weather.tempC)
                        val smartTarget = smartClimateTargetF(ambientF)
                        tempF = smartTarget; defrost = false; activePresetId = null
                        vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                    } else startClimate()
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
                    vm.startClimate(v, preset.request)
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
        // and pick a target -- see smartClimateTargetF, shared with the widget/QS
        // tile and the watch: ~10°F off ambient normally, or the car's most
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
                        MorphButton(
                            onClick = {
                                tempF = smartTarget
                                defrost = false
                                activePresetId = null
                                vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                            },
                            enabled = !pending && !climateOn,
                            interactionSource = smartSource,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                        ) {
                            Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(smartLabel, fontWeight = FontWeight.SemiBold)
                        }
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
            enter = collapseEnter(),
            exit = collapseExit(),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Set temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // uicommon.tempColor() now centralizes (shared with the watch, which
        // had drifted to a different, unanimated palette).
        val tempRange = CLIMATE_TEMP_RANGE_F.first.toFloat()..CLIMATE_TEMP_RANGE_F.last.toFloat()
        val tempColor = com.bloo.uicommon.tempColor(tempF, tempRange.start, tempRange.endInclusive)
        // The label + value readout is the same in either unit -- only degLabel's
        // suffix (°F/°C) and the slider below differ -- so it's hoisted out of the
        // branch. RollingNumber (used for the hero's %/range) rather than the plain
        // AnimatedValue this had: it rolls the DIRECTION the value actually moved (up
        // when dragged warmer, down when cooler) instead of always sliding one way.
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Text(
                "Sent as ${climateChunksLabel(duration)}, continued automatically",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ToggleRow("Defrost", defrost) { defrost = it }
        if (seats.steeringWheel) {
            ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
        }

        val isGen5W = state.isGen5WEffective(v)
        if (seats.any && !(isGen5W && v.isEv)) {
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
                        MorphButton(
                            onClick = {
                                if (presetName.isNotBlank()) {
                                    vm.saveClimatePreset(v, presetName.trim(), currentReq)
                                    showAddPreset = false
                                }
                            },
                            enabled = presetName.isNotBlank(),
                            active = true,
                            interactionSource = saveSource,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
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

@Composable
internal fun SeatControl(
    label: String,
    level: SeatLevel,
    canCool: Boolean,
    canHeat: Boolean,
    onChange: (SeatLevel) -> Unit,
) {
    val range = SeatLevel.rangeFor(canCool, canHeat)
    if (range.size <= 1) return
    val index = range.indexOf(level).let { if (it < 0) range.indexOf(SeatLevel.OFF) else it }
    val current = range.getOrNull(index) ?: range.firstOrNull() ?: return
    // Deeper colour the stronger the setting; smoothly cross-fades as you slide
    // through neutral between cooling (blues) and heating (reds).
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = seatTint(current),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seatTint",
    )
    Column {
        // The level text (e.g. "High cool") wears the slider's colour, so OFF is
        // neutral, cooling reads blue and heating reads red - no caption needed.
        StepRow(label, current.label, valueColor = tint)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            accent = tint,
        )
    }
}

/** Seat colour by intensity: light->dark blue for cool, light->dark red for heat. */
@Composable
internal fun seatTint(level: SeatLevel): Color = when {
    level.isCool -> androidx.compose.ui.graphics.lerp(
        Color(0xFF82B1FF), Color(0xFF1A45C0), ((level.apiValue - 3) / 2f).coerceIn(0f, 1f),
    )
    level.isHeat -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF8A80), Color(0xFFC62828), ((level.apiValue - 6) / 2f).coerceIn(0f, 1f),
    )
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// --- Climate presets section ----------------------------------------------

@Composable
internal fun ClimatePresetSection(
    presets: List<ClimatePreset>,
    activeId: String?,
    fahrenheit: Boolean,
    onStart: (ClimatePreset) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<ClimatePreset>) -> Unit,
) {
    // Track IDs mid-exit so the item stays visible until its shrink animation ends.
    var deletingIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = presets.isNotEmpty(),
        enter = collapseEnter(Alignment.Bottom),
        exit = collapseExit(Alignment.Bottom),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // The heading lives INSIDE the visibility gate. Outside it, a user with no saved
            // presets got a "Presets" heading over nothing at all -- a whole line of a cover
            // screen spent announcing an empty section. It also means the label animates away
            // with the last preset instead of being left behind.
            SectionLabel("Presets")
            Spacer(Modifier.height(4.dp))
            // Full-width reorderable rows: drag handle to re-rank, tap to apply.
            ReorderColumn(
                items = presets,
                keyOf = { it.id },
                onReorder = onReorder,
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { preset, dragHandle, _ ->
                AnimatedVisibility(
                    visible = preset.id !in deletingIds,
                    enter = scaleIn(tween(240, easing = LinearOutSlowInEasing), initialScale = 0.88f) +
                        expandVertically(tween(260)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(180, easing = FastOutLinearInEasing), targetScale = 0.88f) +
                        shrinkVertically(tween(220)) + fadeOut(tween(160)),
                ) {
                    PresetPill(
                        name = preset.name,
                        detail = presetDetail(preset.request, fahrenheit),
                        active = preset.id == activeId,
                        onStart = { onStart(preset) },
                        onDelete = {
                            val id = preset.id
                            scope.launch {
                                deletingIds = deletingIds + id
                                delay(240)
                                onDelete(id)
                                deletingIds = deletingIds - id
                            }
                        },
                        dragHandle = dragHandle,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A compact "79° · Defrost · Heat" summary of what a preset will set. */
internal fun presetDetail(req: ClimateRequest, fahrenheit: Boolean): String {
    val parts = mutableListOf<String>()
    // Bare "°" rather than degLabel's "°F"/"°C": this is a compact one-line summary
    // where the unit is already established by everything around it. The CONVERSION
    // is shared now though -- this used to re-inline the °F-to-°C arithmetic, so the
    // rounding rule lived here as well as in degLabel and could drift from it.
    parts += "${degValue(req.tempF.toDouble(), fahrenheit)}°"
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat) parts += "Wheel"
    return parts.joinToString(" · ")
}


/**
 * A two-segment split button for a saved preset, styled after M3 Expressive
 * connected-button group #5: a wider "start" half and a narrow "delete" half,
 * each a pill on its outer edge with a smaller radius on the inner edge. The two
 * are separated by a real gap (not a drawn line) so the pebble background shows
 * through and they read as distinct buttons.
 *
 * Tapping the start half loads the preset into the climate controls and fires it;
 * while it is the [active] (currently applied) preset, that half morphs from a
 * pill into a rounded rectangle and fills with the running-climate highlight,
 * exactly like the Start button when climate is on. The delete half removes it.
 */
@Composable
internal fun PresetPill(
    name: String,
    detail: String,
    active: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    // Delete was a single un-confirmable tap right beside the much larger,
    // frequently-tapped Apply half -- a slightly mis-aimed tap silently and
    // irreversibly dropped a saved preset. Now requires a second tap, same
    // "tap again to confirm" pattern (with the same 4s auto-reset) used for
    // Sign out and the watch's own preset-delete confirm.
    val confirm = rememberConfirmArm()
    // No measured row height any more. These shapes used to be derived from one -- the row was
    // measured with onSizeChanged, the height written to state, and the whole row recomposed to
    // rebuild the shapes, a measure -> state -> recompose loop that ran whenever the height
    // changed. All it was computing was "16dp, expressed as a percent of this row", which
    // CornerSize(Dp) states directly. The morphed OUTER corner goes back to the app-wide
    // MorphedCornerPercent every other button uses, which is one less thing these two pills do
    // differently from everything around them.
    val leftShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        splitPillShapes(morph, cp).first
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        splitPillShapes(morph, cp).second
    }

    // The drag handle wraps the whole pill so long-press anywhere reorders.
    // A real button group, not a Row of separately-wrapped buttons. groupWeight on the Apply
    // half is what makes it span the row (Modifier.weight cannot reach a group member), and
    // being members is what lets pressing either half take width from the other instead of
    // shoving it.
    ExpressiveButtonRow(
        modifier = dragHandle.fillMaxWidth().height(IntrinsicSize.Min),
        spacing = 3.dp,
        verticalAlignment = Alignment.CenterVertically,
        // One split pill, not two buttons that happen to be adjacent -- see `wrap`.
        wrap = false,
    ) {
        // Apply half — snowflake icon plus the preset name. The shared
        // MorphButton: pill when idle, rounded rectangle + primary fill when
        // this preset is the applied one. With expansion animation.
        val applySource = remember { MutableInteractionSource() }
        MorphButton(
            onClick = { onStart() },
            onClickHaptic = { haptics?.click() },
            active = active,
            interactionSource = applySource,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            shapeForCorner = leftShapeForCorner,
            pillCornerPercent = 50f,
            morphedCornerPercent = MorphedCornerPercent,
            minHeight = 0.dp,
            groupWeight = 1f,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Delete nub — inner (left) corners match the gap, outer (right) corners
        // are pill-rounded; same MorphButton as the Apply half, just mirrored
        // corners and error colours while armed. With expansion animation.
        val deleteSource = remember { MutableInteractionSource() }
        MorphButton(
            onClick = {
                haptics?.tick()
                if (confirm.armed) onDelete() else confirm.arm()
            },
            interactionSource = deleteSource,
            containerColor = if (confirm.armed) MaterialTheme.colorScheme.error else buttonContainer(),
            contentColor = if (confirm.armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = 14.dp),
            shapeForCorner = rightShapeForCorner,
            pillCornerPercent = 50f,
            morphedCornerPercent = MorphedCornerPercent,
            minHeight = 0.dp,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = if (confirm.armed) "Confirm delete $name" else "Delete $name",
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// --- Charge limits --------------------------------------------------------

/**
 * Two-segment split pill for the charge-limit control, styled like the climate
 * presets: wide left half shows the current value and hosts the inline slider;
 * narrow right half ("Set ⚡") sends the command. Morphs from pill to rounded
 * rectangle when pressed, identical motion to [PresetPill].
 */
@Composable
internal fun ChargeLimitPill(
    label: String,
    limit: Int,
    pending: Boolean,
    enabled: Boolean,
    icon: ImageVector = Icons.Filled.Bolt,
    onValueChange: (Int) -> Unit,
    onApply: () -> Unit,
) {
    val haptics = LocalHaptics.current
    // Same split-pill geometry as the preset pill above, and the same reason there is no
    // measured row height here any more -- see that one's note.
    val leftShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        splitPillShapes(morph, cp).first
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        splitPillShapes(morph, cp).second
    }

    Column(Modifier.fillMaxWidth()) {
        // Same group conversion as PresetPill above -- see its note.
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            spacing = 3.dp,
            wrap = false,
        ) {
            // Left half — label. Tapping bumps the limit up by one step, wrapping
            // back to 50% after 100%, for quick keyboard-free adjustment. With expansion.
            val incrementSource = remember { MutableInteractionSource() }
            MorphButton(
                onClick = { onValueChange(if (limit >= 100) 50 else limit + 10) },
                onClickHaptic = { haptics?.tick() },
                enabled = enabled,
                interactionSource = incrementSource,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                shapeForCorner = leftShapeForCorner,
                pillCornerPercent = 50f,
                morphedCornerPercent = MorphedCornerPercent,
                minHeight = 0.dp,
                // Both the current value and what tapping actually does (bump
                // by 10%, wrapping at 100%) were purely visual -- TalkBack
                // announced only the label text with no indication this half
                // was itself a stepper, distinct from "Set" on the right.
                groupWeight = 1f,
                modifier = Modifier.fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$label, $limit percent"
                        onClick(label = "Increase by 10 percent") {
                            onValueChange(if (limit >= 100) 50 else limit + 10)
                            true
                        }
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    RollingNumber(
                        text = "$limit%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Right half — "Set" nub. Inner (left) corners match the gap; outer
            // (right) are pill-rounded. Active while the command is in flight.
            // With expansion animation.
            val applySource = remember { MutableInteractionSource() }
            MorphButton(
                onClick = { onApply() },
                onClickHaptic = { haptics?.heavy() },
                enabled = enabled && !pending,
                active = pending,
                interactionSource = applySource,
                contentPadding = PaddingValues(horizontal = 18.dp),
                shapeForCorner = rightShapeForCorner,
                pillCornerPercent = 50f,
                morphedCornerPercent = MorphedCornerPercent,
                minHeight = 0.dp,
                // The pending spinner must not fade with the disabled content
                // (Surface didn't dim it before), so pin the full tone.
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxHeight(),
            ) {
                if (pending) {
                    LoadingIndicator(Modifier.size(18.dp))
                } else {
                    Text("Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedSlider(
            value = limit.toFloat(),
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            valueRange = CHARGE_LIMIT_RANGE.first.toFloat()..CHARGE_LIMIT_RANGE.last.toFloat(),
            steps = 4,
        )
        Spacer(Modifier.height(6.dp))
    }
}
