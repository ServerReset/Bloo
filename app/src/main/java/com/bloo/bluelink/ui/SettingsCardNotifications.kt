package com.bloo.bluelink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bloo.bluelink.autolock.AutoLockNotification
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.openLiveUpdateSettings
import com.bloo.bluelink.data.isBackgroundUnrestricted
import com.bloo.bluelink.data.requestBackgroundUnrestricted

/**
 * Simple mode is the handful of switches most people want. Advanced adds the timing thresholds, the
 * charging notification's look, the less common alerts and the troubleshooting tools.
 */
@Composable
internal fun NotificationsCardContent(
    notif: SettingsStore.NotificationPrefs,
    state: UiState,
    advanced: Boolean,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    // AutoLock is configured per car and read asynchronously, so "is it on for anyone" is resolved
    // here once per garage change rather than asked of every row.
    var autoLockOn by remember { mutableStateOf(false) }
    LaunchedEffect(state.vehicles) {
        autoLockOn = state.vehicles.any { v -> runCatching { vm.autoLockConfig(v.vin).enabled }.getOrDefault(false) }
    }
    // Bumped when a permission prompt returns, so the warnings below re-check the real grant.
    var recheck by remember { mutableIntStateOf(0) }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }

    val toggles = listOf(
        notif.charging, notif.chargeComplete, notif.unlocked, notif.doorOpen, notif.service,
        notif.running, notif.carStarted, notif.autoLockAlerts,
    )
    val alertsOn = toggles.count { it }
    val status = if (alertsOn == 0) "All off" else "$alertsOn of ${toggles.size} on"

    SettingsCard("Notifications", Icons.Filled.Notifications, vm, status = status) {
      Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        SettingsGroup("Charging") {
            // First: every other switch here is an alert the user hopes never fires, while this is
            // a live surface they watch on purpose while the car charges.
            ToggleRow(
                "Live charging updates",
                notif.charging,
                description = "A live notification with progress, the charge limit and a Stop button; on Android 16+ it also shows in the status bar.",
            ) { vm.setNotifyCharging(it) }
            // The troubleshooting lives WITH the live notification it is about -- in the same
            // "Charging" group, right under the toggle that turns it on -- not tucked at the bottom
            // of the card behind advanced mode.
            PopVisible(visible = notif.charging) {
                var showTroubleshoot by remember { mutableStateOf(false) }
                SafeMorphTextButton(
                    "Live notification not showing?",
                    onClick = { showTroubleshoot = true },
                    icon = AppIcons.Info,
                )
                if (showTroubleshoot) LiveUpdateTroubleshootDialog(onDismiss = { showTroubleshoot = false })
            }
            ToggleRow("Charge complete", notif.chargeComplete) { vm.setNotifyChargeComplete(it) }
            val hasWatchApp by com.bloo.bluelink.wear.WatchPresence.hasApp.collectAsStateWithLifecycle()
            PopVisible(visible = hasWatchApp) {
                ToggleRow(
                    "Low battery on watch",
                    notif.watchLowBattery,
                    description = "Your watch warns you when a car drops under 20% and isn't charging. Charging and charge-complete alerts show on the watch too; these switches match the ones there.",
                ) { vm.setNotifyWatchLowBattery(it) }
            }
        }

        SettingsGroup("Car alerts") {
            ToggleRow("Left unlocked", notif.unlocked) { vm.setNotifyUnlocked(it) }
            PopVisible(visible = notif.unlocked && advanced) {
                MinutesField(notif.unlockedMinutes, "Minutes before alerting", vm::setUnlockedMinutes)
            }
            ToggleRow("Door left open", notif.doorOpen) { vm.setNotifyDoor(it) }
            PopVisible(visible = notif.doorOpen && advanced) {
                MinutesField(notif.doorOpenMinutes, "Minutes before alerting", vm::setDoorOpenMinutes)
            }
            ToggleRow("Service due", notif.service) { vm.setNotifyService(it) }
            PopVisible(visible = advanced) {
                Column {
                    ToggleRow("Car left running", notif.running) { vm.setNotifyRunning(it) }
                    PopVisible(visible = notif.running) {
                        MinutesField(notif.runningMinutes, "Minutes before alerting", vm::setRunningMinutes)
                    }
                    ToggleRow("Car started", notif.carStarted) { vm.setNotifyCarStarted(it) }
                }
            }
            BodySmallText("Checks run about every 30 minutes. Door and running alerts include a one-tap action.")
        }

        // Only worth a line for someone using AutoLock; in advanced mode it is always listed so the
        // switch can be set before AutoLock is turned on.
        PopVisible(visible = autoLockOn || advanced) {
            Column {
                SettingsGroup("AutoLock") {
                    ToggleRow(
                        "AutoLock alerts",
                        notif.autoLockAlerts,
                        description = "Tells you when AutoLock locks the car, or would have in testing mode. " +
                            "A failed lock always notifies.",
                    ) { vm.setNotifyAutoLock(it) }
                    PopVisible(visible = autoLockOn) {
                        Column {
                            SettingsCaption(
                                "AutoLock runs a quiet background watcher. You can hide its notification from " +
                                    "Android's notification settings.",
                                bottomGap = GapHairline,
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
                                // The tonal emphasis already pairs the container with its own
                                // legible on-colour; letting it do that is the fix.
                                icon = Icons.Filled.NotificationsOff,
                            )
                        }
                    }
                }
            }
        }

        // What is actually stopping an enabled alert from arriving. Read live on every pass; the
        // permission prompt bumps [recheck] so a grant clears its own warning.
        val ignored = recheck
        val backgroundBlocked = (notif.charging || autoLockOn) && !LiveCharge.isBackgroundUnrestricted(context)
        val activityBlocked = autoLockOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        val alarmsBlocked = autoLockOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() != true
        val chipBlocked = notif.charging && Build.VERSION.SDK_INT >= 36 &&
            !LiveCharge.isPromotable(context)
        if (ignored >= 0 && (backgroundBlocked || activityBlocked || alarmsBlocked || chipBlocked)) {
            SettingsGroup("Needs attention") {
                if (backgroundBlocked) {
                    AttentionRow("Alerts may not arrive with the app closed", "Allow background activity") {
                        LiveCharge.requestBackgroundUnrestricted(context)
                    }
                }
                if (activityBlocked) {
                    AttentionRow("AutoLock can't tell you've walked away", "Allow physical activity") {
                        activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
                if (alarmsBlocked) {
                    AttentionRow("AutoLock may lock late", "Allow alarms & reminders") {
                        context.tryStart(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    }
                }
                if (chipBlocked) {
                    AttentionRow("Charging bar isn't reaching the status bar", "Open system settings") {
                        LiveCharge.openLiveUpdateSettings(context)
                    }
                }
            }
        }

      }
    }
}

/** One fixable problem: what is wrong as a caption, the fix as a real button beneath it. */
@Composable
private fun AttentionRow(problem: String, action: String, onClick: () -> Unit) {
    SettingsCaption(problem, bottomGap = GapHairline)
    SafeMorphTextButton(
        action,
        onClick = onClick,
        icon = AppIcons.Warning,
    )
}
