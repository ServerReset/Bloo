package com.bloo.bluelink.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
 * The car's climate on the watch, the phone's own: smart climate (the temperature the weather at the car calls for), then
 * every preset saved on the phone for this car, each starting at its settings, and a stop while it runs. A start goes
 * through the same PIN gate as a tap on the face ([requestCommand]).
 */
@Composable
internal fun WearClimatePebble(
    repo: WearSnapshotRepository,
    v: com.bloo.bluelink.data.VehicleSnapshot,
    pending: Set<String>,
    requestCommand: ((() -> Unit)) -> Unit,
) {
    val presets by WearPresets.presets.collectAsStateWithLifecycle()
    val saved = presets[v.vin].orEmpty()
    val on = v.climateOn == true
    fun busy(action: String) = WearCommands.key(v.vin, action) in pending
    WearPebble(title = "Climate", icon = Icons.Filled.AcUnit) {
        if (on) {
            WearActionRow("Stop climate", Icons.Filled.Close, pending = busy(com.bloo.bluelink.data.CarAction.CLIMATE_OFF)) {
                requestCommand { repo.command(v.vin, com.bloo.bluelink.data.CarAction.CLIMATE_OFF) }
            }
        } else {
            WearActionRow("Smart climate", Icons.Filled.AutoMode, pending = busy(com.bloo.bluelink.data.CarAction.CLIMATE_SMART)) {
                requestCommand { repo.command(v.vin, com.bloo.bluelink.data.CarAction.CLIMATE_SMART) }
            }
            saved.forEach { preset ->
                WearActionRow(preset.name, Icons.Filled.Thermostat, pending = busy(com.bloo.bluelink.data.CarAction.CLIMATE_ON)) {
                    requestCommand { repo.command(v.vin, com.bloo.bluelink.data.CarAction.CLIMATE_ON, preset.request) }
                }
                WearCenteredText(
                    WearPresets.detail(preset.request),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
            }
            if (saved.isEmpty()) {
                WearCenteredText(
                    "Smart climate sets the temperature for the weather at the car. Presets you save in Bloo on your phone show up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                    maxLines = 5,
                )
            }
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
    WearPebble(title = "Effects", icon = Icons.Filled.AutoAwesome) {
        WearActionRow("Reduce effects", Icons.Filled.AutoAwesome, active = reduced, switch = true) {
            reduced = !reduced
            WearLook.setReduceEffects(context, reduced)
            WearLook.sendToPhone(context)
        }
        WearCenteredText(
            if (reduced) "On. No blur, drifting colour or animation, which saves battery."
            else "Blur, drifting colour and animation. They switch off by themselves in Battery Saver.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            maxLines = 4,
        )
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
            WearLook.sendToPhone(context)
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

/** A door to the face editor, where each quarter's control is chosen. */
@Composable
internal fun WearFaceControlsPebble(slots: List<String>, onChoose: (Int) -> Unit) {
    // One door to the editor (the face in miniature) instead of four list rows that each said a long name and got cut off.
    WearPebble(title = "Face controls", icon = Icons.Filled.Tune) {
        WearActionRow("Change controls", Icons.Filled.Tune) { onChoose(0) }
    }
}
