package com.bloo.bluelink.ui

/** The climate pebble's controls section: [ClimateControlsSection]. */

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.degLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.AnimatedSlider
import com.bloo.uicommon.AnimatedValue
import com.bloo.uicommon.tempColor

/**
 * The editable state behind the climate controls -- temperature, duration, defrost, wheel heat and the four seats -- in one
 * holder, so the pebble and the preset editor wire the same eight values once instead of each carrying their own copy.
 */
@Stable
internal class ClimateFormState(initial: ClimateRequest? = null) {
    var tempF by mutableIntStateOf(initial?.tempF ?: DEFAULT_CLIMATE_TEMP_F)
    var duration by mutableIntStateOf(initial?.durationMinutes ?: DEFAULT_CLIMATE_DURATION_MIN)
    var defrost by mutableStateOf(initial?.defrost ?: false)
    var steeringHeat by mutableStateOf(initial?.steeringWheelHeat ?: WheelHeatLevel.OFF)
    var driver by mutableStateOf(initial?.seatFrontLeft ?: SeatLevel.OFF)
    var passenger by mutableStateOf(initial?.seatFrontRight ?: SeatLevel.OFF)
    var rearLeft by mutableStateOf(initial?.seatRearLeft ?: SeatLevel.OFF)
    var rearRight by mutableStateOf(initial?.seatRearRight ?: SeatLevel.OFF)

    /** Copies [r] into the form (restoring saved settings, applying a preset). */
    fun load(r: ClimateRequest) {
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steeringWheelHeat
        driver = r.seatFrontLeft
        passenger = r.seatFrontRight
        rearLeft = r.seatRearLeft
        rearRight = r.seatRearRight
    }

    /** What the form says right now, as a request. */
    fun toRequest() = ClimateRequest(
        tempF = tempF,
        defrost = defrost,
        durationMinutes = duration,
        steeringWheelHeat = steeringHeat,
        seatFrontLeft = driver,
        seatFrontRight = passenger,
        seatRearLeft = rearLeft,
        seatRearRight = rearRight,
    )
}

/** A [ClimateFormState] that lives as long as [key] stays the same, starting from [initial]. */
@Composable
internal fun rememberClimateForm(key: Any?, initial: ClimateRequest? = null): ClimateFormState =
    remember(key) { ClimateFormState(initial) }

/** The climate controls wired to a [form]: every slider and switch reads and writes it. */
@Composable
internal fun ClimateControlsSection(
    form: ClimateFormState,
    locked: Boolean,
    fahrenheit: Boolean,
    seats: SeatConfig,
    showSeats: Boolean,
    onSaveAsPreset: (() -> Unit)?,
) = ClimateControlsSection(
    locked = locked,
    fahrenheit = fahrenheit,
    tempF = form.tempF, onTempF = { form.tempF = it },
    duration = form.duration, onDuration = { form.duration = it },
    defrost = form.defrost, onDefrost = { form.defrost = it },
    steeringHeat = form.steeringHeat, onSteeringHeat = { form.steeringHeat = it },
    seats = seats,
    showSeats = showSeats,
    driver = form.driver, onDriver = { form.driver = it },
    passenger = form.passenger, onPassenger = { form.passenger = it },
    rearLeft = form.rearLeft, onRearLeft = { form.rearLeft = it },
    rearRight = form.rearRight, onRearRight = { form.rearRight = it },
    onSaveAsPreset = onSaveAsPreset,
)

/**
 * The climate pebble's controls -- temperature, run time, defrost, steering-wheel and seat heat,
 * and Save as preset -- peeled out of [ClimatePebble].
 */
@Composable
internal fun ClimateControlsSection(
    locked: Boolean,
    fahrenheit: Boolean,
    tempF: Int,
    onTempF: (Int) -> Unit,
    duration: Int,
    onDuration: (Int) -> Unit,
    defrost: Boolean,
    onDefrost: (Boolean) -> Unit,
    steeringHeat: WheelHeatLevel,
    onSteeringHeat: (WheelHeatLevel) -> Unit,
    seats: SeatConfig,
    showSeats: Boolean,
    driver: SeatLevel,
    onDriver: (SeatLevel) -> Unit,
    passenger: SeatLevel,
    onPassenger: (SeatLevel) -> Unit,
    rearLeft: SeatLevel,
    onRearLeft: (SeatLevel) -> Unit,
    rearRight: SeatLevel,
    onRearRight: (SeatLevel) -> Unit,
    onSaveAsPreset: (() -> Unit)?,
) {
    LockedControls(locked = locked, message = "Stop climate to change settings.") {
        // No "Controls" heading: the pebble is already called Climate, so a heading that repeats it
        // was a word with no meaning.

        // Show the set temperature when climate is running, with an animated entrance.
        ExpandVisibility(locked) {
            Row(Modifier.fillMaxWidth().padding(bottom = GapRow), horizontalArrangement = Arrangement.SpaceBetween) {
                BodySmallText("Set temperature")
                // color resolved explicitly to onSurface -- same fix, same reason as the update
                // pebble's own AnimatedValue calls: BasicText (which this renders through) doesn't
                // fall back to LocalContentColor the way a plain Text() does, so this rendered
                // unreadably dark instead of standing out against the muted label beside it -- the
                // value, not the label, is the important half of this row.
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

        // The reference layout: a big temperature and a duration, each with its own −/+, then the feature tiles, then the seats
        // they open into. Every control writes the same form the presets save, so a preset carries exactly what is shown here.
        val tempColor = com.bloo.uicommon.tempColor(tempF, CLIMATE_TEMP_RANGE_F.first.toFloat(), CLIMATE_TEMP_RANGE_F.last.toFloat())
        ClimateStepper(
            value = degLabel(tempF.toString(), fahrenheit),
            caption = "Temperature",
            big = true,
            canDecrease = tempF > CLIMATE_TEMP_RANGE_F.first,
            canIncrease = tempF < CLIMATE_TEMP_RANGE_F.last,
            tint = tempColor,
            onDecrease = { onTempF(stepTempF(tempF, -1, fahrenheit)) },
            onIncrease = { onTempF(stepTempF(tempF, +1, fahrenheit)) },
        )
        ClimateStepper(
            value = "$duration min",
            caption = "Duration",
            big = false,
            canDecrease = duration > CLIMATE_EXTENDED_DURATION_RANGE.first,
            canIncrease = duration < CLIMATE_EXTENDED_DURATION_RANGE.last,
            tint = MaterialTheme.colorScheme.onSurface,
            onDecrease = { onDuration(duration - 1) },
            onIncrease = { onDuration(duration + 1) },
        )
        // Beyond one command's own cap the run is chained into follow-up commands (see AppViewModel.startClimate).
        ExpandVisibility(duration > CLIMATE_DURATION_RANGE.last, from = Alignment.Bottom) {
            BodySmallText("Sent as ${climateChunksLabel(duration)}, continued automatically")
        }

        var seatsOpen by remember { mutableStateOf(false) }
        val hasSeats = showSeats && (seats.driverHeat || seats.driverCool || seats.passHeat || seats.passCool ||
            seats.rearLeftHeat || seats.rearLeftCool || seats.rearRightHeat || seats.rearRightCool)
        val tiles = buildList {
            add(ClimateTile("Defrost", Icons.Filled.Air, active = defrost, detail = if (defrost) "On" else "Off") { onDefrost(!defrost) })
            if (seats.steeringWheel) {
                add(ClimateTile("Steering wheel", Icons.Filled.Adjust, active = steeringHeat.isOn, detail = steeringHeat.label) {
                    // Off, then Low, then High, then Off again: one tap per step, the way the car's own button steps.
                    onSteeringHeat(WheelHeatLevel.entries[(steeringHeat.ordinal + 1) % WheelHeatLevel.entries.size])
                })
            }
            if (hasSeats) {
                add(ClimateTile("Seat temperatures", Icons.Filled.Thermostat, active = seatsOpen, detail = null) { seatsOpen = !seatsOpen })
            }
        }
        ClimateTileGrid(tiles)

        if (hasSeats) {
            ExpandVisibility(seatsOpen, from = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                    if (seats.driverHeat || seats.driverCool) {
                        SeatCard("Driver", driver, seats.driverHeat, seats.driverCool) { onDriver(it) }
                    }
                    if (seats.passHeat || seats.passCool) {
                        SeatCard("Passenger", passenger, seats.passHeat, seats.passCool) { onPassenger(it) }
                    }
                    if (seats.rearLeftHeat || seats.rearLeftCool) {
                        SeatCard("Rear left", rearLeft, seats.rearLeftHeat, seats.rearLeftCool) { onRearLeft(it) }
                    }
                    if (seats.rearRightHeat || seats.rearRightCool) {
                        SeatCard("Rear right", rearRight, seats.rearRightHeat, seats.rearRightCool) { onRearRight(it) }
                    }
                }
            }
        }

        // No "Save" heading either: the button below already says what it does.
        // The editor for a saved preset passes no save: it already has its own Save.
        if (onSaveAsPreset != null) {
            MorphTextButton(
                text = "Save as preset", onClick = onSaveAsPreset, modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Confirm,
            )
        }
    }
}
