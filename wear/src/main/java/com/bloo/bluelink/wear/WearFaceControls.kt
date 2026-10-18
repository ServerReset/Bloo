package com.bloo.bluelink.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.supportsHornLights

/**
 * One control that can sit in a quarter of the watch face. The registry below is the single list: the face
 * reads it to draw and run a quarter, and the chooser reads it to offer the options, so adding a control is
 * one entry here and nothing else.
 */
internal data class FaceControl(
    /** What is stored in the wearer's choice; never change one. */
    val key: String,
    /** The name in the chooser (the quarter's own label may change with the car's state). */
    val choice: String,
    /** The word on the quarter, from the car's state ("Lock" or "Unlock"). */
    val label: (VehicleSnapshot) -> String,
    val icon: (VehicleSnapshot) -> ImageVector,
    /** The command it sends, as the app's own action name. */
    val action: String,
    /** False leaves the quarter quiet for a car that cannot do it (no battery, no horn). */
    val available: (VehicleSnapshot) -> Boolean = { true },
    /** Lit while the thing it controls is on. */
    val active: (VehicleSnapshot) -> Boolean = { false },
    /** Opens the car, so it asks once more first. */
    val confirm: (VehicleSnapshot) -> Boolean = { false },
)

internal object WearFaceControls {
    const val NONE = "none"

    val all: List<FaceControl> = listOf(
        FaceControl(
            key = "lock", choice = "Lock / unlock",
            label = { if (it.locked == true) "Unlock" else "Lock" },
            icon = { if (it.locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock },
            action = CarAction.TOGGLE_LOCK,
            confirm = { it.locked == true },
        ),
        FaceControl(
            key = "lock_only", choice = "Lock", label = { "Lock" }, icon = { Icons.Filled.Lock },
            action = CarAction.LOCK, active = { it.locked == true },
        ),
        FaceControl(
            key = "unlock_only", choice = "Unlock", label = { "Unlock" }, icon = { Icons.Filled.LockOpen },
            action = CarAction.UNLOCK, confirm = { true },
        ),
        FaceControl(
            key = "climate", choice = "Climate on / off",
            label = { if (it.climateOn == true) "Climate off" else "Climate" },
            icon = { Icons.Filled.AcUnit }, action = CarAction.TOGGLE_CLIMATE, active = { it.climateOn == true },
        ),
        FaceControl(
            key = "climate_on", choice = "Start climate", label = { "Climate on" }, icon = { Icons.Filled.AcUnit },
            action = CarAction.CLIMATE_ON, active = { it.climateOn == true },
        ),
        FaceControl(
            key = "climate_off", choice = "Stop climate", label = { "Climate off" }, icon = { Icons.Filled.Close },
            action = CarAction.CLIMATE_OFF,
        ),
        FaceControl(
            key = "charge", choice = "Charge on / off",
            label = { if (it.charging == true) "Stop charge" else "Charge" },
            icon = { Icons.Filled.Bolt }, action = CarAction.TOGGLE_CHARGE,
            available = { it.hasBattery }, active = { it.charging == true },
        ),
        FaceControl(
            key = "charge_on", choice = "Start charging", label = { "Charge" }, icon = { Icons.Filled.Bolt },
            action = CarAction.CHARGE_ON, available = { it.hasBattery }, active = { it.charging == true },
        ),
        FaceControl(
            key = "charge_off", choice = "Stop charging", label = { "Stop charge" }, icon = { Icons.Filled.Close },
            action = CarAction.CHARGE_OFF, available = { it.hasBattery },
        ),
        FaceControl(
            key = "flash", choice = "Flash lights", label = { "Flash" }, icon = { Icons.Filled.FlashOn },
            action = CarAction.FLASH_LIGHTS, available = { it.toVehicle().supportsHornLights },
        ),
        FaceControl(
            key = "horn", choice = "Horn and lights", label = { "Horn" }, icon = { Icons.Filled.Notifications },
            action = CarAction.HORN_AND_LIGHTS, available = { it.toVehicle().supportsHornLights },
        ),
        FaceControl(
            key = "refresh", choice = "Refresh status", label = { "Refresh" }, icon = { Icons.Filled.Refresh },
            action = CarAction.REFRESH,
        ),
    )

    /** What each quarter holds until the wearer chooses: the four everyday controls. */
    val defaults = listOf("lock", "climate", "charge", "flash")

    fun byKey(key: String): FaceControl? = all.firstOrNull { it.key == key }

    /** The name for a stored choice, in the chooser and the settings row. */
    fun choiceName(key: String): String = byKey(key)?.choice ?: "Empty"
}
