package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.Vehicle

/**
 * Sets the AC and DC charge limits on a Hyundai Canada car, in steps of 10 percent. Canadian cars do not report their current limits to
 * Bloo, so the dialog starts at 80 and does not show what the car has now. Setting them is the same PIN command the upstream app sends.
 */
@Composable
internal fun ChargeLimitsSetOnlyDialog(vm: AppViewModel, v: Vehicle, onDismiss: () -> Unit) {
    var ac by remember(v.vin) { mutableIntStateOf(80) }
    var dc by remember(v.vin) { mutableIntStateOf(80) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Charge limits", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The car's current limits are not shown for Canadian cars. Set the ones you want.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LimitRow("DC fast charge", dc, onChange = { dc = it })
                LimitRow("AC (home or work)", ac, onChange = { ac = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        vm.setChargeLimits(v, ac, dc)
                        onDismiss()
                    }) { Text("Set limits") }
                }
            }
        }
    }
}

@Composable
private fun LimitRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        OutlinedButton(onClick = { onChange((value - 10).coerceAtLeast(50)) }) { Text("−") }
        Text("$value%", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { onChange((value + 10).coerceAtMost(100)) }) { Text("+") }
    }
}
