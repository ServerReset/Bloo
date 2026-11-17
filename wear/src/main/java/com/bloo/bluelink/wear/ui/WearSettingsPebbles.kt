package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bloo.uicommon.AppIcons

/**
 * The watch's own settings, in ONE pebble: what it alerts about (four switches), the effects switch, and its own Auto lock. They
 * touch no car, so they live apart from the car page that shows them; each change the phone should keep (and Drive with it) is
 * sent up through [WearLook] or [WearNotificationPrefs].
 *
 * The face's controls are not here: they are changed by pressing the centre of the face.
 */
@Composable
internal fun WearSettingsPebble(signedIn: Boolean) {
    val context = LocalContext.current
    val alerts = remember { WearNotificationPrefs(context) }
    var charging by remember { mutableStateOf(alerts.charging) }
    var complete by remember { mutableStateOf(alerts.chargeComplete) }
    var low by remember { mutableStateOf(alerts.lowBattery) }
    var autoLockAlert by remember { mutableStateOf(alerts.autoLock) }
    var reduced by remember { mutableStateOf(WearLook.reduceEffects(context)) }
    var autoLock by remember { mutableStateOf(WearAutoLock.isEnabled(context)) }

    WearPebble(title = "Watch settings", icon = AppIcons.Settings) {
        // --- Alerts: one switch a row, the same rows as the effects below, so the section reads as a list not a grid ---
        WearSubheading("Alerts")
        WearToggleRow("Charging", AppIcons.Bolt, charging) {
            charging = it; alerts.charging = it; alerts.sendToPhone(context)
        }
        WearToggleRow("Charged", AppIcons.BatteryFull, complete) {
            complete = it; alerts.chargeComplete = it; alerts.sendToPhone(context)
        }
        WearToggleRow("Low battery", AppIcons.BatteryAlert, low) {
            low = it; alerts.lowBattery = it; alerts.sendToPhone(context)
        }
        WearToggleRow("Auto lock", AppIcons.Lock, autoLockAlert) {
            autoLockAlert = it; alerts.autoLock = it; alerts.sendToPhone(context)
        }
        WearCaption("Choose what the watch tells you.", small = true)

        // --- Effects: blur, springs and text animation off, as under Battery Saver ---
        WearSubheading("Effects")
        WearToggleRow(
            "Reduce effects", AppIcons.AutoAwesome, reduced,
            description = if (reduced) "Less motion, to save battery." else "Off by itself in Battery Saver",
        ) {
            reduced = it
            WearLook.setReduceEffects(context, it)
            WearLook.sendToPhone(context)
        }

        // --- Auto lock (beta): lock the car when the wearer walks away. It needs the car's sign-in on the watch. ---
        WearSubheading(if (WearAutoLock.BETA) "Auto lock · Beta" else "Auto lock")
        if (signedIn) {
            WearToggleRow("Lock when I walk away", AppIcons.Lock, autoLock) {
                autoLock = it
                WearAutoLock.setEnabled(context, it)
                WearLook.sendToPhone(context)
            }
            WearCaption(
                if (autoLock) "On. Locks about a minute after you walk away."
                else "Uses the watch's motion. Set up Bluetooth in Bloo on your phone.",
                small = true,
            )
        } else {
            WearCaption("Auto lock needs the car's sign-in. Set it up on your phone.", small = true, maxLines = 5)
        }
    }
}
