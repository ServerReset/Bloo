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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.SeatLevel
import com.bloo.wear.CarView
import com.bloo.wear.WearRemote
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels

private fun wrap(index: Int, count: Int) = ((index % count) + count) % count

@Composable
fun HomeScreen(vm: WearViewModel, ui: WearUi, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    if (ui.cars.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No cars yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val count = ui.cars.size
    val loop = count > 1
    val virtual = if (loop) count * 1000 else count
    val carPager = rememberPagerState(initialPage = if (loop) virtual / 2 else 0) { virtual }

    LaunchedEffect(carPager.settledPage, count) {
        ui.cars.getOrNull(wrap(carPager.settledPage, count))?.let { vm.onCarShown(it.vin) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = carPager, modifier = Modifier.fillMaxSize()) { page ->
            CarTiles(vm, ui, ui.cars[wrap(page, count)], onSettings, onTrips)
        }
        if (count > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { repeat(count) { Dot(it == wrap(carPager.currentPage, count)) } }
        }
    }
}

@Composable
private fun CarTiles(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    val tiles = remember(car.vin, car.hasBattery, car.lat, car.hasLiveStatus, car.tripsSupported) {
        buildList {
            add("summary"); add("climate"); add("comfort")
            if (car.hasBattery) { add("charge"); add("limits") }
            if (car.lat != null && car.lon != null) add("location")
            add("info")
            if (car.hasLiveStatus) add("diagnostics")
            add("more")
        }
    }
    val count = tiles.size
    val loop = count > 1
    val virtual = if (loop) count * 1000 else count
    val vPager = rememberPagerState(initialPage = if (loop) virtual / 2 else 0) { virtual }

    // Bezel / digital-crown scrolling drives the tile pager.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(car.vin) { runCatching { focusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(vPager), focusRequester),
        ) { i ->
            when (tiles[wrap(i, count)]) {
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
        if (count > 1) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { repeat(count) { Dot(it == wrap(vPager.currentPage, count)) } }
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
    MorphButton(
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
    MorphButton(
        label = if (car.climateOn == true) "Climate on" else "Start climate",
        icon = Icons.Filled.Thermostat,
        active = car.climateOn == true,
        activeColor = MaterialTheme.colorScheme.tertiary,
        pending = "${car.vin}:climate" in ui.pending,
        onClick = { vm.toggleClimate(car.vin) },
    )
    Spacer(Modifier.height(6.dp))
    SliderRow("Temp", "${ui.climateTempF}°F", ui.climateTempF, 62, 82, 1, accent = tempColor(ui.climateTempF)) { vm.setClimateTemp(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("Run", "${ui.climateDuration} min", ui.climateDuration, 1, 30, 1) { vm.setClimateDuration(it) }
    Spacer(Modifier.height(4.dp))
    SwitchButton(checked = ui.climateDefrost, onCheckedChange = { vm.toggleDefrost() }, modifier = Modifier.fillMaxWidth(), label = { Text("Defrost") })
}

@Composable
private fun ComfortTile(vm: WearViewModel, ui: WearUi) = TileFrame("Comfort") {
    SwitchButton(checked = ui.climateSteering, onCheckedChange = { vm.toggleSteering() }, modifier = Modifier.fillMaxWidth(), label = { Text("Steering heat") })
    Spacer(Modifier.height(6.dp))
    SliderRow("Driver seat", seatStepLabels[ui.seatDriver], ui.seatDriver, 0, 3, 1, accent = WearColors.heat) { vm.setSeatDriver(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("Passenger", seatStepLabels[ui.seatPassenger], ui.seatPassenger, 0, 3, 1, accent = WearColors.heat) { vm.setSeatPassenger(it) }
    Spacer(Modifier.height(4.dp))
    Text("Applied when you start climate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun ChargeTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Charge") {
    MorphButton(
        label = if (car.charging == true) "Charging — stop" else "Start charge",
        icon = Icons.Filled.Bolt,
        active = car.charging == true,
        activeColor = WearColors.chargeGreen,
        pending = "${car.vin}:charge" in ui.pending,
        onClick = { vm.toggleCharge(car.vin) },
    )
    Spacer(Modifier.height(4.dp))
    car.percent?.let { StatusRow("Battery", "$it%") }
    car.rangeMi?.let { StatusRow("Range", "$it mi") }
    StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
}

@Composable
private fun LimitsTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Charge limits") {
    val ac = ui.acLimitDraft ?: car.acLimit ?: 80
    val dc = ui.dcLimitDraft ?: car.dcLimit ?: 90
    SliderRow("AC", "$ac%", ac, 50, 100, 5) { vm.setAcLimit(it) }
    Spacer(Modifier.height(4.dp))
    SliderRow("DC", "$dc%", dc, 50, 100, 5) { vm.setDcLimit(it) }
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
    StatusRow("Engine", if (car.engineOn) "On" else "Off")
    car.tempSetting?.let { StatusRow("Set temp", "$it°") }
    StatusRow("Climate", if (car.climateOn == true) "On" else "Off")
    StatusRow("Defrost", if (car.defrostOn) "On" else "Off")
    StatusRow("Accessory", if (car.accessoryOn) "On" else "Off")
    StatusRow("Doors", if (car.doorsOpen.isEmpty()) "Closed" else car.doorsOpen.joinToString())
    StatusRow("Windows", if (car.windowsOpen.isEmpty()) "Closed" else car.windowsOpen.joinToString())
    if (car.trunkOpen) StatusRow("Trunk", "Open", valueColor = MaterialTheme.colorScheme.error)
    if (car.hoodOpen) StatusRow("Hood", "Open", valueColor = MaterialTheme.colorScheme.error)
    StatusRow("VIN", car.vin.takeLast(6))
    car.odometer?.let { StatusRow("Odometer", it) }
}

@Composable
private fun DiagnosticsTile(car: CarView) = TileFrame("Diagnostics") {
    val err = MaterialTheme.colorScheme.error
    car.tireAll?.let { StatusRow("Tire avg", "$it psi") }
    if (car.tireFl) StatusRow("Tire FL", "Check", valueColor = err)
    if (car.tireFr) StatusRow("Tire FR", "Check", valueColor = err)
    if (car.tireRl) StatusRow("Tire RL", "Check", valueColor = err)
    if (car.tireRr) StatusRow("Tire RR", "Check", valueColor = err)
    if (!car.tireFl && !car.tireFr && !car.tireRl && !car.tireRr && car.tireAll == null) StatusRow("Tires", if (car.tireWarning) "Check" else "OK", valueColor = if (car.tireWarning) err else null)
    car.battery12v?.let { StatusRow("12V", "$it%" + (car.battery12vHealth?.let { h -> " · $h" } ?: "")) }
    if (car.hasBattery) car.percent?.let { StatusRow("Battery", "$it%") }
    car.fuelLevel?.let { StatusRow("Fuel", "$it%") }
    if (car.lowFuel) StatusRow("Fuel", "Low", valueColor = err)
    if (car.washerLow) StatusRow("Washer", "Low", valueColor = err)
    if (car.brakeLow) StatusRow("Brake fluid", "Low", valueColor = err)
    if (car.keyFobLow) StatusRow("Key fob", "Low battery", valueColor = err)
    if (car.steerHeat) StatusRow("Steering", "Heating")
    if (car.mirrorHeat) StatusRow("Mirrors", "Heating")
    if (car.rearDefrost) StatusRow("Rear defrost", "On")
    seatLabel(car.seatFl)?.let { StatusRow("Seat FL", it) }
    seatLabel(car.seatFr)?.let { StatusRow("Seat FR", it) }
    seatLabel(car.seatRl)?.let { StatusRow("Seat RL", it) }
    seatLabel(car.seatRr)?.let { StatusRow("Seat RR", it) }
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
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
    if (car.hasBattery && car.tripsSupported) {
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

private fun seatLabel(v: Int?): String? = v?.takeIf { it != 0 }?.let { SeatLevel.fromApi(it).label }

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
