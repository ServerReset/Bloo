package com.bloo.bluelink.wear

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.AppIcons

/**
 * The chooser for one quarter of the face. The controls this car can do are grouped into pebbles by what they act on (doors,
 * climate, charging, lights and horn, the car), each a row with its glyph, its name and what it does; the one the quarter holds
 * now is lit, and "Leave empty" is last. A tap picks it and returns; "Climate preset..." goes on to the list of presets.
 */
@Composable
internal fun WearControlPicker(
    position: String,
    current: String,
    car: VehicleSnapshot,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    // A quarter holding a climate preset (or smart climate) lights the "Climate preset..." entry that leads to them.
    val selected = if (WearFaceControls.isClimatePick(current)) WearFaceControls.CLIMATE_PICK else current
    val groups = remember(car) { WearFaceControls.choicesFor(car).groupBy { it.group } }
    // Swiping right (or the back button) returns to the editor it was opened from.
    WearBackable(onBack) {
        WearListScreen {
            item(key = "title") {
                WearCenteredText(position, style = MaterialTheme.typography.titleMedium)
            }
            item(key = "hint") { WearCaption("What should this quarter do?", small = true) }
            FaceGroup.entries.forEach { group ->
                val controls = groups[group].orEmpty()
                if (controls.isEmpty()) return@forEach
                item(key = group.name) {
                    WearPebble(title = group.title, icon = group.icon) {
                        controls.forEach { control ->
                            WearListRow(
                                title = control.choice,
                                subtitle = control.hint.ifBlank { null },
                                icon = control.icon(car),
                                active = control.key == selected,
                            ) { onPick(control.key) }
                        }
                    }
                }
            }
            item(key = "empty") {
                WearPebble(title = "Nothing", icon = AppIcons.Close) {
                    WearListRow(
                        title = "Leave empty",
                        subtitle = "A quiet quarter",
                        icon = AppIcons.Close,
                        active = selected == WearFaceControls.NONE,
                    ) { onPick(WearFaceControls.NONE) }
                }
            }
        }
    }
}
