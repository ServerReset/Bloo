package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.foundation.lazy.items
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
import com.bloo.wear.WearTiles
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private fun wrap(index: Int, count: Int) = ((index % count) + count) % count

/** Synthetic tile key for the alerts card (not part of the user-orderable set). */
private const val TILE_ALERTS = "alerts"

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
    val virtual = if (loop) count * 50 else count

    // key() resets the pager when the VIN list changes (e.g. cars added/removed).
    key(ui.cars.map { it.vin }) {
        val carPager = rememberPagerState(initialPage = if (loop) virtual / 2 else 0) { virtual }

        // Only fetch a car's status once it actually settles on a *new* VIN, so a
        // swipe doesn't churn through cars or re-trigger fetches mid-gesture.
        var lastShownVin by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(carPager.settledPage) {
            val vin = ui.cars.getOrNull(wrap(carPager.settledPage, count))?.vin
            if (vin != null && vin != lastShownVin) {
                lastShownVin = vin
                vm.onCarShown(vin)
            }
        }

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = carPager,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val car = ui.cars[wrap(page, count)]
                // Only the settled page owns rotary focus.
                val active = wrap(carPager.settledPage, count) == wrap(page, count)
                val carRoles = ui.settings?.carColors?.get(car.vin)
                if (carRoles != null) {
                    MaterialTheme(colorScheme = schemeFrom(carRoles)) { CarColumn(vm, ui, car, active, onSettings, onTrips) }
                } else {
                    CarColumn(vm, ui, car, active, onSettings, onTrips)
                }
            }
            CurvedIndicator(count, wrap(carPager.currentPage, count), anchor = 90f)
        }
    }
}

/** The ordered tiles actually shown for this car (conditions resolved once). */
private fun visibleTiles(ui: WearUi, car: CarView): List<String> {
    val hasAlerts = car.doorsOpen.isNotEmpty() || car.windowsOpen.isNotEmpty() ||
        car.trunkOpen || car.hoodOpen || car.tireWarning ||
        car.lowFuel || car.brakeLow || car.washerLow || car.keyFobLow
    val out = ArrayList<String>()
    if (hasAlerts) out.add(TILE_ALERTS)
    for (key in ui.localSettings.tileOrder) {
        val show = when (key) {
            WearTiles.PRESETS -> ui.presets[car.vin]?.isNotEmpty() == true
            WearTiles.CHARGE, WearTiles.LIMITS -> car.hasBattery
            WearTiles.LOCATION -> car.lat != null && car.lon != null
            WearTiles.WEATHER -> ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null
            WearTiles.DIAGNOSTICS -> car.hasLiveStatus
            else -> true // summary, lock, climate, comfort, info, ai, assist, more
        }
        if (show) out.add(key)
    }
    return out
}

/**
 * One car's content as a morphing-scroll [ScalingLazyColumn]. Cards scale/fade
 * at the edges, the list wraps around for endless vertical scroll, and rotary
 * (bezel / crown) snaps exactly one tile per detent. The screen names the car
 * once you scroll away from its summary.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarColumn(vm: WearViewModel, ui: WearUi, car: CarView, active: Boolean, onSettings: () -> Unit, onTrips: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val round = LocalConfiguration.current.isScreenRound

    val tiles = remember(ui.localSettings.tileOrder, ui.presets, ui.extras, car) { visibleTiles(ui, car) }
    val tileCount = tiles.size
    // Wrap-around (endless) scrolling once there's more than one tile.
    val infinite = tileCount > 1
    val cycles = if (infinite) 200 else 1
    val total = tileCount * cycles
    val summaryIdx = tiles.indexOf(WearTiles.SUMMARY).coerceAtLeast(0)
    val initialIndex = if (infinite) (cycles / 2) * tileCount + summaryIdx else summaryIdx

    val state = rememberScalingLazyListState(initialCenterItemIndex = initialIndex)

    // Only the on-screen page requests focus, otherwise off-screen pages steal
    // rotary input and the bezel appears to do nothing.
    LaunchedEffect(active, car.vin) {
        if (active) runCatching { focusRequester.requestFocus() }
    }

    var isSnapping by remember { mutableStateOf(false) }

    // Snap to the nearest tile when a finger fling settles — tile-by-tile feel.
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress && !isSnapping) {
            val info = state.layoutInfo
            val viewportCenter = info.viewportSize.height / 2
            val centerItem = info.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            }
            if (centerItem != null) {
                isSnapping = true
                state.animateScrollToItem(centerItem.index)
                isSnapping = false
            }
        }
    }

    val centerItemIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val viewportCenter = info.viewportSize.height / 2
            info.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            }?.index ?: 0
        }
    }
    val centerTile = if (tileCount > 0) tiles[wrap(centerItemIndex, tileCount)] else ""

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { e ->
                    val info = state.layoutInfo
                    val viewportCenter = info.viewportSize.height / 2
                    val center = info.visibleItemsInfo.minByOrNull {
                        abs(it.offset + it.size / 2 - viewportCenter)
                    }?.index ?: 0
                    // One detent → one tile, in the direction of the turn.
                    val dir = if (e.verticalScrollPixels > 0) 1 else -1
                    val maxIdx = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val target = (center + dir).coerceIn(0, maxIdx)
                    scope.launch { state.animateScrollToItem(target) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            state = state,
            contentPadding = PaddingValues(horizontal = if (round) 16.dp else 8.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = total, key = { it }) { i ->
                TileContent(tiles[wrap(i, tileCount)], vm, ui, car, onSettings, onTrips)
            }
        }

        // Tile-progress dots that curve along the right bezel so the round face
        // never clips them.
        CurvedDotIndicator(
            total = tileCount,
            activeIndex = wrap(centerItemIndex, tileCount.coerceAtLeast(1)),
        )

        // Name the car once you leave its summary tile.
        CarNameOverlay(
            name = car.name,
            visible = centerTile.isNotEmpty() && centerTile != WearTiles.SUMMARY,
        )

        // Auto-dismissing message snackbar at the bottom.
        if (ui.message != null) {
            LaunchedEffect(ui.message) {
                delay(3500)
                vm.dismissMessage()
            }
        }
        AnimatedVisibility(
            visible = ui.message != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { vm.dismissMessage() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    ui.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Render a single tile by key. Plain composable so it can repeat for wrap-around. */
@Composable
private fun TileContent(
    key: String,
    vm: WearViewModel,
    ui: WearUi,
    car: CarView,
    onSettings: () -> Unit,
    onTrips: (String) -> Unit,
) {
    when (key) {
        TILE_ALERTS -> AlertsCard(car)
        WearTiles.SUMMARY -> SummaryCard(car, ui)
        WearTiles.LOCK -> MorphButton(
            label = if (car.locked == true) "Locked" else "Unlocked",
            icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
            active = car.locked == true,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = "${car.vin}:doors" in ui.pending,
            onClick = { vm.toggleLock(car.vin) },
        )
        WearTiles.CLIMATE -> ClimateCard(vm, ui, car)
        WearTiles.COMFORT -> ComfortCard(vm, ui, car)
        WearTiles.PRESETS -> PresetsCard(vm, ui, car)
        WearTiles.CHARGE -> ChargeCard(vm, ui, car)
        WearTiles.LIMITS -> LimitsCard(vm, ui, car)
        WearTiles.LOCATION -> LocationCard(vm, ui, car)
        WearTiles.WEATHER -> WeatherCard(ui, car)
        WearTiles.INFO -> InfoCard(car)
        WearTiles.DIAGNOSTICS -> DiagnosticsCard(car)
        WearTiles.AI -> AiCard(ui, car)
        WearTiles.ASSIST -> AssistCard(car)
        WearTiles.MORE -> MoreCard(vm, ui, car, onSettings, onTrips)
    }
}

/** Tile-progress dots laid along the right-hand arc of the (round) screen. */
@Composable
private fun CurvedDotIndicator(total: Int, activeIndex: Int) {
    if (total <= 1) return
    val shown = min(total, 12)
    val active = if (total <= shown) {
        activeIndex.coerceIn(0, shown - 1)
    } else {
        ((activeIndex.toFloat() / (total - 1)) * (shown - 1)).roundToInt().coerceIn(0, shown - 1)
    }
    val selected = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.outlineVariant
    // anchor 0° = 3 o'clock; dots hug the right bezel and follow the curve.
    CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = 0f, anchorType = AnchorType.Center) {
        curvedRow {
            repeat(shown) { i ->
                curvedComposable {
                    val isOn = i == active
                    val sz by animateDpAsState(if (isOn) 7.dp else 4.dp, tween(150), label = "cd$i")
                    val c by animateColorAsState(if (isOn) selected else unselected, tween(150), label = "cc$i")
                    Box(Modifier.padding(1.5.dp).size(sz).clip(CircleShape).background(c))
                }
            }
        }
    }
}

/** A small pill at the top naming the car you're currently looking at. */
@Composable
private fun BoxScope.CarNameOverlay(name: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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

/** Alert card — shown only when there are open doors/windows/warnings. */
@Composable
private fun AlertsCard(car: CarView) {
    val warnings = buildList {
        if (car.doorsOpen.isNotEmpty()) add("Doors" to car.doorsOpen.joinToString(", "))
        if (car.windowsOpen.isNotEmpty()) add("Windows" to car.windowsOpen.joinToString(", "))
        if (car.trunkOpen) add("Trunk" to "Open")
        if (car.hoodOpen) add("Hood" to "Open")
        if (car.tireWarning) add("Tires" to "Check")
        if (car.lowFuel) add("Fuel" to "Low")
        if (car.washerLow) add("Washer fluid" to "Low")
        if (car.brakeLow) add("Brake fluid" to "Low")
        if (car.keyFobLow) add("Key fob" to "Low battery")
    }
    if (warnings.isEmpty()) return
    val errColor = MaterialTheme.colorScheme.error
    SectionCard(null) {
        warnings.forEach { (label, value) -> StatusRow(label, value, valueColor = errColor) }
    }
}

@Composable
private fun SummaryCard(car: CarView, ui: WearUi) = SectionCard(null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChargeRing(car.percent, size = 60.dp)
        Column {
            AnimatedValue(
                value = car.rangeMi?.let { "$it mi" } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(if (car.hasBattery) "Battery" else "Fuel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                car.engineOn ->
                    Text("Driving", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                car.charging == true && car.timeToFullMin != null && car.timeToFullMin > 0 ->
                    Text("${fmtMinutes(car.timeToFullMin)} to full", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                car.charging == true ->
                    Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                car.pluggedIn == true ->
                    Text("Plugged in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rel = relativeLabel(car.fetchedAt)
            if (rel.isNotBlank()) Text(rel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // 12V battery hint row
    car.battery12v?.let { v12 ->
        Spacer(Modifier.height(4.dp))
        val c = if (v12 < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        StatusRow("12V battery", "$v12%", valueColor = c)
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
    SliderRow("Run", "${ui.climateDuration} min", ui.climateDuration, 1, 10, 1) { vm.setClimateDuration(it) }
    Spacer(Modifier.height(4.dp))
    SwitchButton(checked = ui.climateDefrost, onCheckedChange = { vm.toggleDefrost() }, modifier = Modifier.fillMaxWidth(), label = { Text("Defrost") })
}

@Composable
private fun ComfortCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Comfort") {
    SwitchButton(checked = ui.climateSteering, onCheckedChange = { vm.toggleSteering() }, modifier = Modifier.fillMaxWidth(), label = { Text("Steering heat") })
    Spacer(Modifier.height(4.dp))
    SliderRow("Driver seat", seatStepLabels[ui.seatDriver], ui.seatDriver, 0, 3, 1, accent = WearColors.heat) { vm.setSeatDriver(it) }
    SliderRow("Passenger", seatStepLabels[ui.seatPassenger], ui.seatPassenger, 0, 3, 1, accent = WearColors.heat) { vm.setSeatPassenger(it) }
    // Rear seats only when the live status shows they exist (non-null seatRl/Rr from a fetch).
    if (car.seatRl != null || car.seatRr != null) {
        Spacer(Modifier.height(2.dp))
        SliderRow("Rear left", seatStepLabels[ui.seatRearLeft], ui.seatRearLeft, 0, 3, 1, accent = WearColors.heat) { vm.setSeatRearLeft(it) }
        SliderRow("Rear right", seatStepLabels[ui.seatRearRight], ui.seatRearRight, 0, 3, 1, accent = WearColors.heat) { vm.setSeatRearRight(it) }
    }
    Spacer(Modifier.height(2.dp))
    Text("Applied when you start climate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PresetsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Presets") {
    ui.presets[car.vin].orEmpty().forEach { preset ->
        val isActive = ui.activePresetId == preset.id && car.climateOn == true
        MorphButton(
            label = preset.name,
            icon = Icons.Filled.Thermostat,
            active = isActive,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = "${car.vin}:climate" in ui.pending,
            // Tapping the running preset turns climate off, like the phone.
            onClick = { if (isActive) vm.toggleClimate(car.vin) else vm.applyPreset(car.vin, preset) },
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
    SliderRow("AC", "$ac%", ac, 50, 100, 10) { vm.setAcLimit(it) }
    SliderRow("DC", "$dc%", dc, 50, 100, 10) { vm.setDcLimit(it) }
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
    MorphButton(
        label = "Open on phone",
        icon = Icons.Filled.OpenInNew,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, "https://www.google.com/maps/search/?api=1&query=$lat,$lon") },
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
        // High / low column
        if (w.highC != null || w.lowC != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                w.highC?.let { Text("H: ${weatherTemp(it, f)}", style = MaterialTheme.typography.labelSmall) }
                w.lowC?.let { Text("L: ${weatherTemp(it, f)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
    StatusRow("Feels", weatherTemp(w.feelsLikeC, f))
    w.humidity?.let { StatusRow("Humidity", "$it%") }
    if (w.windKph > 0) StatusRow("Wind", "${w.windKph.toInt()} km/h")
}

@Composable
private fun InfoCard(car: CarView) = SectionCard("Info") {
    StatusRow("Engine", if (car.engineOn) "On" else "Off")
    car.tempSetting?.let { StatusRow("Set temp", "$it°") }
    StatusRow("Climate", if (car.climateOn == true) "On" else "Off")
    StatusRow("Defrost", if (car.defrostOn) "On" else "Off")
    StatusRow("Accessory", if (car.accessoryOn) "On" else "Off")
    val doorsLabel = when {
        car.doorsOpen.isEmpty() -> "All closed"
        car.doorsOpen.size == 1 -> car.doorsOpen.first()
        else -> "${car.doorsOpen.size} open"
    }
    StatusRow("Doors", doorsLabel, valueColor = if (car.doorsOpen.isNotEmpty()) MaterialTheme.colorScheme.error else null)
    val winsLabel = when {
        car.windowsOpen.isEmpty() -> "All closed"
        car.windowsOpen.size == 1 -> car.windowsOpen.first()
        else -> "${car.windowsOpen.size} open"
    }
    StatusRow("Windows", winsLabel, valueColor = if (car.windowsOpen.isNotEmpty()) MaterialTheme.colorScheme.error else null)
    if (car.trunkOpen) StatusRow("Trunk", "Open", valueColor = MaterialTheme.colorScheme.error)
    if (car.hoodOpen) StatusRow("Hood", "Open", valueColor = MaterialTheme.colorScheme.error)
    StatusRow("VIN", car.vin.takeLast(6))
    car.odometer?.let { StatusRow("Odometer", it) }
}

@Composable
private fun DiagnosticsCard(car: CarView) = SectionCard("Diagnostics") {
    val err = MaterialTheme.colorScheme.error
    car.tireAll?.let { StatusRow("Tire avg", "$it psi") }
    if (car.tireFl) StatusRow("Tire FL", "Check", valueColor = err)
    if (car.tireFr) StatusRow("Tire FR", "Check", valueColor = err)
    if (car.tireRl) StatusRow("Tire RL", "Check", valueColor = err)
    if (car.tireRr) StatusRow("Tire RR", "Check", valueColor = err)
    if (!car.tireFl && !car.tireFr && !car.tireRl && !car.tireRr && car.tireAll == null) {
        StatusRow("Tires", if (car.tireWarning) "Check" else "OK", valueColor = if (car.tireWarning) err else null)
    }
    car.battery12v?.let { v12 ->
        val h = car.battery12vHealth?.let { " · $it" } ?: ""
        StatusRow("12V", "$v12%$h", valueColor = if (v12 < 20) err else null)
    }
    car.fuelLevel?.let { StatusRow("Fuel", "$it%", valueColor = if (car.lowFuel) err else null) }
    if (car.washerLow) StatusRow("Washer", "Low", valueColor = err)
    if (car.brakeLow) StatusRow("Brake fluid", "Low", valueColor = err)
    if (car.keyFobLow) StatusRow("Key fob", "Low battery", valueColor = err)
    if (car.steerHeat) StatusRow("Steering", "Heating", valueColor = WearColors.heat)
    if (car.mirrorHeat) StatusRow("Mirrors", "Heating", valueColor = WearColors.heat)
    if (car.rearDefrost) StatusRow("Rear defrost", "On")
    seatLabel(car.seatFl)?.let { StatusRow("Seat FL", it) }
    seatLabel(car.seatFr)?.let { StatusRow("Seat FR", it) }
    seatLabel(car.seatRl)?.let { StatusRow("Seat RL", it) }
    seatLabel(car.seatRr)?.let { StatusRow("Seat RR", it) }
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
    if (car.chargerLabel != null) StatusRow("Charger", car.chargerLabel)
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
    val accent = MaterialTheme.colorScheme.primary
    MorphButton(
        label = "Roadside",
        icon = Icons.Filled.Call,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.dialOnPhone(context, links.roadsidePhone) },
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Schedule service",
        icon = Icons.Filled.Build,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, links.serviceScheduleUrl) },
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Owner site",
        icon = Icons.Filled.OpenInNew,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, links.ownersUrl) },
    )
}

@Composable
private fun MoreCard(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit) = SectionCard("More") {
    val accent = MaterialTheme.colorScheme.primary
    MorphButton(
        label = "Refresh",
        icon = Icons.Filled.Refresh,
        active = false,
        activeColor = accent,
        pending = "${car.vin}:refresh" in ui.pending,
        onClick = { vm.refreshStatus(car.vin) },
    )
    if (car.hasBattery && car.tripsSupported) {
        Spacer(Modifier.height(6.dp))
        MorphButton(
            label = "Trips",
            icon = Icons.Filled.Route,
            active = false,
            activeColor = accent,
            pending = false,
            onClick = { onTrips(car.vin) },
        )
    }
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Settings",
        icon = Icons.Filled.Settings,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = onSettings,
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
