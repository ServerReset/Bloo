package com.bloo.bluelink.ui

import com.bloo.uicommon.segmentsOf
import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.SettingsStore

/** The unit pickers, defined once for the Look card and for Settings search. */
@Composable
internal fun UnitSystemRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Units",
        options = segmentsOf("imperial" to "Imperial", "metric" to "Metric"),
        selectedKey = appearance.unitSystem,
        onSelect = { vm.setUnitSystem(it) },
    )
}

@Composable
internal fun TempUnitRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Temperature",
        options = segmentsOf("auto" to "Auto", "f" to "°F", "c" to "°C"),
        selectedKey = appearance.tempUnit,
        onSelect = { vm.setTempUnit(it) },
    )
}

@Composable
internal fun DistanceUnitRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Distance and mileage",
        options = segmentsOf("auto" to "Auto", "mi" to "Miles", "km" to "Km"),
        selectedKey = appearance.distanceUnit,
        onSelect = { vm.setDistanceUnit(it) },
    )
}
