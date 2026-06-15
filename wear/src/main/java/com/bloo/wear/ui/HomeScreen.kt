package com.bloo.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.links
import com.bloo.wear.CarView
import com.bloo.wear.WearRemote
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels
import kotlinx.coroutines.launch

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
            val car = ui.cars[wrap(page, count)]
            val carRoles = ui.settings?.carColors?.get(car.vin)
            if (carRoles != null) {
                MaterialTheme(colorScheme = schemeFrom(carRoles)) { CarColumn(vm, ui, car, onSettings, onTrips) }
            } else {
                CarColumn(vm, ui, car, onSettings, onTrips)
            }
        }
        CurvedIndicator(count, wrap(carPager.currentPage, count), anchor = 90f)
    }
}

/**
 * One car's content as a Wear [ScalingLazyColumn]: cards size to the screen width
 * and scale/fade toward the top & bottom edges as you scroll, the classic Wear
 * morphing-scroll. Rotary (bezel / crown) scrolls it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarColumn(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    val state = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(car.vin) { runCatching { focusRequester.requestFocus() } }

    val round = LocalConfiguration.current.isScreenRound
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scope.launch { state.scrollBy(e.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = state,
        contentPadding = PaddingValues(horizontal = if (round) 16.dp else 8.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(car.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1) }
        item { SummaryCard(car) }
        item {
            MorphButton(
                label = if (car.locked == true) "Locked" else "Unlocked",
                icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
                active = car.locked == true,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = "${car.vin}:doors" in ui.pending,
                onClick = { vm.toggleLock(car.vin) },
            )
        }
        item { ClimateCard(vm, ui, car) }
        item { ComfortCard(vm, ui) }
        if (ui.presets[car.vin]?.isNotEmpty() == true) item { PresetsCard(vm, ui, car) }
        if (car.hasBattery) {
            item { ChargeCard(vm, ui, car) }
            item { LimitsCard(vm, ui, car) }
        }
        if (car.lat != null && car.lon != null) item { LocationCard(vm, ui, car) }
        if (ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null) item { WeatherCard(ui, car) }
        item { InfoCard(car) }
        if (car.hasLiveStatus) item { DiagnosticsCard(car) }
        if (!ui.extras.ai[car.vin].isNullOrBlank()) item { AiCard(ui, car) }
        item { AssistCard(car) }
        item { MoreCard(vm, ui, car, onSettings, onTrips) }
    }
}

// ---- Section cards -------------------------------------------------------

@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
        }
        content()
    }
}

@Composable
private fun SummaryCard(car: CarView) = SectionCard(null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChargeRing(car.percent, size = 60.dp)
        Column {
            Text(car.rangeMi?.let { "$it mi" } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (car.hasBattery) "Battery" else "Fuel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (car.charging == true) Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
            val rel = relativeLabel(car.fetchedAt)
            if (rel.isNotBlank()) Text(rel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClimateCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Climate") {
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
    SliderRow("Run", "${ui.climateDuration} min", ui.climateDuration, 1, 30, 1) { vm.setClimateDuration(it) }
    Spacer(Modifier.height(4.dp))
    SwitchButton(checked = ui.climateDefrost, onCheckedChange = { vm.toggleDefrost() }, modifier = Modifier.fillMaxWidth(), label = { Text("Defrost") })
}

@Composable
private fun ComfortCard(vm: WearViewModel, ui: WearUi) = SectionCard("Comfort") {
    SwitchButton(checked = ui.climateSteering, onCheckedChange = { vm.toggleSteering() }, modifier = Modifier.fillMaxWidth(), label = { Text("Steering heat") })
    Spacer(Modifier.height(4.dp))
    SliderRow("Driver seat", seatStepLabels[ui.seatDriver], ui.seatDriver, 0, 3, 1, accent = WearColors.heat) { vm.setSeatDriver(it) }
    SliderRow("Passenger", seatStepLabels[ui.seatPassenger], ui.seatPassenger, 0, 3, 1, accent = WearColors.heat) { vm.setSeatPassenger(it) }
    Text("Applied when you start climate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PresetsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Presets") {
    ui.presets[car.vin].orEmpty().forEach { preset ->
        MorphButton(
            label = preset.name,
            icon = Icons.Filled.Thermostat,
            active = false,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = "${car.vin}:climate" in ui.pending,
            onClick = { vm.applyPreset(car.vin, preset) },
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ChargeCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Charge") {
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
private fun LimitsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Charge limits") {
    val ac = ui.acLimitDraft ?: car.acLimit ?: 80
    val dc = ui.dcLimitDraft ?: car.dcLimit ?: 90
    SliderRow("AC", "$ac%", ac, 50, 100, 5) { vm.setAcLimit(it) }
    SliderRow("DC", "$dc%", dc, 50, 100, 5) { vm.setDcLimit(it) }
    Spacer(Modifier.height(4.dp))
    MorphButton(
        label = "Apply limits",
        icon = Icons.Filled.Bolt,
        active = false,
        activeColor = WearColors.chargeGreen,
        pending = "${car.vin}:chargeLimit" in ui.pending,
        onClick = { vm.applyChargeLimits(car.vin) },
    )
}

@Composable
private fun LocationCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Location") {
    val lat = car.lat ?: 0.0
    val lon = car.lon ?: 0.0
    val context = LocalContext.current
    MapThumbnail(lat, lon)
    Spacer(Modifier.height(4.dp))
    Text(
        car.locationName ?: "%.4f, %.4f".format(lat, lon),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Locate",
        icon = Icons.Filled.LocationOn,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = "${car.vin}:refresh" in ui.pending,
        onClick = { vm.refreshStatus(car.vin) },
    )
    Spacer(Modifier.height(6.dp))
    FilledTonalButton(
        onClick = { WearRemote.openOnPhone(context, "https://www.google.com/maps/search/?api=1&query=$lat,$lon") },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Open on phone", maxLines = 1) },
        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
    )
}

@Composable
private fun WeatherCard(ui: WearUi, car: CarView) = SectionCard("Weather") {
    val w = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather ?: return@SectionCard
    val f = ui.settings?.useFahrenheit != false
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(weatherIcon(w.code, w.isDay), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
        Column {
            Text(weatherTemp(w.tempC, f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(weatherLabel(w.code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(2.dp))
    StatusRow("Feels", weatherTemp(w.feelsLikeC, f))
    w.humidity?.let { StatusRow("Humidity", "$it%") }
    StatusRow("Wind", "${w.windKph.toInt()} km/h")
}

@Composable
private fun InfoCard(car: CarView) = SectionCard("Info") {
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
private fun DiagnosticsCard(car: CarView) = SectionCard("Diagnostics") {
    val err = MaterialTheme.colorScheme.error
    car.tempSetting?.let { StatusRow("Set temp", "$it°") }
    if (car.hasBattery) car.rangeMi?.let { StatusRow("Range", "$it mi") }
    StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
    car.tireAll?.let { StatusRow("Tire avg", "$it psi") }
    if (car.tireFl) StatusRow("Tire FL", "Check", valueColor = err)
    if (car.tireFr) StatusRow("Tire FR", "Check", valueColor = err)
    if (car.tireRl) StatusRow("Tire RL", "Check", valueColor = err)
    if (car.tireRr) StatusRow("Tire RR", "Check", valueColor = err)
    if (!car.tireFl && !car.tireFr && !car.tireRl && !car.tireRr && car.tireAll == null) {
        StatusRow("Tires", if (car.tireWarning) "Check" else "OK", valueColor = if (car.tireWarning) err else null)
    }
    car.battery12v?.let { StatusRow("12V", "$it%" + (car.battery12vHealth?.let { h -> " · $h" } ?: "")) }
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
private fun AiCard(ui: WearUi, car: CarView) = SectionCard("AI summary") {
    val text = ui.extras.ai[car.vin]
    if (text.isNullOrBlank()) {
        Text("No summary yet — generate on your phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AssistCard(car: CarView) = SectionCard("Assist") {
    val context = LocalContext.current
    val links = car.brand.links
    FilledTonalButton(
        onClick = { WearRemote.dialOnPhone(context, links.roadsidePhone) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Roadside", maxLines = 1) },
        icon = { Icon(Icons.Filled.Call, contentDescription = null) },
    )
    Spacer(Modifier.height(6.dp))
    FilledTonalButton(
        onClick = { WearRemote.openOnPhone(context, links.serviceScheduleUrl) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Schedule service", maxLines = 1) },
        icon = { Icon(Icons.Filled.Build, contentDescription = null) },
    )
    Spacer(Modifier.height(6.dp))
    FilledTonalButton(
        onClick = { WearRemote.openOnPhone(context, links.ownersUrl) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Owner site", maxLines = 1) },
        icon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
    )
}

@Composable
private fun MoreCard(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) = SectionCard("More") {
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

// ---- Shared bits ---------------------------------------------------------

private fun seatLabel(v: Int?): String? = v?.takeIf { it != 0 }?.let { SeatLevel.fromApi(it).label }

/** A page indicator whose dots curve along the round screen's edge. */
@Composable
private fun CurvedIndicator(count: Int, current: Int, anchor: Float) {
    if (count <= 1) return
    val selected = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.outlineVariant
    CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = anchor, anchorType = AnchorType.Center) {
        curvedRow {
            repeat(count) { i ->
                curvedComposable {
                    Box(
                        Modifier
                            .padding(1.5.dp)
                            .size(if (i == current) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == current) selected else unselected)
                    )
                }
            }
        }
    }
}
