package com.bloo.bluelink.ui

/**
 * Climate controls: ClimatePebble, SeatControl, seatTint, preset section, PresetPill,
 * ChargeLimitPill -- extracted from Pebbles.kt.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

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
    onSaveAsPreset: () -> Unit,
) {
    LockedControls(locked = locked, message = "Stop climate to change settings") {
        SectionLabel("Controls")

        // Show the set temperature when climate is running, with an animated entrance.
        AnimatedVisibility(
            visible = locked,
            enter = expandEnterSized(),
            exit = expandExitSized(),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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

        val tempRange = CLIMATE_TEMP_RANGE_F.first.toFloat()..CLIMATE_TEMP_RANGE_F.last.toFloat()
        val tempColor = com.bloo.uicommon.tempColor(tempF, tempRange.start, tempRange.endInclusive)
        // The label + value readout is the same in either unit -- only degLabel's suffix (°F/°C)
        // and the slider below differ -- so it's hoisted out of the branch.
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BodySmallText("Temperature")
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
                onValueChange = { onTempF(it.roundToInt()) },
                valueRange = tempRange,
                steps = 19,
                accent = tempColor,
            )
        } else {
            // Celsius: drive the slider in whole °C but keep tempF canonical for the command,
            // converting on each side.
            val tempC = ((tempF - 32) * 5 / 9f).roundToInt()
            AnimatedSlider(
                value = tempC.toFloat(),
                onValueChange = { onTempF((it * 9 / 5f + 32).roundToInt()) },
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
            Spacer(Modifier.width(GapRow))
            // RollingNumber, not StepRow's built-in roll: StepRow's AnimatedContent always slides
            // the same direction regardless of which way the value moved, which reads oddly on a
            // slider you're actively dragging both ways.
            RollingNumber(
                text = "$duration min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        AnimatedSlider(
            value = duration.toFloat(),
            // Extended range: the car itself has no single command past CLIMATE_DURATION_RANGE's
            // 10-minute cap -- a request beyond that is auto-chained into follow-up commands
            // instead (see AppViewModel.startClimate / ClimateExtendWorker), so the slider can go
            // further than any one command actually could.
            onValueChange = { onDuration(it.roundToInt()) },
            valueRange = CLIMATE_EXTENDED_DURATION_RANGE.first.toFloat()..CLIMATE_EXTENDED_DURATION_RANGE.last.toFloat(),
            steps = CLIMATE_EXTENDED_DURATION_RANGE.last - CLIMATE_EXTENDED_DURATION_RANGE.first - 1,
        )
        AnimatedVisibility(
            visible = duration > CLIMATE_DURATION_RANGE.last,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            BodySmallText(
                "Sent as ${climateChunksLabel(duration)}, continued automatically",
            )
        }

        ToggleRow("Defrost", defrost) { onDefrost(it) }
        if (seats.steeringWheel) {
            WheelHeatControl(steeringHeat) { onSteeringHeat(it) }
        }

        if (showSeats) {
            SectionLabel("Seats")
            if (seats.driverHeat || seats.driverCool) {
                SeatControl("Driver seat", driver, seats.driverCool, seats.driverHeat) { onDriver(it) }
            }
            if (seats.passHeat || seats.passCool) {
                SeatControl("Passenger seat", passenger, seats.passCool, seats.passHeat) { onPassenger(it) }
            }
            if (seats.rearLeftHeat || seats.rearLeftCool) {
                SeatControl("Rear left seat", rearLeft, seats.rearLeftCool, seats.rearLeftHeat) { onRearLeft(it) }
            }
            if (seats.rearRightHeat || seats.rearRightCool) {
                SeatControl("Rear right seat", rearRight, seats.rearRightCool, seats.rearRightHeat) { onRearRight(it) }
            }
        }

        SectionLabel("Save")
        SafeMorphTextButton(
            text = "Save as preset",
            onClick = onSaveAsPreset,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
