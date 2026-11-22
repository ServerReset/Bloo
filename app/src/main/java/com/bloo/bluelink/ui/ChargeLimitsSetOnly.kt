package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.RevealText

/**
 * Sets the AC and DC charge limits on a Hyundai Canada car, in steps of 10 percent. Canadian cars do not report their current limits to
 * Bloo, so the dialog starts at 80 and does not show what the car has now. Setting them is the same PIN command the upstream app sends.
 */
@Composable
internal fun ChargeLimitsSetOnlyDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    var ac by remember(v.vin) { mutableIntStateOf(80) }
    var dc by remember(v.vin) { mutableIntStateOf(80) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = AppIcons.Bolt,
        title = "Charge limits",
        text = {
            RevealText("The car's current limits are not shown for Canadian cars. Set the ones you want.")
            LimitStepper("DC fast charge", dc) { dc = it }
            LimitStepper("AC (home or work)", ac) { ac = it }
        },
        buttons = {
            MorphTextButton(
                text = "Set limits",
                onClick = {
                    vm.setChargeLimits(v, ac, dc)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Confirm,
            )
            MorphTextButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth())
        },
    )
}

/** One limit, 50 to 100 percent in tens, as the same big value and stepped slider the climate controls use. */
@Composable
private fun LimitStepper(label: String, percent: Int, onChange: (Int) -> Unit) {
    ClimateStepper(
        value = "$percent%",
        caption = label,
        big = false,
        current = percent / 10,
        range = 5..10,
        tint = MaterialTheme.colorScheme.onSurface,
        onChange = { onChange(it * 10) },
    )
}
