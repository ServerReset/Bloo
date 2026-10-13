package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.SettingsStore

/** The unit pickers, defined once for the Look card and for Settings search. */
@Composable
internal fun UnitSystemRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Units",
        options = listOf(SegmentOption("imperial", "Imperial", null), SegmentOption("metric", "Metric", null)),
        selectedKey = appearance.unitSystem,
        onSelect = { vm.setUnitSystem(it) },
    )
}

@Composable
internal fun TempUnitRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Temperature",
        options = listOf(SegmentOption("auto", "Auto", null), SegmentOption("f", "°F", null), SegmentOption("c", "°C", null)),
        selectedKey = appearance.tempUnit,
        onSelect = { vm.setTempUnit(it) },
    )
}

@Composable
internal fun DistanceUnitRow(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsSegmentedRow(
        label = "Distance and mileage",
        options = listOf(SegmentOption("auto", "Auto", null), SegmentOption("mi", "Miles", null), SegmentOption("km", "Km", null)),
        selectedKey = appearance.distanceUnit,
        onSelect = { vm.setDistanceUnit(it) },
    )
}
