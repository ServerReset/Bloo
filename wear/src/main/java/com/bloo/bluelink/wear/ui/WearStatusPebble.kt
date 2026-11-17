package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.isStale
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.rememberRelativeTime

/**
 * The one number a page is about, large, with what it measures under it: the way the face shows the charge. Each page leads with one, so
 * the wearer reads the car's main figure first and the rest of the page after it.
 */
@Composable
internal fun WearHero(value: String, caption: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = ListInset).fitRound(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RevealText(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (!caption.isNullOrBlank()) WearCaption(caption, small = true)
    }
}

/**
 * The car's state, in two pages: the battery or engine and range at the top, then what the car is doing now, and the charging figures on
 * their own page when it has a battery. Each fact is shown only when the car reports it.
 */
@Composable
internal fun WearStatusPebble(v: VehicleSnapshot) {
    val facts = WearCarFacts.status(v).toMap()
    // The parts stack top to bottom: a single item on the page holds them all, so they are laid out in a column, not over each other.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        val battery = facts["Battery"]
        if (battery != null) {
            WearHero(battery, facts["Range"]?.let { range -> "$range range" })
        } else facts["Engine"]?.let { engine -> WearHero(engine, null) }
        val now = listOf("Doors", "Climate", "Moving").mapNotNull { label -> facts[label]?.let { label to it } }
        if (now.isNotEmpty()) {
            WearPebble(title = "Now", icon = AppIcons.DirectionsCar) {
                now.forEach { (label, value) -> WearStatRow(label, value) }
            }
        }
        val charging = listOf("Charging", "Charge limit").mapNotNull { label -> facts[label]?.let { label to it } }
        if (charging.isNotEmpty()) {
            WearPebble(title = "Charging", icon = AppIcons.BatteryFull) {
                charging.forEach { (label, value) -> WearStatRow(label, value) }
            }
        }
    }
}

/**
 * The car's upkeep and the data behind it: the next service leads, then the odometer and plate, then how fresh the reading is and the
 * Refresh button. The watch does not show where the car is.
 */
@Composable
internal fun WearCarCarePebble(
    v: VehicleSnapshot,
    refreshing: Boolean,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
) {
    val care = WearCarFacts.care(v).toMap()
    val service = care["Service"]
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SectionGap)) {
    WearHero(service ?: care["Odometer"].orEmpty(), caption = if (service != null) "service" else "odometer")
    val car = listOf("Odometer", "Plate").mapNotNull { label -> care[label]?.let { label to it } }
    if (car.isNotEmpty()) {
        WearPebble(title = "Car", icon = AppIcons.Build) {
            car.forEach { (label, value) -> WearStatRow(label, value) }
        }
    }
    WearPebble(title = "Data", icon = AppIcons.Refresh) {
        // Live: the phone's own label (:uicommon), so "5 min ago" moves on while the page stays open.
        val updated = rememberRelativeTime(v.fetchedAt)?.takeIf { it.isNotBlank() }
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
}
