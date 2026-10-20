package com.bloo.bluelink.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.SafeMorphTextButton
import com.bloo.uicommon.AnimatedSlider

/**
 * The AutoLock countdown's own bounds: 0 (lock the moment the walk-away is confirmed) up to 30, in 5s
 * steps -- 0, 5, 10, 15, 20, 25, 30.
 *
 * Was 5..45. The long end was never useful -- 45s of standing there -- and the short end excluded the
 * people who want no wait at all, which the evaluation handles cleanly: a countdown of 0 simply does
 * not run (see AutoLockController's `for (remaining in graceSeconds downTo 1)`).
 */
private const val GraceMinSeconds = 0
private const val GraceMaxSeconds = 30

/**
 * AutoLock's per-car Settings section, in two shapes.
 *
 * Which shape you get is decided by the APP, not by this car. AutoLock needs four things that are
 * app-wide -- a Bluetooth permission, a notification permission, the background exemption, and the
 * watcher notification dealt with -- and they only have to be asked for once. So:
 *
 *  - **Setup**, on the first car to turn AutoLock on: the four things, in the order they have to
 *    happen, each showing its own state and the action that clears it. Nothing else is in the
 *    section. Not the toggle, not the grace slider, not the dry-run switch -- because an enabled
 *    AutoLock that cannot run is the one state that produces NOTHING at all: no trigger, no
 *    notification, no command, and no line saying why. That is indistinguishable from the feature
 *    being broken, and it is how it gets reported. The device row the user had scrolled past was the
 *    answer every time.
 *  - **Settings**, for every car once the first one is set up.
 */
@Composable
internal fun AutoLockSettingsGroup(v: Vehicle, vm: AppViewModel, gen5w: Boolean = false) {
    val context = LocalContext.current
    var config by remember(v.vin) { mutableStateOf<AutoLockConfig?>(null) }
    // Whether this is the app's FIRST car to have AutoLock set up, which is what decides whether the
    // section is the setup flow or the settings. Read from the store rather than from this car alone,
    // so a second car is never asked for permissions the app already has.
    //
    // Set in the SAME effect as the config, and first: the section renders nothing until `config`
    // lands (see the `?: return` below), so reading this here means the setup flow is never preceded
    // by a frame of the settings it replaces.
    var firstCar by remember(v.vin) { mutableStateOf(false) }
    LaunchedEffect(v.vin) {
        firstCar = !vm.autoLockConfiguredElsewhere(v.vin)
        config = vm.autoLockConfig(v.vin)
    }
    val current = config ?: return

    // The background exemption and the notification channel are granted in SYSTEM screens, so re-read
    // them every time we come back to Settings instead of only once -- otherwise the "allow
    // background" and "hide the watcher" steps never clear.
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
    // The watcher's own channel, NOT the events one: bloo_autolock is the quiet "AutoLock is on"
    // notification, and hiding it leaves the "your car was locked" notice (bloo_autolock_events_v1)
    // alone. Read back, so the step clears itself once it has been done.
    val watcherHidden = remember(recheck) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(AutoLockNotification.CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE
        }.getOrDefault(false)
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

    val bluetoothOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        granted(Manifest.permission.BLUETOOTH_CONNECT)
    val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.POST_NOTIFICATIONS)

    var showDevicePicker by remember { mutableStateOf(false) }

    val showSetup = firstCar && !(bluetoothOk && notificationsOk && backgroundOk && current.isUsable)

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

    /** The watcher notification lives on its own channel, so hiding it is a system-screen trip. */
    fun openWatcherChannel() {
        context.tryStart(
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, AutoLockNotification.CHANNEL_ID)
            },
        )
    }

    if (showSetup) {
        AutoLockSetup(
            carName = v.name,
            bluetoothOk = bluetoothOk,
            notificationsOk = notificationsOk,
            backgroundOk = backgroundOk,
            watcherHidden = watcherHidden,
            deviceName = current.deviceName,
            onRequestPermissions = { corePermissionLauncher.launch(corePermissions.toTypedArray()) },
            onAllowBackground = { LiveCharge.requestBackgroundUnrestricted(context) },
            onChooseDevice = {
                if (bluetoothOk) showDevicePicker = true
                else bluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            },
            onHideWatcher = { openWatcherChannel() },
        )
    } else {
        AutoLockSettings(current, v, vm, gen5w, backgroundOk) { update(it) }
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
                    RevealText(
                        "No paired Bluetooth devices. Pair your car in Android's Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // The paired-device list is a handful of rows, not a feed.
                    devices.forEach { device ->
                        ListButton(
                            title = device.name,
                            subtitle = device.address,
                            onClick = {
                                // Choosing a device is what turns AutoLock ON for this car, not the
                                // toggle: the toggle cannot appear until this is done (see above), so
                                // the pick IS the enable. Dry run stays on (see AutoLockConfig), so
                                // the first walks only say "would have locked".
                                update(
                                    current.copy(
                                        deviceAddress = device.address,
                                        deviceName = device.name,
                                        enabled = true,
                                    ),
                                )
                                showDevicePicker = false
                            },
                        )
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

/**
 * The first-car setup. Four rows, in the order they have to happen, each showing its own state and
 * the one action that clears it. They are genuinely ordered -- there is no paired-device list to pick
 * from without the Bluetooth permission, and nothing reacts in the background without the exemption
 * -- so the later rows are disabled until the earlier ones are done rather than letting the user tap
 * something that cannot work yet.
 */
@Composable
private fun AutoLockSetup(
    carName: String,
    bluetoothOk: Boolean,
    notificationsOk: Boolean,
    backgroundOk: Boolean,
    watcherHidden: Boolean,
    deviceName: String?,
    onRequestPermissions: () -> Unit,
    onAllowBackground: () -> Unit,
    onChooseDevice: () -> Unit,
    onHideWatcher: () -> Unit,
) {
    val permissionsOk = bluetoothOk && notificationsOk
    SettingsGroup("AutoLock") {
        BodySmallText(
            "Locks $carName when you walk away, using the car's Bluetooth to tell you've left.",
        )
        BodySmallText(
            "Four things first. AutoLock does nothing at all until they are done, so none of its " +
                "settings are here yet.",
        )
        SectionDivider()

        StepRow("1. Bluetooth permission", if (bluetoothOk) "Granted" else "Not granted")
        BodySmallText("Lets Bloo recognise your car's head unit. Without it, a disconnect cannot be matched to a car.")
        SafeMorphTextButton(
            if (bluetoothOk) "Granted" else "Allow Bluetooth",
            enabled = !bluetoothOk,
            onClick = onRequestPermissions,
        )

        StepRow("2. Notifications", if (notificationsOk) "Allowed" else "Not allowed")
        BodySmallText("Carries the progress and the result. AutoLock still locks the car without it, silently.")
        SafeMorphTextButton(
            if (notificationsOk) "Allowed" else "Allow notifications",
            enabled = !notificationsOk,
            onClick = onRequestPermissions,
        )

        StepRow("3. Background activity", if (backgroundOk) "Unrestricted" else "Restricted")
        BodySmallText("A battery-restricted app is asleep when you walk away, so it never sees the disconnect.")
        SafeMorphTextButton(
            "Allow background activity",
            enabled = !backgroundOk,
            onClick = onAllowBackground,
        )

        StepRow("4. Car Bluetooth device", deviceName ?: "Not set")
        BodySmallText("The one device Bloo watches. Pick the car's head unit, not your phone or earbuds.")
        SafeMorphTextButton(
            if (deviceName == null) "Choose device" else "Change device",
            enabled = permissionsOk && backgroundOk,
            onClick = onChooseDevice,
        )

        SectionDivider()

        // Optional on purpose, and last: the watcher notification is a real feature (it is the only
        // sign AutoLock is running), so hiding it is the user's call, not a prerequisite. Hiding it
        // leaves the "your car was locked" notice alone -- different channel.
        StepRow("Watcher notification", if (watcherHidden) "Hidden" else "Showing")
        BodySmallText("AutoLock keeps a quiet notification while it is on. Hide it if you would rather not see it.")
        SafeMorphTextButton(
            "Hide watcher notification",
            enabled = !watcherHidden,
            onClick = onHideWatcher,
            icon = Icons.Filled.NotificationsOff,
        )
    }
}

/** The real controls, for every car once the first one has been set up. */
@Composable
private fun AutoLockSettings(
    current: AutoLockConfig,
    v: Vehicle,
    vm: AppViewModel,
    gen5w: Boolean,
    backgroundOk: Boolean,
    update: (AutoLockConfig) -> Unit,
) {
    val context = LocalContext.current
    var showDevicePicker by remember { mutableStateOf(false) }
    // The slider drags locally and persists once, on settle: a DataStore write behind every frame of
    // a drag is a write per frame.
    var grace by remember(current.graceSeconds) { mutableFloatStateOf(current.graceSeconds.toFloat()) }
    val notificationsOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

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
            onChange = { update(current.copy(enabled = it)) },
        )

        // AutoLock's notification controls (its alerts, the watcher notification, and the
        // permission and reliability prompts it depends on) are in Settings -> Notifications, next
        // to every other reason an alert might or might not arrive.
        if (current.enabled) {
            StatusRow("Car Bluetooth device", current.deviceName ?: "Not set")
            SafeMorphTextButton(
                "Change device",
                onClick = { showDevicePicker = true },
            )

            // The reliability prompts, in the order they matter. Both are what actually lets AutoLock
            // fire after you walk away -- a Doze-suspended process never reacts to the disconnect, and
            // a missing notification permission makes every step silent.
            if (!backgroundOk) {
                BodySmallText(
                    "AutoLock has to keep running while Bloo is in the background, or it can't lock " +
                        "the car after you walk away. Allow background activity next.",
                )
                SafeMorphTextButton(
                    "Allow background activity",
                    onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                )
            } else if (!notificationsOk) {
                BodySmallText(
                    "Without the notification permission AutoLock still locks the car, but you get " +
                        "no progress and no result -- it happens silently.",
                )
            }

            // The two waits are separate, and the user only ever sees the SECOND one. That is why this
            // is spelled out: the confirmation happens first -- Android's own walk-away detection,
            // usually 10 to 15 seconds and up to 20 -- and the grace period below is ADDITIONAL time
            // on top of it. "Waits 15s" read as the whole delay when it was only half of it.
            BodySmallText(
                "First Bloo waits for Android to confirm you have actually walked away from the car. " +
                    "That usually takes 10 to 15 seconds, and up to 20.",
            )
            StepRow("Grace period", "${grace.roundToInt()}s")
            AnimatedSlider(
                value = grace,
                onValueChange = { grace = it },
                onValueSettled = { update(current.copy(graceSeconds = it.roundToInt())) },
                valueRange = GraceMinSeconds.toFloat()..GraceMaxSeconds.toFloat(),
                // 5s steps across the (short) range, so every stop is a distinct, low delay.
                steps = (GraceMaxSeconds - GraceMinSeconds) / 5 - 1,
            )
            val graceNow = grace.roundToInt()
            BodySmallText(
                if (graceNow == 0) {
                    "Then it locks as soon as that confirmation lands - no extra wait."
                } else {
                    "Then it waits ${graceNow}s more before locking, on top of the confirmation. " +
                        "\"Lock now\" skips this part."
                },
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
            SectionDivider()
            SafeMorphTextButton(
                "Simulate leaving",
                enabled = current.isUsable,
                onClick = { vm.simulateAutoLockLeaving(v) },
            )

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
                // The detection says what happened; this says WHY, in the place the user is already
                // standing. It is the one thing the old section never showed: a skip and a failure
                // both looked like nothing at all from here.
                s.lastOutcome?.let { BodySmallText(it) }
            }
        }
    }

    if (showDevicePicker) {
        val devices = remember { vm.pairedBluetoothDevices() }
        GlassAlertDialog(
            onDismissRequest = { showDevicePicker = false },
            icon = Icons.Filled.Bluetooth,
            title = "Choose the car's Bluetooth device",
            text = {
                if (devices.isEmpty()) {
                    RevealText(
                        "No paired Bluetooth devices. Pair your car in Android's Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    devices.forEach { device ->
                        ListButton(
                            title = device.name,
                            subtitle = device.address,
                            onClick = {
                                update(current.copy(deviceAddress = device.address, deviceName = device.name))
                                showDevicePicker = false
                            },
                        )
                    }
                }
            },
            buttons = {
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
