package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel

/** The car's state now: label/value rows from [WearCarFacts], each shown only when the car reports it. */
@Composable
internal fun WearStatusPebble(v: VehicleSnapshot) {
    WearPebble(title = v.name, icon = AppIcons.DirectionsCar) {
        WearCarFacts.status(v).forEach { (label, value) -> WearStatRow(label, value) }
    }
}

/** Odometer, service, plate, how fresh the reading is, and the refresh button where the freshness is. */
@Composable
internal fun WearCarCarePebble(v: VehicleSnapshot, refreshing: Boolean, canRefresh: Boolean, onRefresh: () -> Unit) {
    WearPebble(title = "Care", icon = AppIcons.Build) {
        WearCarFacts.care(v).forEach { (label, value) -> WearStatRow(label, value) }
        val updated = v.updatedLabel()
        if (refreshing || updated != null) {
            WearStatRow(
                "Updated",
                if (refreshing) "Refreshing…" else updated.orEmpty(),
                valueColor = if (v.isStale() && !refreshing) MaterialTheme.colorScheme.error else null,
            )
        }
        if (canRefresh) WearActionRow("Refresh", AppIcons.Refresh, pending = refreshing, onClick = onRefresh)
    }
}
