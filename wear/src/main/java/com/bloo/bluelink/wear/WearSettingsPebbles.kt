package com.bloo.bluelink.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * The watch's settings pebbles: what it notifies about, and its own AutoLock.
 *
 * They live here rather than in WearGarageScreen because they are not car-page furniture -- they are
 * the watch's settings, they touch no vehicle, and they were only ever in that file because that is
 * where the list that showed them happened to be. The car page composes them; it does not own them.
 */

/** The watch's notification switches: a pebble of three toggles, kept deliberately short. */
@Composable
internal fun WearAlertsPebble() {
    val context = LocalContext.current
    val prefs = remember { WearNotificationPrefs(context) }
    var charging by remember { mutableStateOf(prefs.charging) }
    var complete by remember { mutableStateOf(prefs.chargeComplete) }
    var low by remember { mutableStateOf(prefs.lowBattery) }
    WearPebble(title = "Watch alerts", icon = Icons.Filled.Notifications) {
        WearActionRow("Charging card", Icons.Filled.Bolt, active = charging, switch = true) {
            charging = !charging; prefs.charging = charging; prefs.sendToPhone(context)
        }
        WearActionRow("Charged", Icons.Filled.BatteryFull, active = complete, switch = true) {
            complete = !complete; prefs.chargeComplete = complete; prefs.sendToPhone(context)
        }
        WearActionRow("Low battery", Icons.Filled.BatteryAlert, active = low, switch = true) {
            low = !low; prefs.lowBattery = low; prefs.sendToPhone(context)
        }
    }
}

/**
 * The watch's own AutoLock, tagged **beta**: lock the car when the wearer walks away. Off by
 * default, and honest about what beta means -- the phone does not need to be nearby, and the watch
 * only ever locks a car it has data for and that is not already locked.
 */
@Composable
internal fun WearAutoLockPebble() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(WearAutoLock.isEnabled(context)) }
    WearPebble(title = if (WearAutoLock.BETA) "Auto lock · Beta" else "Auto lock", icon = Icons.Filled.Lock) {
        WearActionRow("Lock when I walk away", Icons.Filled.Lock, active = enabled, switch = true) {
            enabled = !enabled
            WearAutoLock.setEnabled(context, enabled)
        }
        WearCenteredText(
            if (enabled) "On. The watch locks the car about a minute after it feels you walk away."
            else "Beta. The watch locks the car when it feels you walk away. Off by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            maxLines = 4,
        )
        // Said plainly: the watch has no Bluetooth link to the car. That pairing (the car's own Bluetooth) belongs to the
        // phone's AutoLock, so it is set up there; this one runs from the watch's motion sensor alone.
        WearCenteredText(
            "Uses the watch's motion sensor. To lock by the car's Bluetooth, set up AutoLock in Bloo on your phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            maxLines = 5,
        )
    }
}

/** Shown in place of Auto lock until the phone has handed this watch the car's sign-in. */
@Composable
internal fun WearPairPebble() {
    WearPebble(title = "Pair with phone", icon = Icons.Filled.Lock) {
        WearCenteredText(
            "Auto lock needs your car's sign-in. In Bloo on the phone, open Settings and set up the watch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            maxLines = 4,
        )
    }
}

/** What each quarter of the face does: a row per quarter, and a tap opens the chooser for it. */
@Composable
internal fun WearFaceControlsPebble(slots: List<String>, onChoose: (Int) -> Unit) {
    WearPebble(title = "Face controls", icon = Icons.Filled.Tune) {
        slots.forEachIndexed { i, key ->
            WearActionRow("${WearFacePrefs.positions[i]}: ${WearFaceControls.choiceName(key)}", Icons.Filled.Tune) { onChoose(i) }
        }
    }
}
