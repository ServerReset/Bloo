@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.autolock.DetectionState
import com.bloo.bluelink.data.Vehicle
import kotlin.math.roundToInt

/**
 * AutoLock's per-car Settings section, inside [CarSettingsCard]. Ported feature from the
 * i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock): locks this car automatically
 * when the phone disconnects from its paired Bluetooth device, after an optional walking/
 * geofence confirmation and a cancellable grace period -- see app/.../autolock/ for the
 * detection + policy machinery this configures.
 */
@Composable
internal fun AutoLockSettingsGroup(v: Vehicle, vm: AppViewModel) {
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return

    fun update(new: AutoLockConfig) {
        config = new
        vm.setAutoLockConfig(v.vin, new)
    }

    var showDevicePicker by remember { mutableStateOf(false) }
    val permissions = remember {
        buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { showDevicePicker = true }

    SettingsGroup("AutoLock") {
        Text(
            "Automatically locks ${v.name} a little while after you leave it, detected " +
                "from your phone disconnecting from its paired Bluetooth (its head unit). " +
                "Off by default, and starts in dry run — it decides what it would do but " +
                "never sends a real lock command until you turn that off below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SettingsGapHairline))
        ToggleRow("Enabled", current.enabled) { update(current.copy(enabled = it)) }

        PopVisible(visible = current.enabled, sizeAnimated = true) {
            Column {
                Spacer(Modifier.height(SettingsGapHairline))
                StatusRow("Car Bluetooth device", current.deviceName ?: "Not set")
                Spacer(Modifier.height(SettingsGapHairline))
                val deviceSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(interactionSource = deviceSource, enabled = true) {
                    MorphTextButton(
                        "Choose device",
                        interactionSource = deviceSource,
                        onClick = {
                            if (vm.hasBluetoothConnectPermission()) showDevicePicker = true
                            else permissionLauncher.launch(permissions)
                        },
                    )
                }

                Spacer(Modifier.height(SettingsGapRow))
                StepRow("Grace period", "${current.graceSeconds}s")
                AnimatedSlider(
                    value = current.graceSeconds.toFloat(),
                    onValueChange = { update(current.copy(graceSeconds = it.roundToInt())) },
                    valueRange = 5f..120f,
                    steps = 22,
                )

                Spacer(Modifier.height(SettingsGapRow))
                ToggleRow(
                    "Confirm with walking",
                    current.useActivityRecognition,
                    description = "Waits for Activity Recognition to notice you're walking before starting the countdown -- cuts false triggers from a brief signal drop.",
                ) { update(current.copy(useActivityRecognition = it)) }
                ToggleRow(
                    "Confirm with geofence",
                    current.useGeofence,
                    description = "Waits for you to walk beyond a radius around where the car's parked before starting the countdown.",
                ) { update(current.copy(useGeofence = it)) }
                PopVisible(visible = current.useGeofence, sizeAnimated = true) {
                    Column {
                        Spacer(Modifier.height(SettingsGapHairline))
                        StepRow("Geofence radius", "${current.geofenceRadiusMeters} m")
                        AnimatedSlider(
                            value = current.geofenceRadiusMeters.toFloat(),
                            onValueChange = { update(current.copy(geofenceRadiusMeters = it.roundToInt())) },
                            valueRange = 50f..500f,
                            steps = 8,
                        )
                    }
                }

                Spacer(Modifier.height(SettingsGapRow))
                ToggleRow(
                    "Skip if a door or window is open",
                    current.dontLockIfOpen,
                ) { update(current.copy(dontLockIfOpen = it)) }
                ToggleRow(
                    "Dry run",
                    current.dryRun,
                    description = "Runs the full detect-and-verify flow and logs what it would do, but never sends the real lock command.",
                ) { update(current.copy(dryRun = it)) }

                Spacer(Modifier.height(SettingsGapRow))
                val simSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(interactionSource = simSource, enabled = current.isUsable) {
                    MorphTextButton(
                        "Simulate leaving",
                        interactionSource = simSource,
                        enabled = current.isUsable,
                        onClick = { vm.simulateAutoLockLeaving(v) },
                    )
                }
                if (!current.isUsable) {
                    Spacer(Modifier.height(SettingsGapHairline))
                    Text(
                        "Choose the car's Bluetooth device above to try this.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val evalStates by vm.autoLockState.collectAsState()
                evalStates[v.vin]?.takeIf { it.detection != DetectionState.IDLE }?.let { s ->
                    Spacer(Modifier.height(SettingsGapHairline))
                    Text(
                        "Status: ${s.detection.label()}" + if (s.detection == DetectionState.GRACE) " (${s.graceRemaining}s)" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showDevicePicker) {
        val devices = remember { vm.pairedBluetoothDevices() }
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Choose the car's Bluetooth device") },
            text = {
                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair with your car's head unit " +
                            "first from Android's Bluetooth settings, then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(Modifier.height((devices.size.coerceAtMost(6) * 56).dp)) {
                        items(devices) { device ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(device.name) },
                                supportingContent = { Text(device.address) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        update(current.copy(deviceAddress = device.address, deviceName = device.name))
                                        showDevicePicker = false
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) { Text("Close") }
            },
        )
    }
}

private fun DetectionState.label(): String = when (this) {
    DetectionState.IDLE -> "Idle"
    DetectionState.ARMED -> "Armed"
    DetectionState.CONFIRMING -> "Confirming you left"
    DetectionState.GRACE -> "Locking soon"
    DetectionState.VERIFYING -> "Checking the car"
    DetectionState.LOCKING -> "Locking"
    DetectionState.LOCKED -> "Locked"
    DetectionState.SKIPPED -> "Skipped"
    DetectionState.ABORTED -> "Cancelled"
    DetectionState.ERROR -> "Error"
}
