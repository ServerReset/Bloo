package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.bloo.bluelink.data.DEPARTURE_LEAD_MIN
import com.bloo.bluelink.data.DepartureSchedule
import com.bloo.bluelink.data.DepartureSlot
import com.bloo.bluelink.data.PRE_RUN_NAME_MAX
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatMinuteOfDay
import com.bloo.bluelink.data.suggestDeparture
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import java.time.LocalDateTime

/** The two pre-runs. The one selected is the one being edited. */
private enum class PreRun { First, Second }

/** The slider's stops: Off turns the pre-run being edited off; each name selects that pre-run and turns it on. */
private enum class PreRunStop { Off, First, Second }

/**
 * The car's two pre-runs: each one a time the car should be ready, and a name. The slider pages through them (the one under it is the
 * one being edited) and is also the switch: moving it to Off turns the edited one off. Smart climate sets what each should be when it starts.
 */
@Composable
internal fun DepartureSection(v: Vehicle, state: UiState, vm: AppViewModel) {
    val schedule = state.departures[v.vin] ?: DepartureSchedule()
    val today = LocalDateTime.now().dayOfWeek.value - 1
    val suggested = suggestDeparture(state.startHistories[v.vin].orEmpty(), today)
    val startedToday = LocalDateTime.now().toLocalDate().toEpochDay()
    val started = state.departureStarted[v.vin].orEmpty()
    var selected by rememberSaveable(v.vin) { mutableStateOf(PreRun.First) }
    val index = selected.ordinal
    val slot = schedule.slots[index]
    // The slider shows Off while the edited pre-run is off, and that pre-run's name while it is on.
    val stop = if (slot.enabled) PreRunStop.entries[index + 1] else PreRunStop.Off
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        EnumSegmented(
            entries = PreRunStop.entries,
            selected = stop,
            label = { if (it == PreRunStop.Off) "Off" else schedule.slots[it.ordinal - 1].label(it.ordinal - 1) },
            onSelect = { choice ->
                if (choice == PreRunStop.Off) {
                    vm.setDeparture(v, index, slot.copy(enabled = false))
                } else {
                    val i = choice.ordinal - 1
                    selected = PreRun.entries[i]
                    if (!schedule.slots[i].enabled) vm.setDeparture(v, i, schedule.slots[i].copy(enabled = true))
                }
            },
        )
        if (slot.enabled && started.getOrElse(index) { -1L } == startedToday) {
            RevealMutedText("${slot.label(index)} started today: the car is warming up.")
        }
        PreRunEditor(
            index = index,
            slot = slot,
            suggestion = if (index == 0) suggested else null,
            onChange = { vm.setDeparture(v, index, it) },
        )
    }
}

/** One pre-run being edited: its name, and, while it is on, the time it should be ready in 15-minute steps. */
@Composable
private fun PreRunEditor(index: Int, slot: DepartureSlot, suggestion: Int?, onChange: (DepartureSlot) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        PreRunName(index, slot, onChange)
        if (slot.enabled) {
            RevealMutedText("Starts the car $DEPARTURE_LEAD_MIN minutes before this time, every day.")
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

/**
 * The pre-run's name. It is saved when the field loses focus or Done is pressed, not on every key, so the watch is not sent each
 * letter. A blank name goes back to "Pre-run N".
 */
@Composable
private fun PreRunName(index: Int, slot: DepartureSlot, onChange: (DepartureSlot) -> Unit) {
    var draft by remember(index, slot.name) { mutableStateOf(slot.name) }
    fun commit() {
        val name = DepartureSlot.cleanName(draft)
        if (name != slot.name) onChange(slot.copy(name = name))
    }
    BlooTextField(
        value = draft,
        onValueChange = { draft = it.filter { c -> c != ',' && c != ';' }.take(PRE_RUN_NAME_MAX) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commit() },
        label = { Text("Name") },
        placeholder = { Text("Pre-run ${index + 1}") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
    )
}
