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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.links
import kotlinx.coroutines.launch
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
            val car = ui.cars[wrap(page, count)]
            val carRoles = ui.settings?.carColors?.get(car.vin)
            if (carRoles != null) {
                // This car has a custom palette on the phone — theme its page to match.
                MaterialTheme(colorScheme = schemeFrom(carRoles)) {
                    CarTiles(vm, ui, car, onSettings, onTrips)
                }
            } else {
                CarTiles(vm, ui, car, onSettings, onTrips)
            }
        }
        // Curved car indicator along the bottom arc.
        CurvedIndicator(count, wrap(carPager.currentPage, count), anchor = 90f)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarTiles(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    val hasPresets = (ui.presets[car.vin]?.isNotEmpty() == true)
    val hasWeather = ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null
    val hasAi = !ui.extras.ai[car.vin].isNullOrBlank()
    val tiles = remember(car.vin, car.hasBattery, car.lat, car.hasLiveStatus, car.tripsSupported, hasPresets, hasWeather, hasAi) {
        buildList {
            add("summary"); add("climate"); add("comfort")
            if (hasPresets) add("presets")
            if (car.hasBattery) { add("charge"); add("limits") }
            if (car.lat != null && car.lon != null) add("location")
            if (hasWeather) add("weather")
            add("info")
            if (car.hasLiveStatus) add("diagnostics")
            if (hasAi) add("ai")
            add("assist")
            add("more")
        }
    }
    val count = tiles.size
    val loop = count > 1
    val virtual = if (loop) count * 1000 else count
    val vPager = rememberPagerState(initialPage = if (loop) virtual / 2 else 0) { virtual }

    // Bezel / digital-crown scrolling, page by page.
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var accum by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(car.vin) { runCatching { focusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize()
                .onRotaryScrollEvent { e ->
                    accum += e.verticalScrollPixels
                    when {
                        accum >= 40f -> { scope.launch { vPager.animateScrollToPage(vPager.currentPage + 1) }; accum = 0f }
                        accum <= -40f -> { scope.launch { vPager.animateScrollToPage(vPager.currentPage - 1) }; accum = 0f }
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) { i ->
            when (tiles[wrap(i, count)]) {
                "summary" -> SummaryTile(vm, ui, car)
                "climate" -> ClimateTile(vm, ui, car)
                "comfort" -> ComfortTile(vm, ui)
                "presets" -> PresetsTile(vm, ui, car)
                "charge" -> ChargeTile(vm, ui, car)
                "limits" -> LimitsTile(vm, ui, car)
                "location" -> LocationTile(vm, ui, car)
                "weather" -> WeatherTile(ui, car)
                "info" -> InfoTile(car)
                "diagnostics" -> DiagnosticsTile(car)
                "ai" -> AiTile(ui, car)
                "assist" -> AssistTile(car)
                "more" -> MoreTile(vm, ui, car, onSettings, onTrips)
            }
        }
        // Curved tile indicator along the right arc.
        CurvedIndicator(count, wrap(vPager.currentPage, count), anchor = 0f)
    }
}

// ---- Tiles ---------------------------------------------------------------

@Composable
private fun SummaryTile(vm: WearViewModel, ui: WearUi, car: CarView) {
    val img = ui.extras.images[car.vin]
    Box(Modifier.fillMaxSize()) {
        if (!img.isNullOrBlank()) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(0.16f),
            )
        }
        TileFrame(car.name) {
            // Compact: ring + range side by side so lock/unlock is always on screen.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChargeRing(car.percent, size = 60.dp)
                Column {
                    Text(car.rangeMi?.let { "$it mi" } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (car.hasBattery) "Battery" else "Fuel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (car.charging == true) Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                }
            }
            val rel = relativeLabel(car.fetchedAt)
            if (rel.isNotBlank()) {
                Text("Updated $rel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    }
}

@Composable
private fun WeatherTile(ui: WearUi, car: CarView) = TileFrame("Weather") {
    val w = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather
    if (w == null) {
        Text("No weather", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@TileFrame
    }
    val f = ui.settings?.useFahrenheit != false
    Icon(weatherIcon(w.code, w.isDay), contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(30.dp))
    Text(weatherTemp(w.tempC, f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(weatherLabel(w.code), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(2.dp))
    StatusRow("Feels", weatherTemp(w.feelsLikeC, f))
    w.humidity?.let { StatusRow("Humidity", "$it%") }
    StatusRow("Wind", "${w.windKph.toInt()} km/h")
}

@Composable
private fun AssistTile(car: CarView) = TileFrame("Assist") {
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
private fun AiTile(ui: WearUi, car: CarView) = TileFrame("AI summary") {
    val text = ui.extras.ai[car.vin]
    if (text.isNullOrBlank()) {
        Text("No summary yet — generate one on your phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    } else {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PresetsTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Presets") {
    val presets = ui.presets[car.vin].orEmpty()
    if (presets.isEmpty()) {
        Text("No presets — save one on your phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    } else {
        presets.forEach { preset ->
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
    SliderRow("DC", "$dc%", dc, 50, 100, 5) { vm.setDcLimit(it) }
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
private fun LocationTile(vm: WearViewModel, ui: WearUi, car: CarView) = TileFrame("Location") {
    val lat = car.lat ?: 0.0
    val lon = car.lon ?: 0.0
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
    car.tempSetting?.let { StatusRow("Set temp", "$it°") }
    if (car.hasBattery) car.rangeMi?.let { StatusRow("Range", "$it mi") }
    StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
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
    // Extra inset on round screens so full-width buttons sit inside the circle.
    val hPad = if (round) 30.dp else 12.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = hPad, vertical = if (round) 32.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

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
