package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.CarView
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.WearRemote

@Composable
fun HomeScreen(vm: WearViewModel, ui: WearUi, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    if (ui.cars.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No cars yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val pager = rememberPagerState { ui.cars.size }

    // Optimisation: only the *settled* car fetches status, so paging doesn't fire
    // a network call for every adjacent page the pager pre-composes.
    LaunchedEffect(pager.settledPage, ui.cars.size) {
        ui.cars.getOrNull(pager.settledPage)?.let { vm.onCarShown(it.vin) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            CarScreen(vm, ui, ui.cars[page], onSettings, onTrips)
        }
        if (ui.cars.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(ui.cars.size) { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.size(if (on) 7.dp else 5.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun CarScreen(
    vm: WearViewModel,
    ui: WearUi,
    car: CarView,
    onSettings: () -> Unit,
    onTrips: (String) -> Unit,
) {
    val state = rememberScalingLazyListState()
    val context = LocalContext.current
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "header") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                val rel = relativeLabel(car.fetchedAt)
                Text(
                    if (rel.isBlank()) (if (car.hasLiveStatus) "" else "synced") else "Updated $rel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "summary") { SummaryPebble(car) }

        item(key = "lock") {
            ActionButton(
                label = if (car.locked == true) "Locked" else "Unlocked",
                icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                active = car.locked == true,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = "${car.vin}:doors" in ui.pending,
                onClick = { vm.toggleLock(car.vin) },
            )
        }

        item(key = "climate") { ClimatePebble(vm, ui, car) }

        if (car.hasBattery) {
            item(key = "charge") { ChargePebble(vm, ui, car) }
        }

        val lat = car.lat
        val lon = car.lon
        if (lat != null && lon != null) {
            item(key = "location") {
                Pebble("Location") {
                    StatusRow("Coords", "%.4f, %.4f".format(lat, lon))
                    Spacer(Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = {
                            WearRemote.openOnPhone(
                                context,
                                "https://www.google.com/maps/search/?api=1&query=$lat,$lon",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Open on phone", maxLines = 1) },
                        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    )
                }
            }
        }

        item(key = "info") { InfoPebble(car) }

        if (car.hasLiveStatus) {
            item(key = "diagnostics") { DiagnosticsPebble(car) }
        }

        item(key = "actions") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (car.hasBattery) {
                    FilledTonalButton(
                        onClick = { onTrips(car.vin) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Trips") },
                        icon = { Icon(Icons.Filled.Route, contentDescription = null) },
                    )
                }
                FilledTonalButton(
                    onClick = { vm.refreshStatus(car.vin) },
                    enabled = "${car.vin}:refresh" !in ui.pending,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Refresh") },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                )
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun SummaryPebble(car: CarView) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChargeRing(car.percent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    car.rangeMi?.let { "$it mi" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (car.hasBattery) "Battery" else "Fuel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (car.charging == true) {
                    Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                }
            }
        }
    }
}

@Composable
private fun ClimatePebble(vm: WearViewModel, ui: WearUi, car: CarView) {
    Pebble("Climate") {
        ActionButton(
            label = if (car.climateOn == true) "Climate on" else "Start climate",
            icon = Icons.Filled.Thermostat,
            active = car.climateOn == true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = "${car.vin}:climate" in ui.pending,
            onClick = { vm.toggleClimate(car.vin) },
        )
        if (car.climateOn != true) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { vm.setClimateTemp(-1) }, modifier = Modifier.size(38.dp), label = { Text("–") })
                Text(
                    "${ui.climateTempF}°F",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(onClick = { vm.setClimateTemp(1) }, modifier = Modifier.size(38.dp), label = { Text("+") })
            }
            Spacer(Modifier.height(4.dp))
            ActionButton(
                label = "Defrost",
                icon = Icons.Filled.Thermostat,
                active = ui.climateDefrost,
                activeColor = WearColors.cool,
                pending = false,
                onClick = { vm.toggleDefrost() },
            )
        }
    }
}

@Composable
private fun ChargePebble(vm: WearViewModel, ui: WearUi, car: CarView) {
    Pebble("Charge") {
        ActionButton(
            label = if (car.charging == true) "Charging — stop" else "Start charge",
            icon = Icons.Filled.Bolt,
            active = car.charging == true,
            activeColor = WearColors.chargeGreen,
            pending = "${car.vin}:charge" in ui.pending,
            onClick = { vm.toggleCharge(car.vin) },
        )
        Spacer(Modifier.height(4.dp))
        car.percent?.let { StatusRow("Battery", "$it%") }
        StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
        car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
        car.acLimit?.let { StatusRow("AC limit", "$it%") }
        car.dcLimit?.let { StatusRow("DC limit", "$it%") }
    }
}

@Composable
private fun InfoPebble(car: CarView) {
    Pebble("Info") {
        StatusRow("VIN", car.vin.takeLast(6))
        car.odometer?.let { StatusRow("Odometer", it) }
        StatusRow("Doors", if (car.doorsOpen.isEmpty()) "Closed" else car.doorsOpen.joinToString())
        StatusRow("Windows", if (car.windowsOpen.isEmpty()) "Closed" else car.windowsOpen.joinToString())
        if (car.trunkOpen) StatusRow("Trunk", "Open", valueColor = MaterialTheme.colorScheme.error)
        if (car.hoodOpen) StatusRow("Hood", "Open", valueColor = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun DiagnosticsPebble(car: CarView) {
    Pebble("Diagnostics") {
        StatusRow("Tires", if (car.tireWarning) "Check" else "OK", valueColor = if (car.tireWarning) MaterialTheme.colorScheme.error else null)
        car.battery12v?.let { StatusRow("12V battery", "$it%") }
        if (car.lowFuel) StatusRow("Fuel", "Low", valueColor = MaterialTheme.colorScheme.error)
        if (car.washerLow) StatusRow("Washer", "Low", valueColor = MaterialTheme.colorScheme.error)
        if (car.brakeLow) StatusRow("Brake fluid", "Low", valueColor = MaterialTheme.colorScheme.error)
        if (car.keyFobLow) StatusRow("Key fob", "Low battery", valueColor = MaterialTheme.colorScheme.error)
    }
}

/** A titled "pebble" card matching the phone app's section style. */
@Composable
private fun Pebble(title: String, content: @Composable () -> Unit) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    pending: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.fillMaxWidth(),
        colors = if (active) {
            ButtonDefaults.buttonColors(containerColor = activeColor, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        label = { Text(if (pending) "Sending…" else label, maxLines = 1) },
        icon = { Icon(icon, contentDescription = null) },
    )
}
