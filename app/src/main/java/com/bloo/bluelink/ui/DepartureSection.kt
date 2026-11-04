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
import com.bloo.bluelink.data.DepartureSchedule
import com.bloo.bluelink.data.DepartureSlot
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatMinuteOfDay
import com.bloo.bluelink.data.suggestDeparture
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import java.time.LocalDateTime

/** The car's two departures: a time it should be ready, switched on or off. Smart climate sets what it should be when it starts. */
@Composable
internal fun DepartureSection(v: Vehicle, state: UiState, vm: AppViewModel) {
    val schedule = state.departures[v.vin] ?: DepartureSchedule()
    val today = LocalDateTime.now().dayOfWeek.value - 1
    val suggested = suggestDeparture(state.startHistories[v.vin].orEmpty(), today)
    val startedToday = LocalDateTime.now().toLocalDate().toEpochDay()
    val started = state.departureStarted[v.vin].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SectionLabel("Departures")
        schedule.slots.forEachIndexed { i, slot ->
            if (slot.enabled && started.getOrElse(i) { -1L } == startedToday) {
                RevealMutedText("Departure ${i + 1} started today: the car is warming up.")
            }
            DepartureTimeField(
                index = i,
                slot = slot,
                suggestion = if (i == 0) suggested else null,
                onChange = { vm.setDeparture(v, i, it) },
            )
        }
    }
}

/** One departure: a switch, and the time it should be ready, in 15-minute steps. */
@Composable
private fun DepartureTimeField(index: Int, slot: DepartureSlot, suggestion: Int?, onChange: (DepartureSlot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        ToggleRow(
            "Departure ${index + 1}",
            slot.enabled,
            description = "Starts the car $DEPARTURE_LEAD_MIN minutes before this time, every day.",
        ) { onChange(slot.copy(enabled = it)) }
        if (slot.enabled) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                MorphTextButton("−15", onClick = { onChange(slot.copy(minuteOfDay = Math.floorMod(slot.minuteOfDay - 15, 1440))) })
                RevealText(formatMinuteOfDay(slot.minuteOfDay), style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold)
                MorphTextButton("+15", onClick = { onChange(slot.copy(minuteOfDay = Math.floorMod(slot.minuteOfDay + 15, 1440))) })
            }
            // What the person's own starts suggest for today, when they are regular enough to predict.
            suggestion?.takeIf { it != slot.minuteOfDay }?.let { s ->
                RevealMutedText("Your usual starts suggest ${formatMinuteOfDay(s)} today.")
                MorphTextButton("Use ${formatMinuteOfDay(s)}", onClick = { onChange(slot.copy(minuteOfDay = s)) })
            }
        }
    }
}
