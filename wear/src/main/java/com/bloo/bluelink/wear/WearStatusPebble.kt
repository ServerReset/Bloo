package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.isStale
import com.bloo.uicommon.rememberRelativeTime

/** The car's state now: label/value rows from [WearCarFacts], each shown only when the car reports it. */
@Composable
internal fun WearStatusPebble(v: VehicleSnapshot) {
    WearPebble(title = v.name, icon = AppIcons.DirectionsCar) {
        WearCarFacts.status(v).forEach { (label, value) -> WearStatRow(label, value) }
    }
}

/**
 * Odometer, service, plate, where the car is, how fresh the reading is, and the Locate and Refresh buttons. The position is the car's own,
 * shown with the phone's coordinates.
 */
@Composable
internal fun WearCarCarePebble(
    v: VehicleSnapshot,
    refreshing: Boolean,
    canRefresh: Boolean,
    locating: Boolean,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
) {
    WearPebble(title = "Care", icon = AppIcons.Build) {
        WearCarFacts.care(v).forEach { (label, value) -> WearStatRow(label, value) }
        val where = v.lat?.let { lat -> v.lon?.let { lon -> coordString(lat, lon, decimals = 4) } }
        WearStatRow("Location", v.place ?: where ?: "Unknown")
        // Live: the phone's own label (:uicommon), so "5 min ago" moves on while the page stays open.
        val updated = rememberRelativeTime(v.fetchedAt)?.takeIf { it.isNotBlank() }
        if (refreshing || updated != null) {
            WearStatRow(
                "Updated",
                if (refreshing) "Refreshing…" else updated.orEmpty(),
                valueColor = if (v.isStale() && !refreshing) MaterialTheme.colorScheme.error else null,
            )
        }
        WearActionRow("Locate", Icons.Filled.MyLocation, pending = locating, onClick = onLocate)
        if (canRefresh) WearActionRow("Refresh", AppIcons.Refresh, pending = refreshing, onClick = onRefresh)
    }
}
