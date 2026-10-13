package com.bloo.bluelink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.autolock.AutoLockNotification
import com.bloo.bluelink.autolock.DetectionState
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.isBackgroundUnrestricted
import com.bloo.bluelink.data.requestBackgroundUnrestricted
import com.bloo.bluelink.data.setAutoLockConfig
import kotlin.math.roundToInt

/** The AutoLock countdown's own bounds: low to start (see the Gen5W note), and no long waits. */
private const val GraceMinSeconds = 5
private const val GraceMaxSeconds = 45

/** AutoLock's per-car Settings section, inside [CarSettingsCard]. */
@Composable
internal fun AutoLockSettingsGroup(v: Vehicle, vm: AppViewModel, gen5w: Boolean = false) {
    val context = LocalContext.current
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    LaunchedEffect(v.vin) { config = vm.autoLockConfig(v.vin) }
    val current = config ?: return

    // The background exemption is granted in a system screen, so re-read it every time we come back
    // to the settings instead of only once -- otherwise the "allow background" step never clears.
    var recheck by remember(v.vin) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) recheck++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val backgroundOk = remember(recheck) {
        runCatching { LiveCharge.isBackgroundUnrestricted(context) }.getOrDefault(true)
    }

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

    val bluetoothConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showDevicePicker = true }
    // Motion is a soft confirmation (it promotes the evaluation early when it fires, and is simply
    // skipped when it doesn't -- see AutoLockController), so ACTIVITY_RECOGNITION is requested
    // alongside the other permissions but is not gating.
    val corePermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
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

    SettingsGroup("AutoLock") {
        BodySmallText(
            "Locks ${v.name} when you walk away, using the car's Bluetooth to tell you've left.",
        )
        ToggleRow(
            "Enabled",
            current.enabled,
            description = if (current.enabled) {
                "On: Bloo locks ${v.name} when you walk away (Bluetooth disconnect + motion)."
            } else {
                "Off: Bloo never locks ${v.name} by itself. Turn on to set it up."
            },
            onChange = { onEnabledChanged(it) },
        )

        // AutoLock's notification controls (its alerts, the watcher notification, and the
        // permission and reliability prompts it depends on) are in Settings -> Notifications, next
        // to every other reason an alert might or might not arrive.

        if (current.enabled) {
            StatusRow("Car Bluetooth device", current.deviceName ?: "Not set")
            SafeMorphTextButton(
                "Choose device",
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                        showDevicePicker = true
                    } else {
                        bluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
            )

            // The setup flow, in order: let Bloo keep running in the background, THEN quiet the
            // watcher notification. Both are what actually lets AutoLock fire after you walk away --
            // a Doze-suspended process never reacts to the disconnect, and the watcher notification
            // is the only sign it is running.
            if (!backgroundOk) {
                BodySmallText(
                    "AutoLock has to keep running while Bloo is in the background, or it can't lock " +
                        "the car after you walk away. Allow background activity next.",
                )
                SafeMorphTextButton(
                    "Allow background activity",
                    onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                )
            } else {
                BodySmallText(
                    "Bloo can run in the background. AutoLock keeps a quiet watcher notification " +
                        "while it's on -- hide it if you'd rather not see it.",
                )
                SafeMorphTextButton(
                    "Hide watcher notification",
                    onClick = {
                        context.tryStart(
                            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, AutoLockNotification.CHANNEL_ID)
                            },
                        )
                    },
                    icon = Icons.Filled.NotificationsOff,
                )
            }

            StepRow("Grace period", "${current.graceSeconds}s")
            AnimatedSlider(
                value = current.graceSeconds.coerceIn(GraceMinSeconds, GraceMaxSeconds).toFloat(),
                onValueChange = { updateLocal(current.copy(graceSeconds = it.roundToInt())) },
                onValueSettled = { update(current.copy(graceSeconds = it.roundToInt())) },
                valueRange = GraceMinSeconds.toFloat()..GraceMaxSeconds.toFloat(),
                // 5s steps across the (short) range, so every stop is a distinct, low delay.
                steps = (GraceMaxSeconds - GraceMinSeconds) / 5 - 1,
            )
            BodySmallText(
                "Waits ${current.graceSeconds}s after you walk away, then locks. \"Lock now\" skips " +
                    "the countdown.",
            )
            if (gen5w) {
                BodySmallText(
                    "This car's Gen5W head unit is older and slower to lock once Bloo asks, so the " +
                        "car adds its own delay on top of this. Keep the grace period low to start; " +
                        "raise it only if it locks before you're away.",
                )
            }

            ToggleRow(
                "Dry run (testing mode)",
                current.dryRun,
                description = if (current.dryRun) {
                    "On: AutoLock runs the whole flow but never locks the car. Turn off once you trust it."
                } else {
                    "Off: AutoLock sends the real lock command."
                },
            ) { update(current.copy(dryRun = it)) }

            // Trying it out lives with the settings it exercises.
            SectionDivider(alpha = 0.5f)
            SafeMorphTextButton(
                "Simulate leaving",
                enabled = current.isUsable,
                onClick = { vm.simulateAutoLockLeaving(v) },
            )
            if (!current.isUsable) {
                BodySmallText(
                    "Choose the car's Bluetooth device above to try this.",
                )
            }

            // The shared StatusChip (Widgets.kt), not a one-off Surface: the Updates card elsewhere
            // in Settings had hand-rolled the same live-status pill with a different fill and
            // padding, and a detection state and an update state are the same kind of readout in
            // the same kind of card.
            val evalStates by vm.autoLockState.collectAsStateWithLifecycle()
            evalStates[v.vin]?.takeIf { it.detection != DetectionState.IDLE }?.let { s ->
                StatusChip(
                    text = "${s.detection.label()}" +
                        if (s.detection == DetectionState.GRACE) " · ${s.graceRemaining}s" else "",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showDevicePicker) {
        val devices = remember { vm.pairedBluetoothDevices() }
        // GlassAlertDialog (Ambient.kt), like every other dialog in the app. Nothing about picking
        // a Bluetooth device needs its own dialog treatment.
        GlassAlertDialog(
            onDismissRequest = { showDevicePicker = false },
            icon = Icons.Filled.Bluetooth,
            title = "Choose the car's Bluetooth device",
            text = {
                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices. Pair your car in Android's Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // The paired-device list is a handful of rows, not a feed.
                    devices.forEach { device ->
                        MorphButton(
                            onClick = {
                                update(current.copy(deviceAddress = device.address, deviceName = device.name))
                                showDevicePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = GapGroup, vertical = GapRow),
                        ) {
                            // name/address pair, the same label-over-caption rhythm every settings
                            // row in the app uses.
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                BodyMediumText(device.name)
                                BodySmallText(device.address)
                            }
                        }
                    }
                }
            },
            buttons = {
                // MorphTextButton, not M3's TextButton: the only bare Material button left in the
                // app's own UI, and it rendered with stock M3 styling -- no morph, no haptic, none
                // of the app's own button language -- next to dialogs whose dismiss action is
                // always a MorphTextButton.
                MorphTextButton(
                    "Close",
                    onClick = { showDevicePicker = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

private fun DetectionState.label(): String = when (this) {
    DetectionState.IDLE -> "Idle"
    DetectionState.CONFIRMING -> "Confirming you left"
    DetectionState.GRACE -> "Locking soon"
    DetectionState.VERIFYING -> "Checking the car"
    DetectionState.LOCKING -> "Locking"
    DetectionState.LOCKED -> "Locked"
    DetectionState.SKIPPED -> "Skipped"
    DetectionState.ABORTED -> "Cancelled"
    DetectionState.ERROR -> "Error"
}
