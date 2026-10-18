package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
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
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            contentPadding = PaddingValues(vertical = 28.dp, horizontal = 18.dp),
        ) {
            item {
                WearCenteredText(position, style = MaterialTheme.typography.titleSmall)
            }
            WearFaceControls.all.filter { it.available(car) }.forEach { control ->
                item(key = control.key) {
                    WearActionRow(control.choice, control.icon(car), active = current == control.key, switch = true) { onPick(control.key) }
                }
            }
            item(key = WearFaceControls.NONE) {
                WearActionRow("Empty", Icons.Filled.Close, active = current == WearFaceControls.NONE, switch = true) { onPick(WearFaceControls.NONE) }
            }
        }
    }
}
