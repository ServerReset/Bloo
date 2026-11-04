package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.DEPARTURE_LEAD_MIN
import com.bloo.bluelink.data.DAYS_EVERY
import com.bloo.bluelink.data.DepartureSchedule
import com.bloo.bluelink.data.DepartureSlot
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.formatMinuteOfDay
import com.bloo.bluelink.data.suggestDeparture
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import java.time.LocalDateTime

/** The two departures for a car, in the climate settings: each a time to be ready, on which days, and what the car should be. */
@Composable
internal fun DepartureSection(v: Vehicle, state: UiState, vm: AppViewModel, fahrenheit: Boolean) {
    val schedule = state.departures[v.vin] ?: DepartureSchedule()
    val history = state.startHistories[v.vin].orEmpty()
    val today = LocalDateTime.now().dayOfWeek.value - 1
    val suggested = suggestDeparture(history, today)
    val startedToday = LocalDateTime.now().toLocalDate().toEpochDay()
    val started = state.departureStarted[v.vin].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SectionLabel("Departures")
        schedule.slots.forEachIndexed { i, slot ->
            if (slot.enabled && started.getOrElse(i) { -1L } == startedToday) {
                RevealMutedText("Departure ${i + 1} started today: the car is warming up.")
            }
            DepartureSlotEditor(
                index = i,
                slot = slot,
                suggestion = if (i == 0) suggested else null,
                fahrenheit = fahrenheit,
                onChange = { vm.setDeparture(v, i, it) },
            )
        }
    }
}

@Composable
private fun DepartureSlotEditor(
    index: Int,
    slot: DepartureSlot,
    suggestion: Int?,
    fahrenheit: Boolean,
    onChange: (DepartureSlot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        ToggleRow(
            "Departure ${index + 1}",
            slot.enabled,
            description = "Starts the car $DEPARTURE_LEAD_MIN minutes before the time, on the days chosen.",
        ) { onChange(slot.copy(enabled = it)) }
        if (!slot.enabled) return@Column
        // The time to be ready, in 15-minute steps.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
            MorphTextButton("−15", onClick = { onChange(slot.copy(minuteOfDay = Math.floorMod(slot.minuteOfDay - 15, 1440))) })
            RevealText(formatMinuteOfDay(slot.minuteOfDay), style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
            MorphTextButton("+15", onClick = { onChange(slot.copy(minuteOfDay = Math.floorMod(slot.minuteOfDay + 15, 1440))) })
        }
        // The days, one letter each (Monday first); a bracket marks the days chosen.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GapHairline)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { d, letter ->
                val on = (slot.days and (1 shl d)) != 0
                MorphTextButton(if (on) "[$letter]" else letter, onClick = { onChange(slot.copy(days = slot.days xor (1 shl d))) })
            }
        }
        MorphTextButton(
            "Every day",
            onClick = { onChange(slot.copy(days = DAYS_EVERY)) },
        )
        ToggleRow(
            "Smart target",
            slot.smart,
            description = "Smart climate picks the temperature for the weather that day.",
        ) { onChange(slot.copy(smart = it)) }
        if (!slot.smart) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                MorphTextButton("−1°", onClick = { onChange(slot.copy(targetF = (slot.targetF - 1).coerceAtLeast(50))) })
                RevealText(degLabel(slot.targetF.toString(), fahrenheit), style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
                MorphTextButton("+1°", onClick = { onChange(slot.copy(targetF = (slot.targetF + 1).coerceAtMost(90))) })
            }
        }
        ToggleRow("Seats", slot.seats, description = "Heat, or cool, the front seats.") { onChange(slot.copy(seats = it)) }
        ToggleRow("Steering wheel", slot.wheel, description = "Warm the wheel, where the car has one.") { onChange(slot.copy(wheel = it)) }
        ToggleRow("Defrost", slot.defrost, description = "Clear the glass.") { onChange(slot.copy(defrost = it)) }
        // What the person's own starts suggest for today, when they are regular enough to predict.
        suggestion?.takeIf { it != slot.minuteOfDay }?.let { s ->
            RevealMutedText("Your usual starts suggest ${formatMinuteOfDay(s)} today.")
            MorphTextButton("Use ${formatMinuteOfDay(s)}", onClick = { onChange(slot.copy(minuteOfDay = s)) })
        }
    }
}

/** Who is driving this car: each driver keeps their own learned comfort, so a switch changes what smart climate sets. */
@Composable
internal fun DriverPicker(v: Vehicle, state: UiState, vm: AppViewModel) {
    val active = state.activeDrivers[v.vin] ?: 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
        RevealMutedText("Driving", modifier = Modifier.weight(1f))
        for (d in 0 until com.bloo.bluelink.data.DRIVER_COUNT) {
            MorphTextButton(if (d == active) "[Driver ${d + 1}]" else "Driver ${d + 1}", onClick = { vm.setActiveDriver(v, d) })
        }
    }
}
