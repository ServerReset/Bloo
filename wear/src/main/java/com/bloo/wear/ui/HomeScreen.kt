package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bloo.wear.CarView
import com.bloo.wear.WearRemote
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels

@Composable
fun HomeScreen(vm: WearViewModel, ui: WearUi, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    if (ui.cars.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cars yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val carPager = rememberPagerState { ui.cars.size }
    LaunchedEffect(carPager.settledPage, ui.cars.size) {
        ui.cars.getOrNull(carPager.settledPage)?.let { vm.onCarShown(it.vin) }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = carPager, modifier = Modifier.fillMaxSize()) { page ->
            CarTiles(vm, ui, ui.cars[page], onSettings, onTrips)
        }
        if (ui.cars.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { repeat(ui.cars.size) { Dot(it == carPager.currentPage) } }
        }
    }
}

@Composable
private fun CarTiles(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    val tiles = remember(car.vin, car.hasBattery, car.lat, car.hasLiveStatus) {
        buildList {
            add("summary"); add("climate"); add("comfort")
            if (car.hasBattery) { add("charge"); add("limits") }
            if (car.lat != null && car.lon != null) add("location")
            add("info")
            if (car.hasLiveStatus) add("diagnostics")
            add("more")
        }
    }
    val vPager = rememberPagerState { tiles.size }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = vPager, modifier = Modifier.fillMaxSize()) { i ->
            when (tiles[i]) {
                "summary" -> SummaryTile(vm, ui, car)
                "climate" -> ClimateTile(vm, ui, car)
                "comfort" -> ComfortTile(vm, ui)
                "charge" -> ChargeTile(vm, ui, car)
                "limits" -> LimitsTile(vm, ui, car)
                "location" -> LocationTile(car)
                "info" -> InfoTile(car)
                "diagnostics" -> DiagnosticsTile(car)
                "more" -> MoreTile(vm, ui, car, onSettings, onTrips)
            }
        }
        if (tiles.size > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { repeat(tiles.size) { Dot(it == vPager.currentPage) } }
        }
    }
}

// ---- Tiles ---------------------------------------------------------------

@Composable
private fun SummaryTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame(car.name) {
    ChargeRing(car.percent)
    Spacer(Modifier.height(2.dp))
    Text(car.rangeMi?.let { "$it mi" } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (car.charging == true) Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
    val rel = relativeLabel(car.fetchedAt)
    if (rel.isNotBlank()) Text("Updated $rel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    ActionButton(
        label = if (car.locked == true) "Locked" else "Unlocked",
        icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
        active = car.locked == true,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = "${car.vin}:doors" in ui.pending,
        onClick = { vm.toggleLock(car.vin) },
    )
}

@Composable
private fun ClimateTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Climate") {
    ActionButton(
        label = if (car.climateOn == true) "Climate on" else "Start climate",
        icon = Icons.Filled.Thermostat,
        active = car.climateOn == true,
        activeColor = MaterialTheme.colorScheme.tertiary,
        pending = "${car.vin}:climate" in ui.pending,
        onClick = { vm.toggleClimate(car.vin) },
    )
    Spacer(Modifier.height(6.dp))
    SliderRow("Temp", "${ui.climateTempF}°F", ui.climateTempF, 62, 82, 1) { vm.setClimateTemp(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("Run", "${ui.climateDuration} min", ui.climateDuration, 1, 30, 1) { vm.setClimateDuration(it) }
    Spacer(Modifier.height(4.dp))
    SwitchButton(
        checked = ui.climateDefrost,
        onCheckedChange = { vm.toggleDefrost() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Defrost") },
    )
}

@Composable
private fun ComfortTile(vm: WearViewModel, ui: WearUi) = TileFrame("Comfort") {
    SwitchButton(
        checked = ui.climateSteering,
        onCheckedChange = { vm.toggleSteering() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Steering heat") },
    )
    Spacer(Modifier.height(6.dp))
    SliderRow("Driver seat", seatStepLabels[ui.seatDriver], ui.seatDriver, 0, 3, 1) { vm.setSeatDriver(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("Passenger", seatStepLabels[ui.seatPassenger], ui.seatPassenger, 0, 3, 1) { vm.setSeatPassenger(it) }
    Spacer(Modifier.height(4.dp))
    Text("Applied when you start climate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun ChargeTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Charge") {
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
}

@Composable
private fun LimitsTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Charge limits") {
    SliderRow("AC", "${ui.acLimitDraft ?: car.acLimit ?: 80}%", ui.acLimitDraft ?: car.acLimit ?: 80, 50, 100, 5) { vm.setAcLimit(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("DC", "${ui.dcLimitDraft ?: car.dcLimit ?: 90}%", ui.dcLimitDraft ?: car.dcLimit ?: 90, 50, 100, 5) { vm.setDcLimit(it) }
    Spacer(Modifier.height(6.dp))
    Button(
        onClick = { vm.applyChargeLimits(car.vin) },
        enabled = "${car.vin}:chargeLimit" !in ui.pending,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if ("${car.vin}:chargeLimit" in ui.pending) "Sending…" else "Apply limits", maxLines = 1) },
    )
}

@Composable
private fun LocationTile(car: CarView) = TileFrame("Location") {
    val lat = car.lat ?: 0.0
    val lon = car.lon ?: 0.0
    Text("%.4f".format(lat), style = MaterialTheme.typography.bodyMedium)
    Text("%.4f".format(lon), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    val context = LocalContext.current
    FilledTonalButton(
        onClick = { WearRemote.openOnPhone(context, "https://www.google.com/maps/search/?api=1&query=$lat,$lon") },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Open on phone", maxLines = 1) },
        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
    )
}

@Composable
private fun InfoTile(car: CarView) = TileFrame("Info") {
    StatusRow("VIN", car.vin.takeLast(6))
    car.odometer?.let { StatusRow("Odometer", it) }
    StatusRow("Doors", if (car.doorsOpen.isEmpty()) "Closed" else car.doorsOpen.joinToString())
    StatusRow("Windows", if (car.windowsOpen.isEmpty()) "Closed" else car.windowsOpen.joinToString())
    if (car.trunkOpen) StatusRow("Trunk", "Open", valueColor = MaterialTheme.colorScheme.error)
    if (car.hoodOpen) StatusRow("Hood", "Open", valueColor = MaterialTheme.colorScheme.error)
}

@Composable
private fun DiagnosticsTile(car: CarView) = TileFrame("Diagnostics") {
    StatusRow("Tires", if (car.tireWarning) "Check" else "OK", valueColor = if (car.tireWarning) MaterialTheme.colorScheme.error else null)
    car.battery12v?.let { StatusRow("12V battery", "$it%") }
    if (car.lowFuel) StatusRow("Fuel", "Low", valueColor = MaterialTheme.colorScheme.error)
    if (car.washerLow) StatusRow("Washer", "Low", valueColor = MaterialTheme.colorScheme.error)
    if (car.brakeLow) StatusRow("Brake fluid", "Low", valueColor = MaterialTheme.colorScheme.error)
    if (car.keyFobLow) StatusRow("Key fob", "Low battery", valueColor = MaterialTheme.colorScheme.error)
}

@Composable
private fun MoreTile(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) = TileFrame("More") {
    FilledTonalButton(
        onClick = { vm.refreshStatus(car.vin) },
        enabled = "${car.vin}:refresh" !in ui.pending,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Refresh") },
        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
    )
    if (car.hasBattery) {
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(
            onClick = { onTrips(car.vin) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Trips") },
            icon = { Icon(Icons.Filled.Route, contentDescription = null) },
        )
    }
    Spacer(Modifier.height(6.dp))
    FilledTonalButton(
        onClick = onSettings,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Settings") },
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
    )
}

// ---- Frame + shared widgets ----------------------------------------------

/** A full-screen tile card: centred, round-screen-safe, scrolls only if content
 *  can't fit (so the vertical pager still owns the swipe on normal tiles). */
@Composable
private fun TileFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    val round = LocalConfiguration.current.isScreenRound
    val hPad = if (round) 24.dp else 12.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = hPad, vertical = if (round) 26.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Dot(selected: Boolean) {
    Box(
        Modifier.size(if (selected) 7.dp else 5.dp).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    )
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
