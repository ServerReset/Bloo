package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/*
 * The watch's own settings, one pebble each: face controls, alerts, effects and auto lock. They touch no car, so they live
 * apart from the car page that shows them. Each change that the phone should keep (and Drive with it) is sent up through
 * [WearLook] or [WearNotificationPrefs].
 */

/** A door to the face editor, where each quarter's control is chosen. */
@Composable
internal fun WearFaceControlsPebble(onOpen: () -> Unit) {
    WearPebble(title = "Face controls", icon = AppIcons.Tune) {
        WearActionRow("Change controls", AppIcons.Tune, onClick = onOpen)
    }
}

/** The watch's notification switches: three toggles, kept deliberately short. */
@Composable
internal fun WearAlertsPebble() {
    val context = LocalContext.current
    val prefs = remember { WearNotificationPrefs(context) }
    var charging by remember { mutableStateOf(prefs.charging) }
    var complete by remember { mutableStateOf(prefs.chargeComplete) }
    var low by remember { mutableStateOf(prefs.lowBattery) }
    var autoLock by remember { mutableStateOf(prefs.autoLock) }
    WearPebble(title = "Watch alerts", icon = AppIcons.Notifications) {
        WearToggleRow("Charging card", AppIcons.Bolt, charging) {
            charging = it; prefs.charging = it; prefs.sendToPhone(context)
        }
        WearToggleRow("Charged", AppIcons.BatteryFull, complete) {
            complete = it; prefs.chargeComplete = it; prefs.sendToPhone(context)
        }
        WearToggleRow("Low battery", AppIcons.BatteryAlert, low) {
            low = it; prefs.lowBattery = it; prefs.sendToPhone(context)
        }
        WearToggleRow("Auto lock", AppIcons.Lock, autoLock) {
            autoLock = it; prefs.autoLock = it; prefs.sendToPhone(context)
        }
    }
}

/**
 * The wearer's own switch for effects: blur, the drifting aurora, springs and text animation off, as under Battery Saver
 * (which turns them off by itself). Kept with the phone's settings, so it follows the watch to Drive.
 */
@Composable
internal fun WearEffectsPebble() {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(WearLook.reduceEffects(context)) }
    WearPebble(title = "Effects", icon = AppIcons.AutoAwesome) {
        WearToggleRow("Reduce effects", AppIcons.AutoAwesome, reduced) {
            reduced = it
            WearLook.setReduceEffects(context, it)
            WearLook.sendToPhone(context)
        }
        WearCaption(
            if (reduced) "On. No blur, drifting colour or animation, which saves battery."
            else "Blur, drifting colour and animation. They switch off by themselves in Battery Saver.",
        )
    }
}

/**
 * The watch's own AutoLock, tagged **beta**: lock the car when the wearer walks away. Off by default, and honest about what
 * beta means -- it runs from the watch's motion sensor alone and only locks a car it has data for that is not already locked.
 */
@Composable
internal fun WearAutoLockPebble() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(WearAutoLock.isEnabled(context)) }
    WearPebble(title = if (WearAutoLock.BETA) "Auto lock · Beta" else "Auto lock", icon = AppIcons.Lock) {
        WearToggleRow("Lock when I walk away", AppIcons.Lock, enabled) {
            enabled = it
            WearAutoLock.setEnabled(context, it)
            WearLook.sendToPhone(context)
        }
        WearCaption(
            if (enabled) "On. The watch locks the car about a minute after it feels you walk away."
            else "Beta. The watch locks the car when it feels you walk away. Off by default.",
        )
        // Said plainly: the watch has no Bluetooth link to the car; that pairing belongs to the phone's own AutoLock.
        WearCaption(
            "Uses the watch's motion sensor. To lock by the car's Bluetooth, set up AutoLock in Bloo on your phone.",
            small = true, maxLines = 5,
        )
    }
}

/** Shown in place of Auto lock until the phone has handed this watch the car's sign-in. */
@Composable
internal fun WearPairPebble() {
    WearPebble(title = "Pair with phone", icon = AppIcons.Lock) {
        WearCaption("Auto lock needs your car's sign-in. In Bloo on the phone, open Settings and set up the watch.")
    }
}
