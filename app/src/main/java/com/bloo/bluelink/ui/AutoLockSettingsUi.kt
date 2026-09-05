@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.core.content.ContextCompat
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.autolock.DetectionState
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Vehicle
import kotlin.math.roundToInt

/**
 * AutoLock's per-car Settings section, inside [CarSettingsCard]. Ported feature from the
 * i5-AutoLock reference implementation by Vel-San (github.com/Vel-San/i5-AutoLock): locks
 * this car automatically when the phone disconnects from its paired Bluetooth device, after
 * an optional walking/geofence confirmation and a cancellable grace period -- see
 * app/.../autolock/ for the detection + policy machinery this configures.
 *
 * Laid out as four clearly separated groups (Trigger, Confirm before locking, Safety, Test)
 * with icons and improved typography to prevent the flat undifferentiated-wall problem of
 * twelve related rows. Each toggle that turns on a signal this app has no permission for yet
 * actively prompts for it right there, instead of silently flipping a setting whose underlying
 * detector will just no-op until the user happens to find their way to Android's own
 * permission settings.
 */
@Composable
internal fun AutoLockSettingsGroup(v: Vehicle, vm: AppViewModel) {
    val context = LocalContext.current
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun update(new: AutoLockConfig) {
        config = new
        vm.setAutoLockConfig(v.vin, new)
    }

    fun updateLocal(new: AutoLockConfig) {
        config = new
    }

    var showDevicePicker by remember { mutableStateOf(false) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op either way: GeofenceManager checks for itself before registering. */ }
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { fineGranted ->
        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: ActivityRecognitionManager checks for itself before registering. */ }
    val bluetoothConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showDevicePicker = true }
    val corePermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val corePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* no-op: every dependent feature checks for itself. */ }

    fun onEnabledChanged(value: Boolean) {
        update(current.copy(enabled = value))
        if (value) {
            val missing = corePermissions.filter { !granted(it) }
            if (missing.isNotEmpty()) corePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun onActivityRecognitionChanged(value: Boolean) {
        update(current.copy(useActivityRecognition = value))
        if (value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    fun onGeofenceChanged(value: Boolean) {
        update(current.copy(useGeofence = value))
        if (value) {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    SettingsGroup("AutoLock") {
        // Prominent intro explaining the feature
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = SettingsGapRow),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "Automatically locks ${v.name} after you leave, detected from your phone " +
                "disconnecting from its paired Bluetooth. Starts disabled and in dry-run mode " +
                "— it decides what it would do but never sends a real lock command until you " +
                "turn that off. Perfect for peace of mind without the risk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }

        ToggleRow("Enabled", current.enabled, onChange = ::onEnabledChanged)

        // Background optimization prompt (only shown when disabled)
        if (current.enabled && !LiveCharge.isBackgroundUnrestricted(context)) {
            Spacer(Modifier.height(SettingsGapHairline))
            SettingsCaption("Won't reliably trigger with the app closed?", bottomGap = SettingsGapHairline)
            MorphTextButton(
                text = "Allow background activity",
                onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                contentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Filled.Warning,
            )
        }

        // Only show detailed settings when enabled
        if (current.enabled) {
            Spacer(Modifier.height(SettingsGapRow))

            // TRIGGER SECTION
            SettingsSectionHeader("Trigger", Icons.Filled.Bluetooth)
            Spacer(Modifier.height(SettingsGapHairline))

            StatusRow("Car Bluetooth device", current.deviceName ?: "Not set")
            Spacer(Modifier.height(SettingsGapHairline))
            val deviceSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(interactionSource = deviceSource, enabled = true) {
                MorphTextButton(
                    "Choose device",
                    interactionSource = deviceSource,
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                            showDevicePicker = true
                        } else {
                            bluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                )
            }

            Spacer(Modifier.height(SettingsGapRow))
            StepRow("Grace period", "${current.graceSeconds}s")
            Spacer(Modifier.height(SettingsGapHairline))
            AnimatedSlider(
                value = current.graceSeconds.toFloat(),
                onValueChange = { updateLocal(current.copy(graceSeconds = it.roundToInt())) },
                onValueSettled = { update(current.copy(graceSeconds = it.roundToInt())) },
                valueRange = 5f..120f,
                steps = 22,
            )
            Spacer(Modifier.height(SettingsGapHairline))
            Text(
                "Wait this long before locking — taps \"Lock now\" to skip the countdown.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(SettingsGapRow))

            // CONFIRM SECTION
            SettingsSectionHeader("Confirm before locking", Icons.Filled.DirectionsWalk)
            Spacer(Modifier.height(SettingsGapHairline))
            ToggleRow(
                "Confirm with walking",
                current.useActivityRecognition,
                description = "Waits for Activity Recognition to notice you're walking before starting the countdown — cuts false triggers from a brief signal drop.",
                onChange = ::onActivityRecognitionChanged,
            )
            ToggleRow(
                "Confirm with geofence",
                current.useGeofence,
                description = "Waits for you to walk beyond a radius around where the car's parked.",
                onChange = ::onGeofenceChanged,
            )

            // Geofence radius slider (stable height, always present when geofence enabled)
            if (current.useGeofence) {
                Spacer(Modifier.height(SettingsGapHairline))
                StepRow("Geofence radius", "${current.geofenceRadiusMeters} m")
                Spacer(Modifier.height(SettingsGapHairline))
                AnimatedSlider(
                    value = current.geofenceRadiusMeters.toFloat(),
                    onValueChange = { updateLocal(current.copy(geofenceRadiusMeters = it.roundToInt())) },
                    onValueSettled = { update(current.copy(geofenceRadiusMeters = it.roundToInt())) },
                    valueRange = 50f..500f,
                    steps = 8,
                )
                Spacer(Modifier.height(SettingsGapHairline))
                Text(
                    "Lock only after you've walked this far from the parked location.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(SettingsGapRow))

            // SAFETY SECTION
            SettingsSectionHeader("Safety", Icons.Filled.Security)
            Spacer(Modifier.height(SettingsGapHairline))
            ToggleRow(
                "Skip if a door or window is open",
                current.dontLockIfOpen,
                description = "Doesn't lock if any door or window is reported open.",
            ) { update(current.copy(dontLockIfOpen = it)) }
            ToggleRow(
                "Dry run (testing mode)",
                current.dryRun,
                description = "Runs the full flow and logs what it would do, but never sends the real lock command. Defaults on; turn off once you trust it.",
            ) { update(current.copy(dryRun = it)) }

            Spacer(Modifier.height(SettingsGapRow))

            // TEST SECTION
            SettingsSectionHeader("Test", Icons.Filled.LocationOn)
            Spacer(Modifier.height(SettingsGapHairline))
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

            // Live status display -- a filled pill rather than plain text, since this is the
            // one piece of AutoLock's UI that changes while you're looking at it (once a
            // second during the grace countdown) and deserves to read as "live", not as
            // another static caption sitting among all the ones above it.
            val evalStates by vm.autoLockState.collectAsState()
            evalStates[v.vin]?.takeIf { it.detection != DetectionState.IDLE }?.let { s ->
                Spacer(Modifier.height(SettingsGapRow))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        "${s.detection.label()}" + if (s.detection == DetectionState.GRACE) " · ${s.graceRemaining}s" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(SettingsGapRow))

            // ATTRIBUTION SECTION
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SettingsGapHairline),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "AutoLock feature ported from i5-AutoLock by Vel-San. " +
                    "github.com/Vel-San/i5-AutoLock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                )
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

/**
 * A section header with an icon, used to separate AutoLock settings into distinct decision points.
 * No animation: the header is always present, and the content below it either is or isn't shown
 * depending on state.
 */
@Composable
private fun SettingsSectionHeader(
    text: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SettingsGapRow, bottom = SettingsGapHairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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
