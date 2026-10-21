package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.supportsHornLights

/**
 * One control that can sit in a quarter of the watch face. The registry below is the single list: the face
 * reads it to draw and run a quarter, and the chooser reads it to offer the options, so adding a control is
 * one entry here and nothing else.
 */
/** What a control acts on: the headings of the chooser, in the order they appear. */
internal enum class FaceGroup(val title: String, val icon: ImageVector) {
    Doors("Doors", AppIcons.Lock),
    Climate("Climate", AppIcons.Thermostat),
    Charging("Charging", AppIcons.Bolt),
    LightsAndHorn("Lights and horn", AppIcons.Flash),
    Car("Car", AppIcons.DirectionsCar),
}

internal data class FaceControl(
    /** What is stored in the wearer's choice; never change one. */
    val key: String,
    /** The heading it sits under in the chooser (the controls are grouped by what they act on). */
    val group: FaceGroup,
    /** A few words under its name in the chooser: what it does. */
    val hint: String = "",
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
    /** The climate settings the command carries (a saved preset's); null for a control that has none. */
    val request: ((VehicleSnapshot) -> ClimateRequest?)? = null,
    /**
     * True when pressing it cannot run yet because the wearer has to choose first: a quarter set to a climate preset this car
     * no longer has (or to "a climate preset" without one picked). The page then shows the list instead of failing.
     */
    val needsChoice: (VehicleSnapshot) -> Boolean = { false },
)

internal object WearFaceControls {
    const val NONE = "none"

    val all: List<FaceControl> = listOf(
        FaceControl(
            key = "lock", group = FaceGroup.Doors, hint = "Locks, or unlocks (asks first)", choice = "Lock / unlock",
            label = { if (it.locked == true) "Unlock" else "Lock" },
            icon = { if (it.locked == true) AppIcons.LockOpen else AppIcons.Lock },
            action = CarAction.TOGGLE_LOCK,
            confirm = { it.locked == true },
        ),
        FaceControl(
            key = "lock_only", group = FaceGroup.Doors, hint = "Always locks", choice = "Lock", label = { "Lock" }, icon = { AppIcons.Lock },
            action = CarAction.LOCK, active = { it.locked == true },
        ),
        FaceControl(
            key = "unlock_only", group = FaceGroup.Doors, hint = "Asks first, then unlocks", choice = "Unlock", label = { "Unlock" }, icon = { AppIcons.LockOpen },
            action = CarAction.UNLOCK, confirm = { true },
        ),
        FaceControl(
            key = "climate", group = FaceGroup.Climate, hint = "Starts or stops it", choice = "Climate on / off",
            label = { if (it.climateOn == true) "Climate off" else "Climate" },
            icon = { AppIcons.Climate }, action = CarAction.TOGGLE_CLIMATE, active = { it.climateOn == true },
        ),
        FaceControl(
            key = "climate_on", group = FaceGroup.Climate, hint = "Starts it on the usual settings", choice = CarLabels.START_CLIMATE, label = { "Climate on" }, icon = { AppIcons.Climate },
            action = CarAction.CLIMATE_ON, active = { it.climateOn == true },
        ),
        FaceControl(
            key = "climate_off", group = FaceGroup.Climate, hint = "Stops it", choice = CarLabels.STOP_CLIMATE, label = { "Climate off" }, icon = { AppIcons.Close },
            action = CarAction.CLIMATE_OFF,
        ),
        FaceControl(
            key = "climate_smart", group = FaceGroup.Climate, hint = "For the weather at the car", choice = CarLabels.SMART_CLIMATE,
            label = { if (it.climateOn == true) "Climate off" else CarLabels.SMART_CLIMATE },
            icon = { AppIcons.SmartClimate }, action = CarAction.CLIMATE_SMART, active = { it.climateOn == true },
        ),
        // Choosing this in the quarter chooser opens the list of climate presets (smart climate among them), and the quarter
        // then holds the one picked. Stored as itself only before that pick, in which case a press asks again.
        FaceControl(
            key = CLIMATE_PICK, group = FaceGroup.Climate, hint = "A saved preset, or smart climate", choice = "Climate preset…", label = { "Climate" }, icon = { AppIcons.Thermostat },
            action = CarAction.CLIMATE_ON, active = { it.climateOn == true }, needsChoice = { true },
        ),
        FaceControl(
            key = "charge", group = FaceGroup.Charging, hint = "Starts or stops it", choice = "Charge on / off",
            label = { if (it.charging == true) "Stop charge" else "Charge" },
            icon = { AppIcons.Bolt }, action = CarAction.TOGGLE_CHARGE,
            available = { it.hasBattery }, active = { it.charging == true },
        ),
        FaceControl(
            key = "charge_on", group = FaceGroup.Charging, hint = "Starts charging", choice = CarLabels.START_CHARGING, label = { "Charge" }, icon = { AppIcons.Bolt },
            action = CarAction.CHARGE_ON, available = { it.hasBattery }, active = { it.charging == true },
        ),
        FaceControl(
            key = "charge_off", group = FaceGroup.Charging, hint = "Stops charging", choice = CarLabels.STOP_CHARGING, label = { "Stop charge" }, icon = { AppIcons.Close },
            action = CarAction.CHARGE_OFF, available = { it.hasBattery },
        ),
        FaceControl(
            key = "flash", group = FaceGroup.LightsAndHorn, hint = "Flashes the lights", choice = CarLabels.FLASH_LIGHTS, label = { "Flash" }, icon = { AppIcons.Flash },
            action = CarAction.FLASH_LIGHTS, available = { it.toVehicle().supportsHornLights },
        ),
        FaceControl(
            key = "horn", group = FaceGroup.LightsAndHorn, hint = "Sounds the horn and flashes", choice = "Horn and lights", label = { "Horn" }, icon = { AppIcons.Notifications },
            action = CarAction.HORN_AND_LIGHTS, available = { it.toVehicle().supportsHornLights },
        ),
        FaceControl(
            key = "refresh", group = FaceGroup.Car, hint = "Reads its latest status", choice = "Refresh status", label = { "Refresh" }, icon = { AppIcons.Refresh },
            action = CarAction.REFRESH,
        ),
    )

    /** What each quarter holds until the wearer chooses: the four everyday controls. */
    val defaults = listOf("lock", "climate", "charge", "flash")

    /** The chooser entry that leads to the list of climate presets; see the control of this key. */
    const val CLIMATE_PICK = "climate_pick"

    /** Smart climate's stored key (it is chosen from the preset list, not from the quarter chooser). */
    const val CLIMATE_SMART_KEY = "climate_smart"

    /** A stored choice that is one of the phone's climate presets is "preset:" and its id; the controls below are built from it. */
    const val PRESET_PREFIX = "preset:"

    /**
     * A control that starts one saved climate preset (the phone's own, see [WearPresets]). Built on demand from the stored
     * id, because which presets exist belongs to the phone. One the car does not have any more still answers a press, by
     * asking which to use ([FaceControl.needsChoice]) rather than staying silent or failing.
     */
    private fun presetControl(id: String) = FaceControl(
        key = PRESET_PREFIX + id,
        group = FaceGroup.Climate,
        choice = "Preset: " + (WearPresets.findAny(id)?.name ?: "Climate"),
        label = { WearPresets.find(it.vin, id)?.name ?: "Climate" },
        icon = { AppIcons.Thermostat },
        action = CarAction.CLIMATE_ON,
        active = { it.climateOn == true },
        request = { WearPresets.find(it.vin, id)?.request },
        needsChoice = { WearPresets.find(it.vin, id) == null },
    )

    fun byKey(key: String): FaceControl? =
        all.firstOrNull { it.key == key }
            ?: key.takeIf { it.startsWith(PRESET_PREFIX) && it.length > PRESET_PREFIX.length }?.let { presetControl(it.removePrefix(PRESET_PREFIX)) }

    /**
     * Everything this car's quarter can be: the fixed controls it can do. Climate presets are not listed one by one: "Climate
     * preset…" opens their own list (smart climate first), so the chooser stays short however many presets there are.
     */
    fun choicesFor(car: VehicleSnapshot): List<FaceControl> =
        all.filter { it.available(car) && it.key != CLIMATE_SMART_KEY }

    /** Whether a stored key is one of the climate ones that the "Climate preset…" entry stands for. */
    fun isClimatePick(key: String) = key == CLIMATE_PICK || key == CLIMATE_SMART_KEY || key.startsWith(PRESET_PREFIX)

    /** The name for a stored choice, in the chooser and the settings row. */
    fun choiceName(key: String): String = byKey(key)?.choice ?: "Empty"
}
