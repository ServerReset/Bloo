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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
 *
 * Laid out as four clearly separated groups (Trigger, Confirm before locking, Safety, Test) --
 * a flat list of a dozen unrelated-looking rows read as one big undifferentiated block, and
 * it's genuinely four different decisions: which device counts as "the car" and how long to
 * wait, what should corroborate the disconnect, what to refuse to do automatically, and how to
 * try the whole thing safely before trusting it. Each toggle that turns on a signal this app
 * has no permission for yet actively prompts for it right there, instead of silently flipping
 * a setting whose underlying detector will just no-op until the user happens to find their way
 * to Android's own permission settings.
 */
@Composable
internal fun AutoLockSettingsGroup(v: Vehicle, vm: AppViewModel) {
    val context = LocalContext.current
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Commits to SettingsStore (a DataStore write) -- for toggles and the device picker,
    // where every call IS the one real change the user made.
    fun update(new: AutoLockConfig) {
        config = new
        vm.setAutoLockConfig(v.vin, new)
    }

    // Cheap: only updates the slider's own displayed value, no DataStore write. AnimatedSlider
    // calls onValueChange on every drag tick -- dozens of times for one drag gesture -- so a
    // slider using `update` directly there was writing to disk that often. The two sliders
    // below call this from onValueChange and `update` from onValueSettled instead, matching
    // this file's own documented "sync on commit" pattern (see the Vibrancy/UI-scale sliders).
    fun updateLocal(new: AutoLockConfig) {
        config = new
    }

    var showDevicePicker by remember { mutableStateOf(false) }

    // Background location has its own launcher, requested ALONE and only after foreground
    // (fine) location is already granted -- bundling it into the same request as anything
    // else is what Android 11+ actively guards against; a background-location request
    // riding along with other permissions in one dialog is liable to be silently denied by
    // the system regardless of what the user taps.
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
    ) { /* no-op: every dependent feature (the device list, the notification) checks for
           itself; this dialog is purely to get the prompt in front of the user right when
           they turn AutoLock on, not to gate anything on its result. */ }

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
        Text(
            "Automatically locks ${v.name} a little while after you leave it, detected " +
                "from your phone disconnecting from its paired Bluetooth (its head unit). " +
                "Off by default, and starts in dry run — it decides what it would do but " +
                "never sends a real lock command until you turn that off below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SettingsGapHairline))
        ToggleRow("Enabled", current.enabled, onChange = ::onEnabledChanged)

        // Plain PopVisible, no sizeAnimated: the pebble body around this whole group already
        // animates its own height via StaggeredRevealColumn's animateContentSize (see
        // PebbleShell) whenever its content changes size, including this reveal. A SECOND,
        // independently-sprung height animation here compounded with that outer one -- two
        // springs disagreeing about the same height delta -- and read as the whole card
        // overshooting upward for a frame before settling, on every open. Fade+scale in
        // place is enough; the outer animator is what should own the height change.
        PopVisible(visible = current.enabled) {
            Column {
                Spacer(Modifier.height(SettingsGapRow))
                SectionLabel("Trigger")
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
                AnimatedSlider(
                    value = current.graceSeconds.toFloat(),
                    onValueChange = { updateLocal(current.copy(graceSeconds = it.roundToInt())) },
                    onValueSettled = { update(current.copy(graceSeconds = it.roundToInt())) },
                    valueRange = 5f..120f,
                    steps = 22,
                )

                Spacer(Modifier.height(SettingsGapRow))
                SectionLabel("Confirm before locking")
                Spacer(Modifier.height(SettingsGapHairline))
                ToggleRow(
                    "Confirm with walking",
                    current.useActivityRecognition,
                    description = "Waits for Activity Recognition to notice you're walking before starting the countdown -- cuts false triggers from a brief signal drop.",
                    onChange = ::onActivityRecognitionChanged,
                )
                ToggleRow(
                    "Confirm with geofence",
                    current.useGeofence,
                    description = "Waits for you to walk beyond a radius around where the car's parked before starting the countdown.",
                    onChange = ::onGeofenceChanged,
                )
                PopVisible(visible = current.useGeofence) {
                    Column {
                        Spacer(Modifier.height(SettingsGapHairline))
                        StepRow("Geofence radius", "${current.geofenceRadiusMeters} m")
                        AnimatedSlider(
                            value = current.geofenceRadiusMeters.toFloat(),
                            onValueChange = { updateLocal(current.copy(geofenceRadiusMeters = it.roundToInt())) },
                            onValueSettled = { update(current.copy(geofenceRadiusMeters = it.roundToInt())) },
                            valueRange = 50f..500f,
                            steps = 8,
                        )
                    }
                }

                Spacer(Modifier.height(SettingsGapRow))
                SectionLabel("Safety")
                Spacer(Modifier.height(SettingsGapHairline))
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
                SectionLabel("Test")
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
