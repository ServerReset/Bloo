package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The chooser for one quarter of the face: every control this car can do (see [WearFaceControls]), the current
 * one lit, and an empty quarter as the last choice. A tap picks it and returns.
 */
@Composable
internal fun WearControlPicker(
    position: String,
    current: String,
    car: VehicleSnapshot,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
  // Swiping right (or the back button) returns to the editor it was opened from.
  WearBackable(onBack) {
    WearChoiceScreen(
        title = position,
        choices = WearFaceControls.choicesFor(car).map { WearChoice(it.key, it.choice, it.icon(car)) },
        // A quarter holding a climate preset (or smart climate) lights the "Climate preset…" entry that leads to them.
        selected = if (WearFaceControls.isClimatePick(current)) WearFaceControls.CLIMATE_PICK else current,
        onPick = onPick,
        empty = WearChoice(WearFaceControls.NONE, "Empty"),
    )
  }
}
