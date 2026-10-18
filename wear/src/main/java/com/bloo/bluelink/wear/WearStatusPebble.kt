package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.bluelink.data.updatedLabel
import com.bloo.uicommon.RevealText

/**
 * The car's details, just past the face: one pebble of label/value rows (lock, range, charge limit, climate,
 * odometer, how fresh the reading is), each shown only when the car reports it, and the refresh button where
 * the freshness is. Replaces the loose text lines that used to sit under the ring.
 */
@Composable
internal fun WearStatusPebble(v: VehicleSnapshot, refreshing: Boolean, canRefresh: Boolean, onRefresh: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rows = buildList {
        v.locked?.let { add("Doors" to (if (it) "Locked" else "Unlocked")) }
        v.rangeMi?.let { add("Range" to "$it mi") }
        if (v.hasBattery) {
            v.charging?.let { add("Charging" to (if (it) "Yes" else "No")) }
            v.chargeLimitPct?.let { add("Limit" to "$it%") }
        }
        v.climateOn?.let { add("Climate" to (if (it) "On" else "Off")) }
        v.odometer?.takeIf { it.isNotBlank() }?.let { add("Odometer" to it) }
        v.licensePlate?.takeIf { it.isNotBlank() }?.let { add("Plate" to it) }
    }
    WearPebble(title = v.name, icon = Icons.Filled.DirectionsCar) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, value) -> WearStatRow(label, value) }
            val updated = v.updatedLabel()
            if (refreshing || updated != null) {
                WearStatRow(
                    "Updated",
                    if (refreshing) "Refreshing…" else updated.orEmpty(),
                    valueColor = if (v.isStale() && !refreshing) scheme.error else null,
                )
            }
            if (canRefresh) {
                WearActionRow(label = "Refresh", icon = Icons.Filled.Refresh, pending = refreshing, onClick = onRefresh)
            }
        }
    }
}

/** One label/value line in a pebble: the label muted on the start, the value on the end. */
@Composable
internal fun WearStatRow(label: String, value: String, valueColor: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    // A glass pill, like the phone's status rows.
    Row(
        Modifier
            .fillMaxWidth()
            .fitRound()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RevealText(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        RevealText(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor ?: scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
