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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.autolock.DetectionState
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Vehicle
import kotlin.math.roundToInt

/**
 * AutoLock's per-car Settings section, inside [CarSettingsCard]. Ported feature from the
 * i5-AutoLock reference implementation by Vel-San (github.com/Vel-San/i5-AutoLock): locks
 * this car automatically when the phone disconnects from its paired Bluetooth device, once
 * Activity Recognition confirms you're actually walking away -- see app/.../autolock/ for
 * the detection + policy machinery this configures.
 *
 * Collapsed to one [SettingsGroup] plus a "Test" group: Bluetooth is the only trigger (no
 * geofence option -- that whole confirmation path, and the location permissions it needed,
 * were cut entirely), motion confirmation is mandatory and not a toggle, and the remaining
 * controls are Enable, the paired device picker, grace period, dry-run, and a way to test it.
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

    val bluetoothConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showDevicePicker = true }
    // Motion confirmation is mandatory (not a toggle), so ACTIVITY_RECOGNITION is requested
    // right alongside the other permissions AutoLock needs the moment it's turned on.
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
        Text(
            "Automatically locks ${v.name} once your phone disconnects from its paired " +
                "Bluetooth and Activity Recognition confirms you're walking away. Starts " +
                "disabled and in dry-run mode -- it decides what it would do but never sends " +
                "a real lock command until you turn that off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow("Enabled", current.enabled, onChange = ::onEnabledChanged)

        if (current.enabled && !LiveCharge.isBackgroundUnrestricted(context)) {
            SettingsCaption("Won't reliably trigger with the app closed?", bottomGap = SettingsGapHairline)
            MorphTextButton(
                text = "Allow background activity",
                onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                contentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Filled.Warning,
            )
        }

        // Locking always waits for a walking confirmation, so denying this permission
        // means every evaluation times out and skips -- surfaced here since it would
        // otherwise look like AutoLock is simply broken, with no toggle left to point at.
        if (current.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !granted(Manifest.permission.ACTIVITY_RECOGNITION)
        ) {
            SettingsCaption("Won't lock without motion access", bottomGap = SettingsGapHairline)
            MorphTextButton(
                text = "Grant Physical activity permission",
                onClick = { corePermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)) },
                contentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Filled.Warning,
            )
        }

        // Exact alarms are what make the lock deadline honest: without this access the OS may
        // delay the fallback's alarm by minutes, and a car that locks "eventually" is worse
        // than one that says so up front. Android 12+ gates it behind a special access screen,
        // so the only way to get it is to send the user there.
        val exactAlarmsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(android.app.AlarmManager::class.java))?.canScheduleExactAlarms() == true
        if (current.enabled && !exactAlarmsOk) {
            SettingsCaption("Locking may be delayed", bottomGap = SettingsGapHairline)
            MorphTextButton(
                text = "Allow Alarms & reminders",
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
                contentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Filled.Warning,
            )
        }

        if (current.enabled) {
            StatusRow("Car Bluetooth device", current.deviceName ?: "Not set")
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

            StepRow("Grace period", "${current.graceSeconds}s")
            AnimatedSlider(
                value = current.graceSeconds.toFloat(),
                onValueChange = { updateLocal(current.copy(graceSeconds = it.roundToInt())) },
                onValueSettled = { update(current.copy(graceSeconds = it.roundToInt())) },
                valueRange = 5f..120f,
                steps = 22,
            )
            Text(
                "Wait this long before locking — taps \"Lock now\" to skip the countdown.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ToggleRow(
                "Dry run (testing mode)",
                current.dryRun,
                description = "Runs the full flow and logs what it would do, but never sends the real lock command. Defaults on; turn off once you trust it.",
            ) { update(current.copy(dryRun = it)) }
        }
    }

    if (current.enabled) {
        SettingsGroup("Test") {
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
                Text(
                    "Choose the car's Bluetooth device above to try this.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The shared StatusChip (Widgets.kt), not a one-off Surface: the Updates
            // card elsewhere in Settings had hand-rolled the same live-status pill
            // with a different fill and padding, and a detection state and an update
            // state are the same kind of readout in the same kind of card.
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
    // The i5-AutoLock attribution that used to sit here (a Surface + Text,
    // shown only while AutoLock is enabled) moved to a single app-wide Credits
    // card -- SettingsScreen.kt's own "Credits" SettingsCard -- alongside every
    // other third-party project/API this app draws on, rather than one feature's
    // settings section being the only place any of them were ever acknowledged.

    if (showDevicePicker) {
        val devices = remember { vm.pairedBluetoothDevices() }
        // GlassAlertDialog (Ambient.kt), like every other dialog in the app. This was
        // the last raw M3 AlertDialog left: a tonal M3 card with M3's own title/text
        // slot split and 28dp-but-not-glass chrome, popping up two rows below a
        // Settings screen where the PIN, preset, rename, palette, OTP, troubleshoot
        // and Drive-sync dialogs all share one frosted shell. Nothing about picking a
        // Bluetooth device needs its own dialog treatment.
        GlassAlertDialog(
            onDismissRequest = { showDevicePicker = false },
            icon = Icons.Filled.Bluetooth,
            title = "Choose the car's Bluetooth device",
            text = {
                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair with your car's head unit " +
                            "first from Android's Bluetooth settings, then come back here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // A plain Column, not the LazyColumn + fixed 56dp-per-row height this
                    // used to compute: the shell's own `text` slot already scrolls (capped
                    // at 360dp), so a second vertical scroller nested inside it meant a
                    // short list padded to a fixed height and a long one scrolling inside
                    // a scroller. The paired-device list is a handful of rows, not a feed.
                    devices.forEach { device ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    update(current.copy(deviceAddress = device.address, deviceName = device.name))
                                    showDevicePicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            // M3's ListItem drew its own surface fill and its own type scale
                            // inside the dialog; the name/address pair is the same
                            // label-over-caption rhythm every settings row in the app uses.
                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                device.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            buttons = {
                // MorphTextButton, not M3's TextButton: the only bare Material button left in
                // the app's own UI, and it rendered with stock M3 styling -- no morph, no
                // haptic, none of the app's own button language -- next to dialogs whose
                // dismiss action is always a MorphTextButton. Full width, like every other
                // GlassAlertDialog's own dismiss action (the shell stacks its buttons).
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
