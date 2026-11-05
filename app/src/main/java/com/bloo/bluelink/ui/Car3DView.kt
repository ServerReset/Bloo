package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.isPluggedIn
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.ui.car3d.CarBody
import com.bloo.bluelink.ui.car3d.CarPaint
import com.bloo.bluelink.ui.car3d.CarPose
import com.bloo.bluelink.ui.car3d.Car3D
import com.bloo.bluelink.ui.car3d.ClimateFx

/**
 * The car in 3D, from the status Bloo already holds: doors, boot and bonnet follow the car, the charge cable is plugged in while it is,
 * and air streams while climate runs. Drag to look around, pinch to zoom, double-tap to reset. Read-only.
 */
@Composable
internal fun Car3DDialog(v: Vehicle, status: VehicleStatus?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(v.name, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Drag to look around, pinch to zoom, double-tap to reset.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                // The model's licence (CC-BY-NC-4.0) asks for this credit wherever the model is shown.
                Text(
                    "Model: 2024 Hyundai Ioniq 5 N by Ddiaz Design (Sketchfab), CC-BY-NC-4.0. Non-commercial use only.",
                    style = MaterialTheme.typography.labelSmall,
                )
                Car3D(
                    pose = poseFor(status),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    body = CarBody.forModel(v.model),
                    paint = CarPaint.WHITE,
                )
            }
        }
    }
}

/** The 3D car's pose from the status: the open doors, boot and bonnet, the charge, and climate. */
internal fun poseFor(st: VehicleStatus?): CarPose {
    if (st == null) return CarPose()
    val ev = st.evStatus
    val open = st.doorOpen?.openLabels().orEmpty()
    val setTemp = st.airTemp?.value?.toDoubleOrNull()
    val climate = if (st.airCtrlOn != true) null else if ((setTemp ?: 22.0) >= 20) ClimateFx.HEAT else ClimateFx.COOL
    return CarPose(
        frontLeft = "front-left" in open,
        frontRight = "front-right" in open,
        rearLeft = "rear-left" in open,
        rearRight = "rear-right" in open,
        frunk = st.hoodOpen == true,
        trunk = st.trunkOpen == true,
        pluggedIn = ev?.isPluggedIn == true,
        charging = ev?.batteryCharge == true,
        climate = climate,
    )
}
